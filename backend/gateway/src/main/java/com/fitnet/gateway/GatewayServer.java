package com.fitnet.gateway;

import com.fitnet.messaging.TcpMessagingClient;
import com.fitnet.transfer.FileMetadata;
import com.fitnet.transfer.SecureTransferClient;
import com.fitnet.transfer.TransferException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;

/** Local HTTP bridge between the Next.js browser UI and the TLS file server. */
public final class GatewayServer implements AutoCloseable {
    private static final int DEFAULT_PORT = 8080;
    private static final long MAX_UPLOAD_BYTES = 500L * 1024 * 1024;
    private final int port;
    private final String frontendOrigin;
    private final SecureTransferClient transferClient;
    private HttpServer server;
    private WebSocketServer webSocketServer;
    private ExecutorService executor;

    public GatewayServer(int port, String frontendOrigin, SecureTransferClient transferClient) {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Port must be between 0 and 65535");
        this.port = port;
        this.frontendOrigin = frontendOrigin;
        this.transferClient = transferClient;
    }

    public synchronized void start() throws IOException {
        if (server != null) return;
        server = HttpServer.create(new InetSocketAddress(
                System.getProperty("gateway.bind", "127.0.0.1"), port), 32);
        server.createContext("/api/files", this::handleFiles);
        executor = Executors.newFixedThreadPool(8, task -> {
            Thread thread = new Thread(task, "fitnet-gateway-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        webSocketServer = new WebSocketServer(
                port == 0
                        ? 0
                        : Integer.parseInt(System.getProperty("gateway.websocket.port", "8081")),
                System.getProperty("tcp.host", "127.0.0.1"),
                Integer.parseInt(System.getProperty("tcp.port", "6000")));
        webSocketServer.start();
    }

    public synchronized int getPort() {
        if (server == null) throw new IllegalStateException("Gateway has not started");
        return server.getAddress().getPort();
    }

    private void handleFiles(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("GET".equals(method) && "/api/files".equals(path)) {
                if (transferClient == null) throw new IOException("Secure transfer service is unavailable");
                json(exchange, 200, filesJson(transferClient.listFiles()));
            } else if ("POST".equals(method) && "/api/files".equals(path)) {
                if (transferClient == null) throw new IOException("Secure transfer service is unavailable");
                upload(exchange);
            } else if ("GET".equals(method) && path.startsWith("/api/files/")) {
                if (transferClient == null) throw new IOException("Secure transfer service is unavailable");
                download(exchange, URLDecoder.decode(path.substring("/api/files/".length()), StandardCharsets.UTF_8));
            } else {
                json(exchange, 404, "{\"error\":\"Route not found\"}");
            }
        } catch (PayloadTooLargeException e) {
            json(exchange, 413, "{\"error\":\"File exceeds the 500 MB upload limit\"}");
        } catch (TransferException e) {
            int status = switch (e.getMessage() == null ? "" : e.getMessage()) {
                case "File not found" -> 404;
                case "File exceeds the configured size limit" -> 413;
                default -> 502;
            };
            json(exchange, status, "{\"error\":" + quote(e.getMessage()) + "}");
        } catch (IllegalArgumentException e) {
            json(exchange, 400, "{\"error\":" + quote(e.getMessage()) + "}");
        } catch (IOException e) {
            json(exchange, 502, "{\"error\":\"Secure transfer service is unavailable\"}");
        } finally {
            exchange.close();
        }
    }

    private void upload(HttpExchange exchange) throws IOException {
        String fileName = exchange.getRequestHeaders().getFirst("X-Filename");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("X-Filename header is required");
        validateUploadFileName(fileName);
        Path temporary = Files.createTempFile("fitnet-gateway-upload-", ".part");
        try {
            try (InputStream body = new SizeLimitedInputStream(exchange.getRequestBody(), MAX_UPLOAD_BYTES)) {
                Files.copy(body, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            FileMetadata metadata = transferClient.upload(temporary, fileName);
            json(exchange, 201, metadataJson(metadata));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void download(HttpExchange exchange, String fileName) throws IOException {
        Path temporary = Files.createTempFile("fitnet-gateway-download-", ".part");
        try {
            FileMetadata metadata = transferClient.download(fileName, temporary);
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename*=UTF-8''" +
                    java.net.URLEncoder.encode(metadata.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
            exchange.getResponseHeaders().set("X-File-SHA256", metadata.sha256());
            exchange.sendResponseHeaders(200, metadata.size());
            try (InputStream content = Files.newInputStream(temporary)) { content.transferTo(exchange.getResponseBody()); }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (frontendOrigin.equals(origin)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", frontendOrigin);
            exchange.getResponseHeaders().set("Vary", "Origin");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Filename");
            exchange.getResponseHeaders().set("Access-Control-Expose-Headers", "Content-Disposition, X-File-SHA256");
        }
    }

    private static String filesJson(List<FileMetadata> files) {
        return "[" + String.join(",", files.stream().map(GatewayServer::metadataJson).toList()) + "]";
    }

    private static String metadataJson(FileMetadata file) {
        return "{\"fileName\":" + quote(file.fileName()) + ",\"size\":" + file.size() +
                ",\"sha256\":" + quote(file.sha256()) + "}";
    }

    private static void validateUploadFileName(String fileName) {
        if (!fileName.matches("[A-Za-z0-9][A-Za-z0-9._ ()\\[\\],&'-]{0,254}")
                || fileName.equals(".") || fileName.equals("..")
                || fileName.endsWith(".") || fileName.endsWith(" ")) {
            throw new IllegalArgumentException("File name must be 1-255 safe ASCII characters without path separators");
        }
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if (c < 0x20) result.append(String.format("\\u%04x", (int) c)); else result.append(c); }
            }
        }
        return result.append('"').toString();
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override public synchronized void close() {
        if (webSocketServer != null) {
            webSocketServer.close();
            webSocketServer = null;
        }
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public static void main(String[] args) throws Exception {
        SecureTransferClient client = null;
        try {
            client = SecureTransferClient.fromSystemProperties();
        } catch (IllegalStateException e) {
            System.err.println("Secure transfer is unavailable: " + e.getMessage());
        }
        GatewayServer gateway = new GatewayServer(
                Integer.parseInt(System.getProperty("gateway.port", Integer.toString(DEFAULT_PORT))),
                System.getProperty("gateway.frontend.origin", "http://localhost:3000"), client);
        gateway.start();
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            gateway.close();
            stopped.countDown();
        }));
        System.out.println("FITNet Gateway listening on " + System.getProperty("gateway.bind", "127.0.0.1") + ":" + gateway.getPort());
        stopped.await();
    }

    private static final class WebSocketServer implements AutoCloseable {
        private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private final int port;
        private final String tcpHost;
        private final int tcpPort;
        private volatile boolean running;
        private ServerSocket serverSocket;
        private ExecutorService workers;

        private WebSocketServer(int port, String tcpHost, int tcpPort) {
            this.port = port;
            this.tcpHost = tcpHost;
            this.tcpPort = tcpPort;
        }

        private void start() throws IOException {
            serverSocket = new ServerSocket(port, 32, java.net.InetAddress.getByName("127.0.0.1"));
            workers = Executors.newVirtualThreadPerTaskExecutor();
            running = true;
            workers.submit(() -> {
                while (running) {
                    try {
                        Socket browser = serverSocket.accept();
                        workers.submit(() -> handleBrowser(browser));
                    } catch (IOException e) {
                        if (running) System.err.println("WebSocket accept failed: " + e.getMessage());
                    }
                }
            });
            System.out.println("FITNet WebSocket bridge listening on ws://127.0.0.1:" + serverSocket.getLocalPort() + "/ws/chat");
        }

        private void handleBrowser(Socket browser) {
            try (Socket socket = browser;
                 TcpMessagingClient tcp = new TcpMessagingClient(tcpHost, tcpPort, message -> {
                     try {
                         sendTextFrame(socket, message);
                     } catch (IOException ignored) {
                     }
                 })) {
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(input, StandardCharsets.US_ASCII));
                String requestLine = reader.readLine();
                if (requestLine == null || !requestLine.startsWith("GET /ws/chat ")) throw new IOException("Invalid WebSocket request");
                String key = null;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    int colon = line.indexOf(':');
                    if (colon >= 0 && line.substring(0, colon).equalsIgnoreCase("Sec-WebSocket-Key")) {
                        key = line.substring(colon + 1).trim();
                    }
                }
                if (key == null) throw new IOException("Missing Sec-WebSocket-Key");
                String accept = Base64.getEncoder().encodeToString(
                        MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).getBytes(StandardCharsets.US_ASCII)));
                output.write(("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.flush();

                while (tcp.isConnected()) {
                    int first = input.read();
                    if (first == -1) break;
                    int second = input.read();
                    if (second == -1) break;
                    boolean masked = (second & 0x80) != 0;
                    long length = second & 0x7f;
                    if (length == 126) {
                        length = ((input.read() & 0xffL) << 8) | (input.read() & 0xffL);
                    } else if (length == 127) {
                        length = 0;
                        for (int i = 0; i < 8; i++) length = (length << 8) | (input.read() & 0xffL);
                    }
                    if (length > 65536) throw new IOException("WebSocket frame too large");
                    byte[] mask = new byte[4];
                    if (masked) {
                        if (input.read(mask) != 4) break;
                    }
                    byte[] payload = input.readNBytes((int) length);
                    if (payload.length != length) break;
                    int opcode = first & 0xf;
                    if (opcode == 8) break;
                    if (opcode == 9) {
                        sendFrame(socket, 10, payload);
                        continue;
                    }
                    if (opcode != 1) continue;
                    if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                    tcp.send(new String(payload, StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                System.err.println("WebSocket client disconnected: " + e.getMessage());
            }
        }

        private static void sendTextFrame(Socket socket, String message) throws IOException {
            sendFrame(socket, 1, message.getBytes(StandardCharsets.UTF_8));
        }

        private static synchronized void sendFrame(Socket socket, int opcode, byte[] payload) throws IOException {
            OutputStream output = socket.getOutputStream();
            output.write(0x80 | opcode);
            if (payload.length <= 125) {
                output.write(payload.length);
            } else if (payload.length <= 65535) {
                output.write(126);
                output.write((payload.length >>> 8) & 0xff);
                output.write(payload.length & 0xff);
            } else {
                output.write(127);
                for (int i = 56; i >= 0; i -= 8) output.write((payload.length >>> i) & 0xff);
            }
            output.write(payload);
            output.flush();
        }

        @Override
        public void close() {
            running = false;
            if (serverSocket != null) {
                try { serverSocket.close(); } catch (IOException ignored) { }
            }
            if (workers != null) workers.close();
        }
    }

    private static final class SizeLimitedInputStream extends FilterInputStream {
        private final long maximum;
        private long count;
        SizeLimitedInputStream(InputStream input, long maximum) { super(input); this.maximum = maximum; }
        @Override public int read() throws IOException {
            int value = super.read();
            if (value != -1 && ++count > maximum) throw new PayloadTooLargeException();
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0 && (count += read) > maximum) throw new PayloadTooLargeException();
            return read;
        }
    }

    private static final class PayloadTooLargeException extends IOException { }
}
