package com.fitnet.transfer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** TLS client for the FITNet binary-safe file transfer protocol. */
public final class SecureTransferClient {
    static final int MAGIC = 0x464E5431; // FNT1
    static final byte LEGACY_VERSION = 1;
    static final byte VERSION = 2;
    static final int OP_UPLOAD = 1;
    static final int OP_DOWNLOAD = 2;
    static final int OP_LIST = 3;
    static final int MAX_FILENAME_BYTES = 255;
    static final int MAX_HASH_BYTES = 64;
    static final int MAX_STATUS_BYTES = 1024;
    static final long DEFAULT_MAX_FILE_BYTES = 500L * 1024 * 1024;
    static final byte OK = 0;
    static final byte ERROR = 1;
    static final int BUFFER_SIZE = 64 * 1024;
    static final int SOCKET_TIMEOUT_MILLIS = 30_000;
    static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final long WRITE_FAILURE_STATUS_WAIT_MILLIS = 1_000;

    private final String host;
    private final int port;
    private final SSLContext sslContext;
    private final int protocolVersion;

    public SecureTransferClient(String host, int port, SSLContext sslContext) {
        this(host, port, sslContext, configuredProtocolVersion());
    }

    SecureTransferClient(String host, int port, SSLContext sslContext, int protocolVersion) {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("Host is required");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535");
        if (protocolVersion != LEGACY_VERSION && protocolVersion != VERSION) {
            throw new IllegalArgumentException("Unsupported transfer protocol version: " + protocolVersion);
        }
        this.host = host;
        this.port = port;
        this.sslContext = java.util.Objects.requireNonNull(sslContext, "sslContext");
        this.protocolVersion = protocolVersion;
    }

    public static SecureTransferClient fromSystemProperties() throws Exception {
        return new SecureTransferClient(System.getProperty("secure.transfer.host", "localhost"),
                Integer.parseInt(System.getProperty("secure.transfer.port", "5004")),
                TlsContexts.clientFromSystemProperties());
    }

