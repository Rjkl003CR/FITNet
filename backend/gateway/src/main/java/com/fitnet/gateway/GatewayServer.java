package com.fitnet.gateway;

import com.fitnet.transfer.FileMetadata;
import com.fitnet.transfer.SecureTransferClient;
import com.fitnet.transfer.TransferException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;

/** Local HTTP bridge between the Next.js browser UI and the TLS file server. */
public final class GatewayServer implements AutoCloseable {
    private static final int DEFAULT_PORT = 8080;
    private static final long MAX_UPLOAD_BYTES = 100L * 1024 * 1024;
    private final int port;
    private final String frontendOrigin;
    private final SecureTransferClient transferClient;
    private HttpServer server;
    private ExecutorService executor;

    public GatewayServer(int port, String frontendOrigin, SecureTransferClient transferClient) {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Port must be between 0 and 65535");
        this.port = port;
        this.frontendOrigin = frontendOrigin;
        this.transferClient = java.util.Objects.requireNonNull(transferClient, "transferClient");
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
                json(exchange, 200, filesJson(transferClient.listFiles()));
            } else if ("POST".equals(method) && "/api/files".equals(path)) {
                upload(exchange);
            } else if ("GET".equals(method) && path.startsWith("/api/files/")) {
                download(exchange, URLDecoder.decode(path.substring("/api/files/".length()), StandardCharsets.UTF_8));
            } else {
                json(exchange, 404, "{\"error\":\"Route not found\"}");
            }
        } catch (PayloadTooLargeException e) {
            json(exchange, 413, "{\"error\":\"File exceeds the 100 MB upload limit\"}");
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
        if (!fileName.matches("[A-Za-z0-9][A-Za-z0-9._ -]{0,127}")
                || fileName.equals(".") || fileName.equals("..")
                || fileName.endsWith(".") || fileName.endsWith(" ")) {
            throw new IllegalArgumentException("File name must be 1-128 safe ASCII characters without path separators");
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
        SecureTransferClient client = SecureTransferClient.fromSystemProperties();
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
