package com.fitnet.messaging;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Line-delimited JSON TCP broker used by the Gateway.
 *
 * <p>Each valid line is broadcast to the connected TCP clients. Routing,
 * authentication, and persistence belong to the Gateway/application layer.</p>
 */
public final class TcpMessagingServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 6000;

    private final int port;
    private final Set<Client> clients = ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public TcpMessagingServer(int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535.");
        this.port = port;
    }

    public void start() throws IOException {
        if (running) throw new IllegalStateException("TCP messaging server is already running.");
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("FITNet TCP messaging server listening on port " + port);
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Client client = new Client(socket);
                clients.add(client);
                workers.submit(client);
            } catch (IOException error) {
                if (running) System.err.println("TCP accept failed: " + error.getMessage());
            }
        }
    }

    private void broadcast(String message) {
        clients.removeIf(client -> !client.send(message));
    }

    @Override
    public void close() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException error) {
            System.err.println("TCP server shutdown failed: " + error.getMessage());
        }
        clients.forEach(Client::close);
        workers.close();
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.getInteger("tcp.port", DEFAULT_PORT);
        TcpMessagingServer server = new TcpMessagingServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
    }

    private final class Client implements Runnable, AutoCloseable {
        private final Socket socket;
        private final BufferedWriter writer;

        private Client(Socket socket) throws IOException {
            this.socket = socket;
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public void run() {
            try (socket;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (MessageJson.isMessage(line)) broadcast(line);
                }
            } catch (IOException error) {
                if (running) System.err.println("TCP client disconnected: " + error.getMessage());
            } finally {
                clients.remove(this);
            }
        }

        private synchronized boolean send(String message) {
            try {
                writer.write(message);
                writer.newLine();
                writer.flush();
                return true;
            } catch (IOException error) {
                close();
                return false;
            }
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // The connection is already being closed.
            }
        }
    }

    static final class MessageJson {
        private MessageJson() {}

        static boolean isMessage(String value) {
            if (value == null || value.length() > 64 * 1024) return false;
            return hasString(value, "messageId")
                    && hasString(value, "senderId")
                    && hasString(value, "senderName")
                    && hasString(value, "content")
                    && hasString(value, "timestamp")
                    && (value.contains("\"type\":\"PRIVATE_MESSAGE\"")
                    || value.contains("\"type\":\"GROUP_MESSAGE\"")
                    || value.contains("\"type\":\"SYSTEM\""));
        }

        private static boolean hasString(String json, String field) {
            return json.matches("(?s).*\"" + field + "\"\\s*:\\s*\"(?:\\\\.|[^\"\\\\])*\".*");
        }
    }
}
