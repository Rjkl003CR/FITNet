package com.fitnet.transfer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Concurrent TLS file server. Files are committed only after size and hash checks pass. */
public final class SecureTransferServer implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(SecureTransferServer.class.getName());
    private static final int DEFAULT_PORT = 5004;
    private static final long DEFAULT_MAX_FILE_BYTES = 500L * 1024 * 1024;
    private static final int ERROR_DRAIN_BUFFER_SIZE = 8 * 1024;
    private static final long ERROR_DRAIN_MAX_BYTES = 4L * 1024 * 1024;
    private static final long ERROR_DRAIN_MAX_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final int ERROR_DRAIN_READ_TIMEOUT_MILLIS = 250;
    private final int port;
    private final Path storageDirectory;
    private final SSLContext sslContext;
    private final long maxFileBytes;
    private final Set<SSLSocket> activeSockets = ConcurrentHashMap.newKeySet();
    private SSLServerSocket serverSocket;
    private ExecutorService workers;
    private Thread acceptThread;
    private volatile boolean closed;

    public SecureTransferServer(int port, Path storageDirectory, SSLContext sslContext, long maxFileBytes) {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Port must be between 0 and 65535");
        if (maxFileBytes < 0) throw new IllegalArgumentException("Maximum file size cannot be negative");
        this.port = port;
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
        this.sslContext = java.util.Objects.requireNonNull(sslContext, "sslContext");
        this.maxFileBytes = maxFileBytes;
    }

    public SecureTransferServer(int port, Path storageDirectory, SSLContext sslContext) {
        this(port, storageDirectory, sslContext, DEFAULT_MAX_FILE_BYTES);
    }

    public synchronized void start() throws IOException {
        if (closed) throw new IllegalStateException("Server is closed");
        if (serverSocket != null) return;
        Files.createDirectories(storageDirectory);
        serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(port, 50,
                InetAddress.getByName(System.getProperty("secure.transfer.bind", "0.0.0.0")));
        serverSocket.setEnabledProtocols(SecureTransferClient.availableTlsProtocols(serverSocket.getSupportedProtocols()));
        workers = Executors.newFixedThreadPool(8, task -> {
            return new Thread(task, "secure-transfer-worker");
        });
        acceptThread = new Thread(this::acceptConnections, "secure-transfer-accept");
        acceptThread.start();
    }

    public synchronized int getPort() {
        if (serverSocket == null) throw new IllegalStateException("Server has not started");
        return serverSocket.getLocalPort();
    }

    private void acceptConnections() {
        while (!closed) {
            SSLSocket accepted = null;
            try {
                accepted = (SSLSocket) serverSocket.accept();
                if (closed) {
                    closeAcceptedSocket(accepted);
                    break;
                }
                activeSockets.add(accepted);
                SSLSocket workerSocket = accepted;
                workers.execute(() -> handle(workerSocket));
                accepted = null;
            } catch (IOException e) {
                closeAcceptedSocket(accepted);
                if (!closed) {
                    LOG.log(System.Logger.Level.WARNING, "TLS accept failed; stopping the transfer listener", e);
                    close();
                }
                break;
            } catch (java.util.concurrent.RejectedExecutionException e) {
                closeAcceptedSocket(accepted);
                if (closed) break;
                LOG.log(System.Logger.Level.WARNING, "Connection rejected by the transfer worker pool");
            }
        }
    }

    private void closeAcceptedSocket(SSLSocket socket) {
        if (socket == null) return;
        activeSockets.remove(socket);
        try { socket.close(); }
        catch (IOException e) { LOG.log(System.Logger.Level.DEBUG, "Could not close a rejected TLS connection", e); }
    }

    private void handle(SSLSocket socket) {
        try (socket) {
            socket.setSoTimeout(SecureTransferClient.SOCKET_TIMEOUT_MILLIS);
            socket.setEnabledProtocols(SecureTransferClient.availableTlsProtocols(socket.getSupportedProtocols()));
            socket.startHandshake();
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                 DataOutputStream output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
                int version = 0;
                try {
                    if (input.readInt() != SecureTransferClient.MAGIC) {
                        LOG.log(System.Logger.Level.DEBUG, "Closing connection with an invalid transfer signature");
                        return;
                    }
                    version = input.readUnsignedByte();
                    if (version == SecureTransferClient.LEGACY_VERSION) {
                        processOperation(input, output, input.readUTF(), version);
                    } else if (version == SecureTransferClient.VERSION) {
                        processOperation(input, output, readV2Operation(input.readUnsignedByte()), version);
                    } else {
                        LOG.log(System.Logger.Level.DEBUG, "Closing connection with an unsupported transfer protocol version");
                        return;
                    }
                } catch (Exception e) {
                    if (version != SecureTransferClient.LEGACY_VERSION && version != SecureTransferClient.VERSION) {
                        LOG.log(System.Logger.Level.DEBUG, "Closing incomplete transfer protocol header");
                        return;
                    }
                    String message = e instanceof TransferException && e.getMessage() != null
                            ? e.getMessage() : "Transfer request failed";
                    try {
                        writeStatus(output, version, SecureTransferClient.ERROR, message);
                        drainAfterError(socket, input);
                    } catch (IOException ignored) { }
                    if (!(e instanceof TransferException)) LOG.log(System.Logger.Level.DEBUG, "Transfer failed", e);
                }
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.DEBUG, "Transfer connection failed: " + e.getMessage());
        } finally {
            activeSockets.remove(socket);
        }
    }

    /** Half-closes TLS output and drains bounded peer bytes so Windows does not reset before error status is read. */
    private void drainAfterError(SSLSocket socket, DataInputStream input) {
        if (closed) return;
        try {
            socket.shutdownOutput();
        } catch (IOException | UnsupportedOperationException e) {
            LOG.log(System.Logger.Level.DEBUG, "Could not half-close TLS output after error", e);
            return;
        }
        byte[] discard = new byte[ERROR_DRAIN_BUFFER_SIZE];
        long deadline = System.nanoTime() + ERROR_DRAIN_MAX_NANOS;
        long drained = 0;
        while (!closed && !Thread.currentThread().isInterrupted() && drained < ERROR_DRAIN_MAX_BYTES) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) break;
            int timeoutMillis = (int) Math.min(ERROR_DRAIN_READ_TIMEOUT_MILLIS,
                    Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            int toRead = (int) Math.min(discard.length, ERROR_DRAIN_MAX_BYTES - drained);
            try {
                socket.setSoTimeout(timeoutMillis);
                int read = input.read(discard, 0, toRead);
                if (read < 0) break;
                drained += read;
            } catch (IOException e) {
                break;
            }
        }
    }

    private void processOperation(DataInputStream input, DataOutputStream output, String operation, int version)
            throws IOException {
        switch (operation) {
            case "UPLOAD" -> receiveUpload(input, output, version);
            case "DOWNLOAD" -> sendDownload(input, output, version);
            case "LIST" -> sendList(output, version);
            default -> throw new TransferException("Unknown transfer operation");
        }
    }

    private static String readV2Operation(int operation) throws TransferException {
        return switch (operation) {
            case SecureTransferClient.OP_UPLOAD -> "UPLOAD";
            case SecureTransferClient.OP_DOWNLOAD -> "DOWNLOAD";
            case SecureTransferClient.OP_LIST -> "LIST";
            default -> throw new TransferException("Unknown transfer operation");
        };
    }

    private void receiveUpload(DataInputStream input, DataOutputStream output, int version) throws IOException {
        String fileName = readText(input, version, SecureTransferClient.MAX_FILENAME_BYTES);
        validateFileName(fileName);
        long size = input.readLong();
        String expectedHash = readText(input, version, SecureTransferClient.MAX_HASH_BYTES);
        if (size < 0 || size > maxFileBytes) throw new TransferException("File exceeds the configured size limit");
        if (!expectedHash.matches("[a-f0-9]{64}")) throw new TransferException("Invalid SHA-256 metadata");

        Path target = resolve(fileName);
        Path temporary = Files.createTempFile(storageDirectory, ".fitnet-upload-", ".part");
        boolean committed = false;
        try {
            MessageDigest digest = SecureTransferClient.newDigest();
            byte[] buffer = new byte[SecureTransferClient.BUFFER_SIZE];
            long remaining = size;
            try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                while (remaining > 0) {
                    int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) throw new TransferException("Connection ended before the complete file arrived");
                    file.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    remaining -= read;
                }
            }
            String actualHash = java.util.HexFormat.of().formatHex(digest.digest());
            if (!MessageDigest.isEqual(actualHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    expectedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new TransferException("Uploaded file failed SHA-256 verification");
            }
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            committed = true;
            writeStatus(output, version, SecureTransferClient.OK, "Upload verified and saved");
        } finally {
            if (!committed) Files.deleteIfExists(temporary);
        }
    }

    private void sendDownload(DataInputStream input, DataOutputStream output, int version) throws IOException {
        String fileName = readText(input, version, SecureTransferClient.MAX_FILENAME_BYTES);
        validateFileName(fileName);
        Path file = resolve(fileName);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new TransferException("File not found");
        long size = Files.size(file);
        if (size > maxFileBytes) throw new TransferException("File exceeds the configured size limit");
        String hash = SecureTransferClient.sha256(file);
        writeStatus(output, version, SecureTransferClient.OK, "File ready");
        writeText(output, version, fileName, SecureTransferClient.MAX_FILENAME_BYTES);
        output.writeLong(size);
        writeText(output, version, hash, SecureTransferClient.MAX_HASH_BYTES);
        try (InputStreamFromPath content = new InputStreamFromPath(file)) {
            content.transferTo(output);
        }
        output.flush();
    }

    private void sendList(DataOutputStream output, int version) throws IOException {
        List<FileMetadata> files;
        try (var entries = Files.list(storageDirectory)) {
            files = entries.filter(Files::isRegularFile)
                    .map(path -> {
                        try { return new FileMetadata(path.getFileName().toString(), Files.size(path), SecureTransferClient.sha256(path)); }
                        catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                    })
                    .sorted(Comparator.comparing(FileMetadata::fileName, String.CASE_INSENSITIVE_ORDER))
                    .limit(10_000).toList();
        } catch (java.io.UncheckedIOException e) { throw e.getCause(); }
        writeStatus(output, version, SecureTransferClient.OK, "File list ready");
        output.writeInt(files.size());
        for (FileMetadata file : files) {
            writeText(output, version, file.fileName(), SecureTransferClient.MAX_FILENAME_BYTES);
            output.writeLong(file.size());
            writeText(output, version, file.sha256(), SecureTransferClient.MAX_HASH_BYTES);
        }
        output.flush();
    }

    static void validateFileName(String fileName) throws TransferException {
        if (fileName == null || !fileName.matches("[A-Za-z0-9][A-Za-z0-9._ ()\\[\\],&'-]{0,254}")
                || fileName.equals(".") || fileName.equals("..") || fileName.endsWith(".") || fileName.endsWith(" ")) {
            throw new TransferException("File name must be 1-255 safe ASCII characters without path separators");
        }
    }

    private Path resolve(String fileName) throws IOException {
        Path resolved = storageDirectory.resolve(fileName).normalize();
        if (!resolved.getParent().equals(storageDirectory)) throw new TransferException("Invalid file path");
        return resolved;
    }

    private static String readText(DataInputStream input, int version, int maxBytes) throws IOException {
        return version == SecureTransferClient.VERSION
                ? SecureTransferClient.readV2String(input, maxBytes) : input.readUTF();
    }

    private static void writeText(DataOutputStream output, int version, String value, int maxBytes) throws IOException {
        if (version == SecureTransferClient.VERSION) SecureTransferClient.writeV2String(output, value, maxBytes);
        else output.writeUTF(value);
    }

    private static void writeStatus(DataOutputStream output, int version, byte status, String message) throws IOException {
        output.writeByte(status);
        writeText(output, version, message, SecureTransferClient.MAX_STATUS_BYTES);
        output.flush();
    }

    @Override public synchronized void close() {
        closed = true;
        if (serverSocket != null) try { serverSocket.close(); } catch (IOException ignored) { }
        if (workers != null) {
            workers.shutdown();
            try {
                if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                    closeActiveSockets();
                    workers.shutdownNow();
                    if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                        LOG.log(System.Logger.Level.WARNING, "Secure transfer workers did not stop promptly");
                    }
                }
            } catch (InterruptedException e) {
                closeActiveSockets();
                workers.shutdownNow();
                Thread.currentThread().interrupt();
            } finally {
                activeSockets.clear();
            }
        }
    }

    private void closeActiveSockets() {
        for (SSLSocket socket : activeSockets) {
            try { socket.close(); }
            catch (IOException e) { LOG.log(System.Logger.Level.DEBUG, "Could not close an active TLS connection", e); }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("secure.transfer.port", "5004"));
        long max = Long.parseLong(System.getProperty("secure.transfer.max.bytes", Long.toString(DEFAULT_MAX_FILE_BYTES)));
        Path storage = Path.of(System.getProperty("secure.transfer.storage", "secure-transfer/storage"));
        SecureTransferServer server = new SecureTransferServer(port, storage, TlsContexts.serverFromSystemProperties(), max);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        System.out.println("FITNet TLS file server listening on port " + server.getPort() + " and storing files in " + storage.toAbsolutePath());
    }

    /** InputStream facade kept private so file content is always streamed. */
    private static final class InputStreamFromPath extends java.io.FilterInputStream {
        InputStreamFromPath(Path path) throws IOException { super(new BufferedInputStream(Files.newInputStream(path))); }
    }
}
