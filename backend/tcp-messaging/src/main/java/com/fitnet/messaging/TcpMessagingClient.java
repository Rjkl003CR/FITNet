package com.fitnet.messaging;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Non-blocking Java NIO {@link SocketChannel} client for the FITNet TCP messaging broker.
 *
 * <p>Uses a {@link Selector} and {@link ByteBuffer}s for asynchronous reads of
 * UTF-8, line-delimited JSON messages and queued non-blocking writes.</p>
 */
public final class TcpMessagingClient implements Closeable {
    private static final int READ_BUFFER_SIZE = 8192;
    private static final int MAX_MESSAGE_BYTES = 64 * 1024;
    private static final long CONNECT_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final SocketChannel channel;
    private final Selector selector;
    private final SelectionKey selectionKey;
    private final Consumer<String> onMessage;
    private final Queue<PendingWrite> writeQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread ioThread;

    public TcpMessagingClient(String host, int port, Consumer<String> onMessage) throws IOException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(onMessage, "onMessage");
        this.onMessage = onMessage;

        Selector openedSelector = Selector.open();
        SocketChannel openedChannel = SocketChannel.open();
        boolean initialized = false;
        try {
            openedChannel.configureBlocking(false);
            SelectionKey key = openedChannel.register(openedSelector, SelectionKey.OP_CONNECT);
            if (!openedChannel.connect(new InetSocketAddress(host, port))) {
                long deadline = System.nanoTime() + CONNECT_TIMEOUT_NANOS;
                while (!openedChannel.finishConnect()) {
                    long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                    if (remainingMs <= 0) {
                        throw new IOException("Timed out connecting to " + host + ":" + port);
                    }
                    openedSelector.select(remainingMs);
                    openedSelector.selectedKeys().clear();
                }
            }
            key.interestOps(SelectionKey.OP_READ);
            this.selector = openedSelector;
            this.channel = openedChannel;
            this.selectionKey = key;
            initialized = true;
        } finally {
            if (!initialized) {
                closeQuietly(openedChannel);
                closeQuietly(openedSelector);
            }
        }

        this.ioThread = new Thread(this::runIoLoop, "tcp-nio-client");
        this.ioThread.setDaemon(true);
        this.ioThread.start();
    }

    public synchronized void send(String message) throws IOException {
        Objects.requireNonNull(message, "message");
        if (closed.get() || !channel.isOpen()) {
            throw new IOException("TCP messaging client is closed.");
        }
        if (message.length() > MAX_MESSAGE_BYTES) {
            throw new IOException("Message is too large.");
        }
        byte[] payload = (message + "\n").getBytes(StandardCharsets.UTF_8);
        PendingWrite write = new PendingWrite(ByteBuffer.wrap(payload));
        writeQueue.add(write);
        selector.wakeup();
        if (closed.get()) {
            write.fail(new IOException("TCP messaging client is closed."));
        }
        if (Thread.currentThread() != ioThread) {
            write.await();
        }
    }

    public boolean isConnected() {
        return !closed.get() && channel.isConnected() && channel.isOpen();
    }

    private void runIoLoop() {
        ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        try {
            while (!closed.get() && selector.isOpen() && channel.isOpen()) {
                updateWriteInterest();
                selector.select();
                if (closed.get()) {
                    break;
                }
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isReadable() && !readAvailable(readBuffer, lineBuffer)) {
                        return;
                    }
                    if (key.isValid() && key.isWritable()) {
                        flushPendingWrites();
                    }
                }
            }
        } catch (IOException error) {
            if (!closed.get()) {
                failPendingWrites(error);
                throw new IllegalStateException("TCP messaging receive failed.", error);
            }
        } finally {
            closed.set(true);
            failPendingWrites(new IOException("TCP messaging client is closed."));
            closeQuietly(channel);
            closeQuietly(selector);
        }
    }

    private void updateWriteInterest() {
        if (!selectionKey.isValid()) {
            return;
        }
        int currentOps = selectionKey.interestOps();
        int targetOps = writeQueue.isEmpty()
                ? (currentOps & ~SelectionKey.OP_WRITE)
                : (currentOps | SelectionKey.OP_WRITE);
        if (currentOps != targetOps) {
            selectionKey.interestOps(targetOps);
        }
    }

    private boolean readAvailable(ByteBuffer readBuffer, ByteArrayOutputStream lineBuffer) throws IOException {
        readBuffer.clear();
        int bytesRead = channel.read(readBuffer);
        if (bytesRead == -1) {
            return false;
        }
        if (bytesRead == 0) {
            return true;
        }
        readBuffer.flip();
        while (readBuffer.hasRemaining()) {
            byte b = readBuffer.get();
            if (b == '\n') {
                byte[] bytes = lineBuffer.toByteArray();
                lineBuffer.reset();
                int length = bytes.length;
                if (length > 0 && bytes[length - 1] == '\r') {
                    length--;
                }
                onMessage.accept(new String(bytes, 0, length, StandardCharsets.UTF_8));
            } else {
                lineBuffer.write(b);
            }
        }
        return true;
    }

    private void flushPendingWrites() throws IOException {
        while (!writeQueue.isEmpty()) {
            PendingWrite head = writeQueue.peek();
            channel.write(head.buffer);
            if (head.buffer.hasRemaining()) {
                return;
            }
            writeQueue.poll();
            head.complete();
        }
        updateWriteInterest();
    }

    private void failPendingWrites(IOException error) {
        PendingWrite pending;
        while ((pending = writeQueue.poll()) != null) {
            pending.fail(error);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        failPendingWrites(new IOException("TCP messaging client is closed."));
        selector.wakeup();
        if (Thread.currentThread() != ioThread) {
            try {
                ioThread.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        closeQuietly(channel);
        closeQuietly(selector);
    }

    public static void main(String[] args) throws Exception {
        String host = System.getProperty("tcp.host", "127.0.0.1");
        int port = Integer.getInteger("tcp.port", TcpMessagingServer.DEFAULT_PORT);
        String user = args.length > 0 ? args[0] : System.getProperty("tcp.user", "student-1");
        try (TcpMessagingClient client = new TcpMessagingClient(host, port, System.out::println);
             var stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            System.out.println("Connected via NIO SocketChannel to " + host + ":" + port + " as " + user);
            String line;
            while ((line = stdin.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String payload = line.startsWith("{")
                        ? line
                        : "{\"messageId\":\"" + java.util.UUID.randomUUID() + "\","
                        + "\"type\":\"GROUP_MESSAGE\","
                        + "\"senderId\":\"" + escapeJson(user) + "\","
                        + "\"senderName\":\"" + escapeJson(user) + "\","
                        + "\"receiverId\":null,"
                        + "\"groupId\":\"fitnet-nio-demo\","
                        + "\"content\":\"" + escapeJson(line) + "\","
                        + "\"timestamp\":\"" + java.time.Instant.now() + "\"}";
                client.send(payload);
            }
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
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

    private static final class PendingWrite {
        private final ByteBuffer buffer;
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile IOException failure;

        private PendingWrite(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        private void complete() {
            done.countDown();
        }

        private void fail(IOException error) {
            if (this.failure == null) {
                this.failure = error;
            }
            done.countDown();
        }

        private void await() throws IOException {
            try {
                done.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while sending TCP message.", error);
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
