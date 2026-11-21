package com.fitnet.messaging;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Line-delimited JSON TCP client for the FITNet messaging broker.
 */
public final class TcpMessagingClient implements Closeable {
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();

    public TcpMessagingClient(String host, int port, Consumer<String> onMessage) throws IOException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(onMessage, "onMessage");
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        worker.submit(() -> receive(onMessage));
    }

    public synchronized void send(String message) throws IOException {
        Objects.requireNonNull(message, "message");
        if (closed.get()) throw new IOException("TCP messaging client is closed.");
        if (message.length() > 64 * 1024) throw new IOException("Message is too large.");
        writer.write(message);
        writer.newLine();
        writer.flush();
    }

    public boolean isConnected() {
        return !closed.get() && socket.isConnected() && !socket.isClosed();
    }

    private void receive(Consumer<String> onMessage) {
        try {
            String line;
            while (!closed.get() && (line = reader.readLine()) != null) {
                onMessage.accept(line);
            }
        } catch (IOException error) {
            if (!closed.get()) throw new IllegalStateException("TCP messaging receive failed.", error);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        try {
            socket.close();
        } finally {
            worker.shutdownNow();
        }
    }
}
