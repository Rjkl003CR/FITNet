package com.fitnet.messaging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Non-blocking Java NIO TCP messaging broker used by the Gateway.
 *
 * <p>Uses a single {@link Selector} event loop over {@link ServerSocketChannel}
 * and non-blocking {@link SocketChannel}s with {@link ByteBuffer} framing for
 * UTF-8, line-delimited JSON messages. Each valid line is broadcast to all
 * connected TCP clients. Routing, authentication, and persistence belong to the
 * Gateway/application layer.</p>
 */
public final class TcpMessagingServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 6000;
    private static final int READ_BUFFER_SIZE = 8192;
    private static final int MAX_MESSAGE_BYTES = 64 * 1024;

    private final int port;
    private final Object lifecycleLock = new Object();
    private final Set<ClientSession> clients = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile boolean running;
    private volatile int boundPort;
    private volatile Selector selector;
    private volatile ServerSocketChannel serverChannel;
    private volatile Thread selectorThread;
    private volatile CountDownLatch stoppedLatch;

    public TcpMessagingServer(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535.");
        }
        this.port = port;
    }

    public void start() throws IOException {
        synchronized (lifecycleLock) {
            if (running) {
                throw new IllegalStateException("TCP messaging server is already running.");
            }
            if (closed.get()) {
                throw new IllegalStateException("TCP messaging server is closed.");
            }
            Selector openedSelector = Selector.open();
            ServerSocketChannel openedServer = ServerSocketChannel.open();
            try {
                openedServer.configureBlocking(false);
                openedServer.bind(new InetSocketAddress(port));
                boundPort = ((InetSocketAddress) openedServer.getLocalAddress()).getPort();
                openedServer.register(openedSelector, SelectionKey.OP_ACCEPT);
            } catch (IOException error) {
                closeQuietly(openedServer);
                closeQuietly(openedSelector);
                throw error;
            }
            this.selector = openedSelector;
            this.serverChannel = openedServer;
            this.selectorThread = Thread.currentThread();
            this.stoppedLatch = new CountDownLatch(1);
            this.running = true;
        }

        System.out.println("FITNet TCP messaging NIO server listening on port " + boundPort);
        try {
            runEventLoop();
        } finally {
            running = false;
            cleanupResources();
            CountDownLatch latch = stoppedLatch;
            if (latch != null) {
                latch.countDown();
            }
        }
    }

    public int getPort() {
        int current = boundPort;
        return current != 0 ? current : port;
    }

    public int getConnectedClientCount() {
        clients.removeIf(client -> !client.isConnected());
        return clients.size();
    }

    @Override
    public void close() {
        closed.set(true);
        running = false;
        Selector activeSelector = selector;
        if (activeSelector != null) {
            activeSelector.wakeup();
        }
        CountDownLatch latch = stoppedLatch;
        if (latch != null && Thread.currentThread() != selectorThread) {
            try {
                latch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        cleanupResources();
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.getInteger("tcp.port", DEFAULT_PORT);
        TcpMessagingServer server = new TcpMessagingServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "tcp-nio-shutdown"));
        server.start();
    }

    void startUnchecked() {
        try {
            start();
        } catch (IOException error) {
            if (!closed.get()) {
                throw new IllegalStateException("TCP messaging server failed.", error);
            }
        }
    }

    private void runEventLoop() throws IOException {
        Selector activeSelector = selector;
        while (running && activeSelector != null && activeSelector.isOpen()) {
            activeSelector.select();
            if (!running) {
                break;
            }
            Iterator<SelectionKey> keys = activeSelector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                if (!key.isValid()) {
                    continue;
                }
                if (key.isAcceptable()) {
                    acceptClients();
                } else {
                    if (key.isReadable()) {
                        readFromClient(key);
                    }
                    if (key.isValid() && key.isWritable()) {
                        writeToClient(key);
                    }
                }
            }
        }
    }

    private void acceptClients() {
        ServerSocketChannel server = serverChannel;
        Selector activeSelector = selector;
        if (server == null || activeSelector == null) {
            return;
        }
        while (running) {
            SocketChannel clientChannel = null;
            try {
                clientChannel = server.accept();
                if (clientChannel == null) {
                    break;
                }
                clientChannel.configureBlocking(false);
                ClientSession session = new ClientSession(clientChannel);
                SelectionKey key = clientChannel.register(activeSelector, SelectionKey.OP_READ, session);
                session.attachKey(key);
                clients.add(session);
            } catch (IOException error) {
                closeQuietly(clientChannel);
                if (running) {
                    System.err.println("TCP accept failed: " + error.getMessage());
                }
                break;
            }
        }
    }

    private void readFromClient(SelectionKey key) {
        ClientSession session = (ClientSession) key.attachment();
        if (session == null) {
            return;
        }
        session.readAndProcess();
    }

    private void writeToClient(SelectionKey key) {
        ClientSession session = (ClientSession) key.attachment();
        if (session == null) {
            return;
        }
        session.flushWrites();
    }

    private void broadcast(String message) {
        byte[] payload = (message + "\n").getBytes(StandardCharsets.UTF_8);
        for (ClientSession client : clients) {
            client.enqueue(ByteBuffer.wrap(payload));
        }
    }

    private void cleanupResources() {
        synchronized (lifecycleLock) {
            for (ClientSession client : clients) {
                client.close();
            }
            clients.clear();
            closeQuietly(serverChannel);
            serverChannel = null;
            closeQuietly(selector);
            selector = null;
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Resource is already closing or closed.
        }
    }

    private final class ClientSession implements AutoCloseable {
        private final SocketChannel channel;
        private final ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
        private SelectionKey key;

        private ClientSession(SocketChannel channel) {
            this.channel = channel;
        }

        private void attachKey(SelectionKey key) {
            this.key = key;
        }

        private void readAndProcess() {
            readBuffer.clear();
            int bytesRead;
            try {
                bytesRead = channel.read(readBuffer);
            } catch (IOException error) {
                if (running) {
                    System.err.println("TCP client disconnected: " + error.getMessage());
                }
                close();
                return;
            }

            if (bytesRead == -1) {
                close();
                return;
            }
            if (bytesRead == 0) {
                return;
            }

            readBuffer.flip();
            while (readBuffer.hasRemaining()) {
                byte b = readBuffer.get();
                if (b == '\n') {
                    String line = extractLine();
                    if (MessageJson.isMessage(line)) {
                        broadcast(line);
                    }
                } else {
                    if (lineBuffer.size() >= MAX_MESSAGE_BYTES) {
                        close();
                        return;
                    }
                    lineBuffer.write(b);
                }
            }
        }

        private String extractLine() {
            byte[] bytes = lineBuffer.toByteArray();
            lineBuffer.reset();
            int length = bytes.length;
            if (length > 0 && bytes[length - 1] == '\r') {
                length--;
            }
            return new String(bytes, 0, length, StandardCharsets.UTF_8);
        }

        private void enqueue(ByteBuffer buffer) {
            if (!isConnected()) {
                close();
                return;
            }
            writeQueue.add(buffer);
            flushWrites();
        }

        private void flushWrites() {
            while (!writeQueue.isEmpty()) {
                ByteBuffer head = writeQueue.peek();
                try {
                    channel.write(head);
                } catch (IOException error) {
                    close();
                    return;
                }
                if (head.hasRemaining()) {
                    setWriteInterest(true);
                    return;
                }
                writeQueue.poll();
            }
            setWriteInterest(false);
        }

        private void setWriteInterest(boolean enabled) {
            SelectionKey selectionKey = key;
            if (selectionKey == null || !selectionKey.isValid()) {
                return;
            }
            int currentOps = selectionKey.interestOps();
            int targetOps = enabled
                    ? (currentOps | SelectionKey.OP_WRITE)
                    : (currentOps & ~SelectionKey.OP_WRITE);
            if (currentOps != targetOps) {
                selectionKey.interestOps(targetOps);
            }
        }

        private boolean isConnected() {
            return channel.isConnected() && channel.isOpen();
        }

        @Override
        public void close() {
            clients.remove(this);
            writeQueue.clear();
            if (key != null) {
                key.cancel();
            }
            closeQuietly(channel);
        }
    }

    static final class MessageJson {
        private MessageJson() {}

        static boolean isMessage(String value) {
            if (value == null || value.length() > MAX_MESSAGE_BYTES) {
                return false;
            }
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