    public FileMetadata upload(Path source, String remoteFileName) throws IOException {
        Path file = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) throw new IOException("Upload source is not a regular file");
        SecureTransferServer.validateFileName(remoteFileName);
        long size = Files.size(file);
        if (size > configuredMaxFileBytes()) throw new TransferException("File exceeds the configured size limit");
        String hash = sha256(file);
        return withSocket((socket, input, output) -> {
            writeRequestHeader(output, "UPLOAD");
            writeRequestText(output, remoteFileName, MAX_FILENAME_BYTES);
            output.writeLong(size);
            writeRequestText(output, hash, MAX_HASH_BYTES);
            output.flush();

            AtomicBoolean stopWriting = new AtomicBoolean(false);
            AtomicReference<Throwable> statusFailure = new AtomicReference<>();
            Thread statusReader = Thread.ofVirtual().name("secure-transfer-upload-status").start(() -> {
                try {
                    readRequestStatus(input);
                } catch (Throwable failure) {
                    statusFailure.set(failure);
                } finally {
                    stopWriting.set(true);
                }
            });

            IOException localReadFailure = null;
            IOException writeFailure = null;
            long remaining = size;
            boolean bodyCompleted = false;
            boolean interrupted;
            try {
                try (InputStream content = new BufferedInputStream(Files.newInputStream(file))) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while (remaining > 0 && !stopWriting.get()) {
                        int read;
                        try {
                            read = content.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        } catch (IOException e) {
                            localReadFailure = e;
                            break;
                        }
                        if (read < 0) {
                            localReadFailure = new IOException("Upload source changed size while streaming");
                            break;
                        }
                        try {
                            output.write(buffer, 0, read);
                        } catch (IOException e) {
                            writeFailure = e;
                            break;
                        }
                        remaining -= read;
                    }
                    if (localReadFailure == null && writeFailure == null && !stopWriting.get()) {
                        try {
                            output.flush();
                        } catch (IOException e) {
                            writeFailure = e;
                        }
                    }
                }
                bodyCompleted = true;
            } finally {
                if (!bodyCompleted || localReadFailure != null) {
                    closeQuietly(socket);
                } else if (writeFailure != null) {
                    shutdownOutputQuietly(socket);
                }
                interrupted = joinStatusReader(statusReader, socket,
                        writeFailure != null ? WRITE_FAILURE_STATUS_WAIT_MILLIS : 0L);
            }

            Throwable statusError = statusFailure.get();
            if (localReadFailure != null) throw localReadFailure;
            if (statusError instanceof TransferException transferFailure) throw transferFailure;
            if (statusError instanceof Error error) throw error;
            if (statusError instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (writeFailure != null) throw writeFailure;
            if (statusError instanceof IOException ioFailure) throw ioFailure;
            if (interrupted) throw new InterruptedIOException("Interrupted while waiting for upload verification");
            if (remaining > 0) throw new TransferException("Server accepted upload before all bytes were transmitted");
            return new FileMetadata(remoteFileName, size, hash);
        });
    }

    public FileMetadata download(String remoteFileName, Path destination) throws IOException {
        SecureTransferServer.validateFileName(remoteFileName);
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) throw new IOException("Download destination must have a parent directory");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".fitnet-download-", ".part");
        boolean complete = false;
        try {
            FileMetadata metadata = withSocket((socket, input, output) -> {
                writeRequestHeader(output, "DOWNLOAD");
                writeRequestText(output, remoteFileName, MAX_FILENAME_BYTES);
                output.flush();
                readRequestStatus(input);
                String fileName = readResponseText(input, MAX_FILENAME_BYTES);
                long size = input.readLong();
                String expectedHash = readResponseText(input, MAX_HASH_BYTES);
                if (size < 0 || size > configuredMaxFileBytes()) {
                    throw new TransferException("Server returned an invalid file size");
                }
                FileMetadata result;
                try { result = new FileMetadata(fileName, size, expectedHash); }
                catch (IllegalArgumentException e) { throw new TransferException("Server returned invalid file metadata", e); }
                try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                    copyExactly(input, file, size);
                }
                String actualHash = sha256(temporary);
                if (!MessageDigest.isEqual(actualHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        expectedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                    throw new TransferException("Downloaded file failed SHA-256 verification");
                }
                return result;
            });
            moveIntoPlace(temporary, target);
            complete = true;
            return metadata;
        } finally {
            if (!complete) Files.deleteIfExists(temporary);
        }
    }

    public List<FileMetadata> listFiles() throws IOException {
        return withSocket((socket, input, output) -> {
            writeRequestHeader(output, "LIST");
            output.flush();
            readRequestStatus(input);
            int count = input.readInt();
            if (count < 0 || count > 10_000) throw new TransferException("Server returned an invalid file list");
            List<FileMetadata> files = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                try { files.add(new FileMetadata(readResponseText(input, MAX_FILENAME_BYTES), input.readLong(),
                        readResponseText(input, MAX_HASH_BYTES))); }
                catch (IllegalArgumentException e) { throw new TransferException("Server returned invalid file metadata", e); }
            }
            return List.copyOf(files);
        });
    }

    private <T> T withSocket(IoExchange<T> exchange) throws IOException {
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket socket;
        try {
            socket = (SSLSocket) factory.createSocket();
        } catch (IOException e) {
            throw new TransferException("TLS connection to " + host + ":" + port + " failed", e);
        }
        try (socket) {
            try {
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
                socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                socket.setEnabledProtocols(availableTlsProtocols(socket.getSupportedProtocols()));
                socket.setSSLParameters(parametersForHost(socket.getSSLParameters()));
                socket.startHandshake();
            } catch (IOException e) {
                throw new TransferException("TLS connection to " + host + ":" + port + " failed", e);
            }
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                 DataOutputStream output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
                try {
                    return exchange.run(socket, input, output);
                } catch (javax.net.ssl.SSLException e) {
                    throw new TransferException("TLS connection to " + host + ":" + port + " failed", e);
                }
            }
        } catch (javax.net.ssl.SSLException e) {
            throw new TransferException("TLS connection to " + host + ":" + port + " failed", e);
        }
    }

    private static boolean joinStatusReader(Thread reader, SSLSocket socket, long boundedWaitMillis) {
        boolean interrupted = false;
        if (boundedWaitMillis > 0 && reader.isAlive()) {
            try {
                reader.join(boundedWaitMillis);
            } catch (InterruptedException e) {
                interrupted = true;
                closeQuietly(socket);
            }
            if (reader.isAlive()) {
                closeQuietly(socket);
            }
        }
        while (reader.isAlive()) {
            try {
                reader.join();
            } catch (InterruptedException e) {
                interrupted = true;
                closeQuietly(socket);
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        return interrupted;
    }

    private static void shutdownOutputQuietly(SSLSocket socket) {
        try { socket.shutdownOutput(); }
        catch (IOException | UnsupportedOperationException ignored) { }
    }

    private static void closeQuietly(SSLSocket socket) {
        try { socket.close(); }
        catch (IOException ignored) { }
    }

    private javax.net.ssl.SSLParameters parametersForHost(javax.net.ssl.SSLParameters parameters) {
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        return parameters;
    }

    static String[] availableTlsProtocols(String[] supported) {
        List<String> enabled = new ArrayList<>();
        for (String protocol : List.of("TLSv1.3", "TLSv1.2")) {
            if (java.util.Arrays.asList(supported).contains(protocol)) enabled.add(protocol);
        }
        if (enabled.isEmpty()) throw new IllegalStateException("No TLS 1.2+ protocol is available");
        return enabled.toArray(String[]::new);
    }

    static void writeHeader(DataOutputStream output, String operation) throws IOException {
        output.writeInt(MAGIC);
        output.writeByte(VERSION);
        output.writeByte(operationCode(operation));
    }

    private void writeRequestHeader(DataOutputStream output, String operation) throws IOException {
        if (protocolVersion == VERSION) {
            writeHeader(output, operation);
        } else {
            output.writeInt(MAGIC);
            output.writeByte(LEGACY_VERSION);
            output.writeUTF(operation);
        }
    }

    private static int operationCode(String operation) {
        return switch (operation) {
            case "UPLOAD" -> OP_UPLOAD;
            case "DOWNLOAD" -> OP_DOWNLOAD;
            case "LIST" -> OP_LIST;
            default -> throw new IllegalArgumentException("Unknown transfer operation: " + operation);
        };
    }

    private void writeRequestText(DataOutputStream output, String value, int maxBytes) throws IOException {
        if (protocolVersion == VERSION) writeV2String(output, value, maxBytes);
        else output.writeUTF(value);
    }

    private String readResponseText(DataInputStream input, int maxBytes) throws IOException {
        return protocolVersion == VERSION ? readV2String(input, maxBytes) : input.readUTF();
    }

    private void readRequestStatus(DataInputStream input) throws IOException {
        if (protocolVersion == VERSION) readV2Status(input);
        else readLegacyStatus(input);
    }

    static void writeV2String(DataOutputStream output, String value, int maxBytes) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes || bytes.length > 0xffff) {
            throw new IOException("Protocol text field exceeds its size limit");
        }
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    static String readV2String(DataInputStream input, int maxBytes) throws IOException {
        int length = input.readUnsignedShort();
        if (length > maxBytes) throw new TransferException("Protocol text field exceeds its size limit");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new TransferException("Protocol text field is not valid UTF-8", e);
        }
    }

    static void readV2Status(DataInputStream input) throws IOException {
        int status = input.readUnsignedByte();
        String message = readV2String(input, MAX_STATUS_BYTES);
        if (status != OK) throw new TransferException(message.isBlank() ? "Transfer rejected by server" : message);
    }

    private static void readLegacyStatus(DataInputStream input) throws IOException {
        int status = input.readUnsignedByte();
        String message = input.readUTF();
        if (status != OK) throw new TransferException(message.isBlank() ? "Transfer rejected by server" : message);
    }

    private static int configuredProtocolVersion() {
        int version = Integer.parseInt(System.getProperty("secure.transfer.protocol", Integer.toString(LEGACY_VERSION)));
        if (version != LEGACY_VERSION && version != VERSION) {
            throw new IllegalStateException("secure.transfer.protocol must be 1 or 2");
        }
        return version;
    }

    private static long configuredMaxFileBytes() {
        long max = Long.parseLong(System.getProperty("secure.transfer.max.bytes", Long.toString(DEFAULT_MAX_FILE_BYTES)));
        if (max < 0) throw new IllegalStateException("Maximum transfer size cannot be negative");
        return max;
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static MessageDigest newDigest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError("JDK must provide SHA-256", impossible); }
    }

    static void copyExactly(InputStream input, OutputStream output, long size) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = size;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) throw new TransferException("Connection ended before the complete file arrived");
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Simple coursework client for demonstrating upload, download and server listing. */
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !List.of("upload", "download", "list").contains(args[0])) {
            System.err.println("Usage: SecureTransferClient list | upload <local-file> [server-name] | download <server-name> <local-file>");
            System.exit(2);
        }
        SecureTransferClient client = fromSystemProperties();
        switch (args[0]) {
            case "list" -> client.listFiles().forEach(file -> System.out.printf("%s  %d bytes  SHA-256 %s%n",
                    file.fileName(), file.size(), file.sha256()));
            case "upload" -> {
                if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("Usage: upload <local-file> [server-name]");
                Path source = Path.of(args[1]);
                String remoteName = args.length == 3 ? args[2] : source.getFileName().toString();
                FileMetadata result = client.upload(source, remoteName);
                System.out.printf("Uploaded and verified %s (%d bytes), SHA-256 %s%n", result.fileName(), result.size(), result.sha256());
            }
            case "download" -> {
                if (args.length != 3) throw new IllegalArgumentException("Usage: download <server-name> <local-file>");
                FileMetadata result = client.download(args[1], Path.of(args[2]));
                System.out.printf("Downloaded and verified %s (%d bytes), SHA-256 %s%n", result.fileName(), result.size(), result.sha256());
            }
        }
    }

    @FunctionalInterface private interface IoExchange<T> {
        T run(SSLSocket socket, DataInputStream input, DataOutputStream output) throws IOException;
    }
}
