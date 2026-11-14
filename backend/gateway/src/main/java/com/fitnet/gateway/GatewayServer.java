package com.fitnet.gateway;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal Java Gateway: WebSocket /ws/chat to the TCP messaging broker.
 */
public final class GatewayServer implements AutoCloseable {
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final int DEFAULT_TCP_PORT = 6000;
    private final int httpPort;
    private final String tcpHost;
    private final int tcpPort;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<WebSocketConnection, Socket> connections = new ConcurrentHashMap<>();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public GatewayServer(int httpPort, String tcpHost, int tcpPort) {
        this.httpPort = httpPort;
        this.tcpHost = tcpHost;
        this.tcpPort = tcpPort;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(httpPort);
        running = true;
        System.out.println("FITNet Gateway listening on port " + httpPort);
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                workers.submit(() -> handle(socket));
            } catch (IOException error) {
                if (running) System.err.println("Gateway accept failed: " + error.getMessage());
            }
        }
    }

    private void handle(Socket socket) {
        try {
            String headerBlock = readHeaders(socket.getInputStream());
            if (headerBlock == null) {
                socket.close();
                return;
            }
            Map<String, String> headers = new ConcurrentHashMap<>();
            String[] headerLines = headerBlock.split("\r\n");
            String requestLine = headerLines[0];
            for (int index = 1; index < headerLines.length; index++) {
                String line = headerLines[index];
                int separator = line.indexOf(':');
                if (separator > 0) headers.put(line.substring(0, separator).trim().toLowerCase(), line.substring(separator + 1).trim());
            }
            String[] requestParts = requestLine.split(" ");
            String path = requestParts.length > 1 ? requestParts[1] : "";
            if (!"/ws/chat".equals(path) || !"websocket".equalsIgnoreCase(headers.get("upgrade"))) {
                writeHttpResponse(socket, 404, "Not Found", "FITNet Gateway");
                return;
            }
            String key = headers.get("sec-websocket-key");
            if (key == null) {
                writeHttpResponse(socket, 400, "Bad Request", "Missing WebSocket key");
                return;
            }
            WebSocketConnection webSocket = new WebSocketConnection(socket);
            webSocket.accept(key);
            Socket tcpSocket = new Socket(tcpHost, tcpPort);
            connections.put(webSocket, tcpSocket);
            bridge(webSocket, tcpSocket);
        } catch (IOException error) {
            try { socket.close(); } catch (IOException ignored) {}
            if (running) System.err.println("Gateway connection failed: " + error.getMessage());
        }
    }

    private static String readHeaders(java.io.InputStream input) throws IOException {
        StringBuilder headers = new StringBuilder();
        int previous = -1;
        int current;
        while ((current = input.read()) != -1) {
            headers.append((char) current);
            if (previous == '\r' && current == '\n' && headers.length() >= 4
                    && headers.substring(headers.length() - 4).equals("\r\n\r\n")) {
                return headers.substring(0, headers.length() - 2);
            }
            previous = current;
            if (headers.length() > 16 * 1024) throw new IOException("HTTP headers are too large.");
        }
        return null;
    }

    private void bridge(WebSocketConnection webSocket, Socket tcpSocket) {
        workers.submit(() -> {
            try (tcpSocket;
                 BufferedReader tcpReader = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream(), StandardCharsets.UTF_8))) {
                String message;
                while ((message = tcpReader.readLine()) != null) webSocket.sendText(message);
            } catch (IOException ignored) {
                // The peer closing a bridge is a normal disconnect.
            } finally {
                connections.remove(webSocket);
                webSocket.close();
            }
        });
        workers.submit(() -> {
            try (BufferedWriter tcpWriter = new BufferedWriter(new OutputStreamWriter(tcpSocket.getOutputStream(), StandardCharsets.UTF_8))) {
                String message;
                while ((message = webSocket.readText()) != null) {
                    tcpWriter.write(message);
                    tcpWriter.newLine();
                    tcpWriter.flush();
                }
            } catch (IOException ignored) {
                // The peer closing a bridge is a normal disconnect.
            } finally {
                try { tcpSocket.close(); } catch (IOException ignored) {}
                webSocket.close();
                connections.remove(webSocket);
            }
        });
    }

    private static void writeHttpResponse(Socket socket, int code, String status, String body) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
        writer.write("HTTP/1.1 " + code + " " + status + "\r\nContent-Type: text/plain\r\nContent-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body);
        writer.flush();
        socket.close();
    }

    @Override
    public void close() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        connections.forEach((webSocket, tcpSocket) -> { try { tcpSocket.close(); } catch (IOException ignored) {} webSocket.close(); });
        workers.close();
    }

    public static void main(String[] args) throws IOException {
        int httpPort = Integer.getInteger("gateway.port", DEFAULT_HTTP_PORT);
        String tcpHost = System.getProperty("tcp.host", "localhost");
        int tcpPort = Integer.getInteger("tcp.port", DEFAULT_TCP_PORT);
        GatewayServer server = new GatewayServer(httpPort, tcpHost, tcpPort);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
    }

    private static final class WebSocketConnection implements AutoCloseable {
        private final Socket socket;
        private final java.io.InputStream input;
        private final java.io.OutputStream output;

        private WebSocketConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
        }

        private void accept(String key) throws IOException {
            String accept = Base64.getEncoder().encodeToString(sha1(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"));
            output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();
        }

        private synchronized void sendText(String message) throws IOException {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            if (data.length > 65535) throw new IOException("WebSocket message too large.");
            output.write(0x81);
            if (data.length < 126) output.write(data.length);
            else { output.write(126); output.write((data.length >> 8) & 255); output.write(data.length & 255); }
            output.write(data);
            output.flush();
        }

        private String readText() throws IOException {
            int first = input.read();
            if (first < 0) return null;
            int second = input.read();
            if (second < 0 || (first & 0x0f) != 1) throw new IOException("Unsupported WebSocket frame.");
            boolean masked = (second & 0x80) != 0;
            int length = second & 0x7f;
            if (length == 126) length = (input.read() << 8) | input.read();
            if (length == 127) throw new IOException("WebSocket payload too large.");
            if (!masked) throw new IOException("Client WebSocket frames must be masked.");
            byte[] mask = input.readNBytes(4);
            byte[] data = input.readNBytes(length);
            if (mask.length != 4 || data.length != length) throw new IOException("Incomplete WebSocket frame.");
            for (int i = 0; i < data.length; i++) data[i] ^= mask[i % 4];
            return new String(data, StandardCharsets.UTF_8);
        }

        @Override public void close() { try { socket.close(); } catch (IOException ignored) {} }

        private static byte[] sha1(String value) throws IOException {
            try { return MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.US_ASCII)); }
            catch (NoSuchAlgorithmException error) { throw new IOException("SHA-1 is unavailable.", error); }
        }
    }
}
