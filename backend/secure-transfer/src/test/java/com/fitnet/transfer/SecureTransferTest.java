package com.fitnet.transfer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SecureTransferTest {
    private static final String PASSWORD = "test-only-password";
    private static Path root;
    private static SecureTransferServer server;
    private static SSLContext serverContext;
    private static SSLContext clientContext;
    private static SSLContext untrustedContext;
    private static SecureTransferClient client;

    @BeforeAll
    static void startTlsServer() throws Exception {
        root = Files.createTempDirectory("fitnet-transfer-test-");
        Path keyStorePath = root.resolve("server.p12");
        Path certificatePath = root.resolve("server.cer");
        Path trustStorePath = root.resolve("truststore.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "keytool.exe" : "keytool").toString();
        run(keytool, "-genkeypair", "-alias", "fitnet-test", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "3", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", keyStorePath.toString(), "-storepass", PASSWORD,
                "-keypass", PASSWORD, "-noprompt");
        run(keytool, "-exportcert", "-alias", "fitnet-test", "-keystore", keyStorePath.toString(),
                "-storepass", PASSWORD, "-file", certificatePath.toString());
        run(keytool, "-importcert", "-alias", "fitnet-test", "-file", certificatePath.toString(),
                "-storetype", "PKCS12", "-keystore", trustStorePath.toString(), "-storepass", PASSWORD, "-noprompt");

        serverContext = TlsContexts.serverFromKeyStore(keyStorePath, PASSWORD.toCharArray());
        clientContext = TlsContexts.clientFromTrustStore(trustStorePath, PASSWORD.toCharArray());
        untrustedContext = SSLContext.getInstance("TLS");
        untrustedContext.init(null, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                throw new java.security.cert.CertificateException("Rejected by test trust manager");
            }
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }}, null);

        server = new SecureTransferServer(0, root.resolve("storage"), serverContext, 2_000_000);
        server.start();
        client = new SecureTransferClient("localhost", server.getPort(), clientContext, SecureTransferClient.VERSION);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.close();
        if (root != null) {
            try (var files = Files.walk(root)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
                });
            } catch (java.io.IOException ignored) { }
        }
    }

    @Test
    void tlsHandshakeCompletesAndServerRespondsOverEncryptedSocket() throws Exception {
        try (SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", server.getPort())) {
            socket.setSoTimeout(5_000);
            socket.setEnabledProtocols(SecureTransferClient.availableTlsProtocols(socket.getSupportedProtocols()));
            var parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);

            socket.startHandshake();

            var session = socket.getSession();
            assertTrue(session.isValid());
            assertTrue(List.of("TLSv1.3", "TLSv1.2").contains(session.getProtocol()));
            assertTrue(session.getPeerCertificates().length > 0);

            try (DataInputStream input = new DataInputStream(socket.getInputStream());
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                SecureTransferClient.writeHeader(output, "LIST");
                output.flush();

                SecureTransferClient.readV2Status(input);
                int count = input.readInt();
                assertTrue(count >= 0 && count <= 10_000);
            }
        }
    }

    @Test
    void serverContinuesToAcceptLegacyVersionOneRequests() throws Exception {
        try (SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", server.getPort())) {
            socket.setSoTimeout(5_000);
            var parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();

            try (DataInputStream input = new DataInputStream(socket.getInputStream());
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                output.writeInt(SecureTransferClient.MAGIC);
                output.writeByte(SecureTransferClient.LEGACY_VERSION);
                output.writeUTF("LIST");
                output.flush();

                assertEquals(SecureTransferClient.OK, input.readByte());
                assertEquals("File list ready", input.readUTF());
                int count = input.readInt();
                assertTrue(count >= 0 && count <= 10_000);
                for (int i = 0; i < count; i++) {
                    input.readUTF();
                    input.readLong();
                    assertEquals(64, input.readUTF().length());
                }
            }
        }
    }

    @Test
    void refusedConnectionIsReportedAsATransferFailure() throws Exception {
        int unusedPort;
        try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            unusedPort = reservation.getLocalPort();
        }

        SecureTransferClient unavailable = new SecureTransferClient("127.0.0.1", unusedPort, clientContext,
                SecureTransferClient.VERSION);
        TransferException failure = assertThrows(TransferException.class, unavailable::listFiles);
        assertTrue(failure.getMessage().contains("TLS connection"));
    }

    @Test
    void unsupportedProtocolIsClosedAndOtherInvalidRequestsDoNotStopTheServer() throws Exception {
        try (SSLSocket socket = openTrustedSocket(server.getPort());
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            output.writeInt(SecureTransferClient.MAGIC);
            output.writeByte(99);
            output.flush();
            assertEquals(-1, input.read());
        }

        try (SSLSocket socket = openTrustedSocket(server.getPort());
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            output.writeInt(SecureTransferClient.MAGIC);
            output.writeByte(SecureTransferClient.VERSION);
            output.writeByte(99);
            output.flush();
            assertEquals(SecureTransferClient.ERROR, input.readByte());
            assertTrue(SecureTransferClient.readV2String(input, SecureTransferClient.MAX_STATUS_BYTES)
                    .contains("Unknown transfer operation"));
        }

        assertNotNull(client.listFiles());
    }

    @Test
    void failedTlsHandshakeDoesNotPreventTheNextTrustedClient() throws Exception {
        SecureTransferClient untrusted = new SecureTransferClient("localhost", server.getPort(), untrustedContext,
                SecureTransferClient.VERSION);
        assertThrows(TransferException.class, untrusted::listFiles);
        assertNotNull(client.listFiles());
    }

    @Test
    void serverShutdownClosesAnActivePartialUploadAndRemovesItsTemporaryFile() throws Exception {
        Path isolatedStorage = root.resolve("shutdown-storage");
        SecureTransferServer isolatedServer = new SecureTransferServer(0, isolatedStorage, serverContext, 2_000_000);
        isolatedServer.start();
        byte[] fullContents = new byte[32_000];
        String fileName = "shutdown-partial.bin";
        try (SSLSocket socket = openTrustedSocket(isolatedServer.getPort())) {
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            SecureTransferClient.writeHeader(output, "UPLOAD");
            SecureTransferClient.writeV2String(output, fileName, SecureTransferClient.MAX_FILENAME_BYTES);
            output.writeLong(fullContents.length);
            SecureTransferClient.writeV2String(output, sha256(fullContents), SecureTransferClient.MAX_HASH_BYTES);
            output.write(fullContents, 0, 8);
            output.flush();

            assertTrue(awaitUploadPartFile(isolatedStorage, 3, TimeUnit.SECONDS),
                    "Server did not create the temporary upload before shutdown");
            isolatedServer.close();
            isolatedServer.close();
        } finally {
            isolatedServer.close();
        }

        assertFalse(Files.exists(isolatedStorage.resolve(fileName)));
        assertNoUploadPartFiles(isolatedStorage);
    }

    @Test
    void unavailableStorageReturnsGenericErrorAndServerContinuesAfterRecovery() throws Exception {
        Path isolatedStorage = root.resolve("runtime-unavailable-storage");
        Files.createDirectory(isolatedStorage);
        SecureTransferServer isolatedServer = new SecureTransferServer(0, isolatedStorage, serverContext, 2_000_000);
        isolatedServer.start();
        SecureTransferClient isolatedClient = new SecureTransferClient("localhost", isolatedServer.getPort(),
                clientContext, SecureTransferClient.VERSION);
        Path emptySource = root.resolve("storage-error-empty.bin");
        Files.write(emptySource, new byte[0]);
        try {
            Files.delete(isolatedStorage);
            TransferException failure = assertThrows(TransferException.class,
                    () -> isolatedClient.upload(emptySource, "storage-error.bin"));
            assertEquals("Transfer request failed", failure.getMessage());
            assertFalse(failure.getMessage().contains(isolatedStorage.toString()));

            Files.createDirectory(isolatedStorage);
            assertTrue(isolatedClient.listFiles().isEmpty());
        } finally {
            isolatedServer.close();
        }
    }

    @Test
    void legacyVersionOneUploadAndDownloadRemainCompatible() throws Exception {
        byte[] content = new byte[12_345];
        for (int i = 0; i < content.length; i++) content[i] = (byte) (i * 47);
        String fileName = "legacy-v1.bin";
        String expectedHash = sha256(content);

        try (SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", server.getPort())) {
            socket.setSoTimeout(5_000);
            var parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            try (DataInputStream input = new DataInputStream(socket.getInputStream());
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                output.writeInt(SecureTransferClient.MAGIC);
                output.writeByte(SecureTransferClient.LEGACY_VERSION);
                output.writeUTF("UPLOAD");
                output.writeUTF(fileName);
                output.writeLong(content.length);
                output.writeUTF(expectedHash);
                output.write(content);
                output.flush();
                assertEquals(SecureTransferClient.OK, input.readByte());
                assertEquals("Upload verified and saved", input.readUTF());
            }
        }

        try (SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", server.getPort())) {
            socket.setSoTimeout(5_000);
            var parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            try (DataInputStream input = new DataInputStream(socket.getInputStream());
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                output.writeInt(SecureTransferClient.MAGIC);
                output.writeByte(SecureTransferClient.LEGACY_VERSION);
                output.writeUTF("DOWNLOAD");
                output.writeUTF(fileName);
                output.flush();

                assertEquals(SecureTransferClient.OK, input.readByte());
                assertEquals("File ready", input.readUTF());
                assertEquals(fileName, input.readUTF());
                assertEquals(content.length, input.readLong());
                assertEquals(expectedHash, input.readUTF());
                assertArrayEquals(content, input.readNBytes(content.length));
            }
        }
    }

    @Test
    void rejectsV1UploadWithWrongHashAndLeavesNoStoredOrTemporaryFile() throws Exception {
        byte[] content = "V1 wrong hash".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (SSLSocket socket = openTrustedSocket(server.getPort());
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            output.writeInt(SecureTransferClient.MAGIC);
            output.writeByte(SecureTransferClient.LEGACY_VERSION);
            output.writeUTF("UPLOAD");
            output.writeUTF("bad-v1-hash.txt");
            output.writeLong(content.length);
            output.writeUTF("0".repeat(64));
            output.write(content);
            output.flush();

            assertEquals(SecureTransferClient.ERROR, input.readByte());
            assertTrue(input.readUTF().contains("SHA-256"));
        }
        assertFalse(Files.exists(root.resolve("storage/bad-v1-hash.txt")));
        assertNoUploadPartFiles();
    }

    @Test
    void rejectsDownloadHashMismatchAndPreservesDestinationAndCleansTemporaryFile() throws Exception {
        byte[] expectedContent = {10, 20, 30, 40, 50};
        byte[] receivedContent = {10, 20, 31, 40, 50};
        String fileName = "mismatch-download.bin";
        Path destination = root.resolve("download-existing.bin");
        byte[] previousDestination = {99, 98, 97};
        Files.write(destination, previousDestination);

        try (SSLServerSocket responder = createTlsResponder();
             var workers = Executors.newSingleThreadExecutor()) {
            var response = workers.submit(() -> {
                serveDownloadResponse(responder, fileName, expectedContent.length, sha256(expectedContent), receivedContent);
                return null;
            });
            SecureTransferClient untrustedByContent = new SecureTransferClient("127.0.0.1", responder.getLocalPort(),
                    clientContext, SecureTransferClient.VERSION);

            TransferException failure = assertThrows(TransferException.class,
                    () -> untrustedByContent.download(fileName, destination));
            assertTrue(failure.getMessage().contains("SHA-256"));
            response.get(5, TimeUnit.SECONDS);
        }

        assertArrayEquals(previousDestination, Files.readAllBytes(destination));
        assertNoDownloadTemporaryFiles(root);
    }

    @Test
    void truncatedUploadIsRejectedAndTemporaryFileIsDeleted() throws Exception {
        byte[] completeContent = "complete payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] partialContent = java.util.Arrays.copyOf(completeContent, 4);
        try (SSLSocket socket = openTrustedSocket(server.getPort());
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            SecureTransferClient.writeHeader(output, "UPLOAD");
            SecureTransferClient.writeV2String(output, "truncated-upload.bin", SecureTransferClient.MAX_FILENAME_BYTES);
            output.writeLong(completeContent.length);
            SecureTransferClient.writeV2String(output, sha256(completeContent), SecureTransferClient.MAX_HASH_BYTES);
            output.write(partialContent);
            output.flush();
            socket.shutdownOutput();

            assertEquals(SecureTransferClient.ERROR, input.readByte());
            assertTrue(SecureTransferClient.readV2String(input, SecureTransferClient.MAX_STATUS_BYTES)
                    .contains("complete file arrived"));
        }
        assertFalse(Files.exists(root.resolve("storage/truncated-upload.bin")));
        assertNoUploadPartFiles();
    }

    @Test
    void truncatedDownloadIsRejectedAndPreservesDestinationAndCleansTemporaryFile() throws Exception {
        byte[] fullContent = "expected complete download".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] partialContent = java.util.Arrays.copyOf(fullContent, 7);
        String fileName = "truncated-download.bin";
        Path destination = root.resolve("truncated-existing.bin");
        byte[] previousDestination = {7, 8, 9};
        Files.write(destination, previousDestination);

        try (SSLServerSocket responder = createTlsResponder();
             var workers = Executors.newSingleThreadExecutor()) {
            var response = workers.submit(() -> {
                serveDownloadResponse(responder, fileName, fullContent.length, sha256(fullContent), partialContent);
                return null;
            });
            SecureTransferClient testClient = new SecureTransferClient("127.0.0.1", responder.getLocalPort(),
                    clientContext, SecureTransferClient.VERSION);

            TransferException failure = assertThrows(TransferException.class,
                    () -> testClient.download(fileName, destination));
            assertTrue(failure.getMessage().contains("complete file arrived"));
            response.get(5, TimeUnit.SECONDS);
        }

        assertArrayEquals(previousDestination, Files.readAllBytes(destination));
        assertNoDownloadTemporaryFiles(root);
    }

    @Test
    void malformedHashMetadataIsRejectedForBothProtocolVersions() throws Exception {
        List<String> malformedHashes = List.of("a".repeat(63), "A".repeat(64), "g".repeat(64));
        for (int version : List.of(SecureTransferClient.LEGACY_VERSION, SecureTransferClient.VERSION)) {
            for (int index = 0; index < malformedHashes.size(); index++) {
                String fileName = "malformed-hash-v" + version + "-" + index + ".bin";
                try (SSLSocket socket = openTrustedSocket(server.getPort());
                     DataInputStream input = new DataInputStream(socket.getInputStream());
                     DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                    if (version == SecureTransferClient.LEGACY_VERSION) {
                        output.writeInt(SecureTransferClient.MAGIC);
                        output.writeByte(version);
                        output.writeUTF("UPLOAD");
                        output.writeUTF(fileName);
                    } else {
                        SecureTransferClient.writeHeader(output, "UPLOAD");
                        SecureTransferClient.writeV2String(output, fileName, SecureTransferClient.MAX_FILENAME_BYTES);
                    }
                    output.writeLong(0);
                    String malformedHash = malformedHashes.get(index);
                    if (version == SecureTransferClient.LEGACY_VERSION) output.writeUTF(malformedHash);
                    else SecureTransferClient.writeV2String(output, malformedHash, SecureTransferClient.MAX_HASH_BYTES);
                    output.flush();

                    assertEquals(SecureTransferClient.ERROR, input.readByte());
                    String message = version == SecureTransferClient.LEGACY_VERSION
                            ? input.readUTF()
                            : SecureTransferClient.readV2String(input, SecureTransferClient.MAX_STATUS_BYTES);
                    assertTrue(message.contains("SHA-256"));
                }
                assertFalse(Files.exists(root.resolve("storage").resolve(fileName)));
            }
        }
        assertNoUploadPartFiles();
    }

    @Test
    void changedSourceBytesPairedWithTheOriginalHashAreRejectedDeterministically() throws Exception {
        Path source = root.resolve("changed-source.bin");
        byte[] originalContents = "hash before source changes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] modifiedContents = "hash before source change!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(originalContents.length, modifiedContents.length);
        Files.write(source, originalContents);

        try (SSLServerSocket responder = createTlsResponder();
             var workers = Executors.newFixedThreadPool(2)) {
            CountDownLatch accepted = new CountDownLatch(1);
            CountDownLatch continueHandshake = new CountDownLatch(1);
            var proxy = workers.submit(() -> {
                forwardChangedUploadToRealServer(responder, server.getPort(), accepted, continueHandshake);
                return null;
            });
            SecureTransferClient testClient = new SecureTransferClient("127.0.0.1", responder.getLocalPort(),
                    clientContext, SecureTransferClient.VERSION);
            var upload = workers.submit(() -> testClient.upload(source, "changed-source.bin"));
            try {
                assertTrue(accepted.await(5, TimeUnit.SECONDS), "Client did not connect after hashing the source");
                Files.write(source, modifiedContents);
            } finally {
                continueHandshake.countDown();
            }

            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> upload.get(10, TimeUnit.SECONDS));
            assertInstanceOf(TransferException.class, failure.getCause());
            proxy.get(10, TimeUnit.SECONDS);
        }
        assertFalse(Files.exists(root.resolve("storage/changed-source.bin")));
        assertNoUploadPartFiles();
    }

    @Test
    void uploadsListsAndDownloadsEmptyTextAndBinaryFilesWithVerifiedHashes() throws Exception {
        byte[] binary = new byte[180_000];
        for (int i = 0; i < binary.length; i++) binary[i] = (byte) (i * 31);
        byte[][] contents = {new byte[0],
                "FITNet secure transfer — Sinhala සිංහල".getBytes(java.nio.charset.StandardCharsets.UTF_8), binary};
        String[] names = {"empty.dat", "notes.txt", "binary.bin"};
        for (int i = 0; i < contents.length; i++) {
            Path source = root.resolve("source-" + names[i]);
            Path destination = root.resolve("downloads").resolve(names[i]);
            Files.write(source, contents[i]);
            FileMetadata uploaded = client.upload(source, names[i]);
            FileMetadata downloaded = client.download(names[i], destination);
            assertEquals(uploaded.sha256(), downloaded.sha256());
            assertArrayEquals(contents[i], Files.readAllBytes(destination));
        }
        List<FileMetadata> listing = client.listFiles();
        assertTrue(listing.stream().map(FileMetadata::fileName).toList().containsAll(List.of("binary.bin", "empty.dat", "notes.txt")));
        for (FileMetadata file : listing) {
            assertEquals(sha256(root.resolve("storage").resolve(file.fileName())), file.sha256());
        }
    }

    @Test
    void rejectsTraversalAndMissingDownloadsWithoutCreatingOutput() throws Exception {
        Path source = root.resolve("traversal-source.txt");
        Files.writeString(source, "safe file");
        assertThrows(TransferException.class, () -> client.upload(source, "../outside.txt"));
        Path destination = root.resolve("missing-download.txt");
        TransferException missing = assertThrows(TransferException.class, () -> client.download("missing.txt", destination));
        assertEquals("File not found", missing.getMessage());
        assertFalse(Files.exists(destination));
    }

    @Test
    void serverRejectsUnsafeNamesAndOversizedUploadsBeforeCreatingFiles() throws Exception {
        try (SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", server.getPort())) {
            socket.setSoTimeout(5_000);
            var parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            try (DataInputStream input = new DataInputStream(socket.getInputStream());
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                SecureTransferClient.writeHeader(output, "UPLOAD");
                SecureTransferClient.writeV2String(output, "../escape.txt", SecureTransferClient.MAX_FILENAME_BYTES);
                output.flush();
                assertEquals(SecureTransferClient.ERROR, input.readByte());
                assertTrue(SecureTransferClient.readV2String(input, SecureTransferClient.MAX_STATUS_BYTES)
                        .contains("File name"));
            }
        }
        assertFalse(Files.exists(root.resolve("escape.txt")));

        try (SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", server.getPort())) {
            socket.setSoTimeout(5_000);
            var parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            try (DataInputStream input = new DataInputStream(socket.getInputStream());
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                SecureTransferClient.writeHeader(output, "UPLOAD");
                SecureTransferClient.writeV2String(output, "too-large.bin", SecureTransferClient.MAX_FILENAME_BYTES);
                output.writeLong(2_000_001L);
                SecureTransferClient.writeV2String(output, "0".repeat(64), SecureTransferClient.MAX_HASH_BYTES);
                output.flush();
                assertEquals(SecureTransferClient.ERROR, input.readByte());
                assertTrue(SecureTransferClient.readV2String(input, SecureTransferClient.MAX_STATUS_BYTES)
                        .contains("size limit"));
            }
        }
        assertFalse(Files.exists(root.resolve("storage/too-large.bin")));
        try (var files = Files.list(root.resolve("storage"))) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    void rejectsUploadWithWrongHashAndDoesNotCommitPartialFile() throws Exception {
        byte[] content = "corrupt me".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", server.getPort())) {
            socket.setSoTimeout(5000);
            var parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            SecureTransferClient.writeHeader(output, "UPLOAD");
            SecureTransferClient.writeV2String(output, "bad-hash.txt", SecureTransferClient.MAX_FILENAME_BYTES);
            output.writeLong(content.length);
            SecureTransferClient.writeV2String(output, "0".repeat(64), SecureTransferClient.MAX_HASH_BYTES);
            output.write(content);
            output.flush();
            assertEquals(SecureTransferClient.ERROR, input.readByte());
            assertTrue(SecureTransferClient.readV2String(input, SecureTransferClient.MAX_STATUS_BYTES).contains("SHA-256"));
        }
        assertFalse(Files.exists(root.resolve("storage/bad-hash.txt")));
        try (var files = Files.list(root.resolve("storage"))) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    void rejectsConnectionsWhenTheClientDoesNotTrustTheServerCertificate() {
        SecureTransferClient untrusted = new SecureTransferClient("localhost", server.getPort(), untrustedContext);
        assertThrows(TransferException.class, untrusted::listFiles);
    }

    @Test
    void handlesConcurrentFileTransfers() throws Exception {
        try (var pool = Executors.newFixedThreadPool(4)) {
            java.util.List<java.util.concurrent.Future<?>> tasks = new java.util.ArrayList<>();
            for (int index = 0; index < 8; index++) {
                int transfer = index;
                tasks.add(pool.submit(() -> {
                byte[] content = ("parallel-" + transfer).repeat(1000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Path source = root.resolve("parallel-source-" + transfer);
                Path download = root.resolve("parallel-download-" + transfer);
                Files.write(source, content);
                client.upload(source, "parallel-" + transfer + ".dat");
                client.download("parallel-" + transfer + ".dat", download);
                assertArrayEquals(content, Files.readAllBytes(download));
                return null;
                }));
            }
            for (var task : tasks) task.get();
        }
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (var input = process.getInputStream()) { output = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException("keytool failed: " + output);
    }

    private static SSLSocket openTrustedSocket(int port) throws Exception {
        SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", port);
        socket.setSoTimeout(5_000);
        var parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parameters);
        socket.startHandshake();
        return socket;
    }

    private static SSLServerSocket createTlsResponder() throws Exception {
        SSLServerSocket responder = (SSLServerSocket) serverContext.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        responder.setSoTimeout(5_000);
        responder.setEnabledProtocols(SecureTransferClient.availableTlsProtocols(responder.getSupportedProtocols()));
        return responder;
    }

    private static void serveDownloadResponse(SSLServerSocket responder, String fileName, long declaredSize,
                                              String expectedHash, byte[] transmittedContents) throws Exception {
        try (SSLSocket socket = (SSLSocket) responder.accept()) {
            socket.setSoTimeout(5_000);
            socket.setEnabledProtocols(SecureTransferClient.availableTlsProtocols(socket.getSupportedProtocols()));
            socket.startHandshake();
            try (DataInputStream input = new DataInputStream(socket.getInputStream());
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                assertEquals(SecureTransferClient.MAGIC, input.readInt());
                assertEquals(SecureTransferClient.VERSION, input.readUnsignedByte());
                assertEquals(SecureTransferClient.OP_DOWNLOAD, input.readUnsignedByte());
                assertEquals(fileName, SecureTransferClient.readV2String(input, SecureTransferClient.MAX_FILENAME_BYTES));

                output.writeByte(SecureTransferClient.OK);
                SecureTransferClient.writeV2String(output, "File ready", SecureTransferClient.MAX_STATUS_BYTES);
                SecureTransferClient.writeV2String(output, fileName, SecureTransferClient.MAX_FILENAME_BYTES);
                output.writeLong(declaredSize);
                SecureTransferClient.writeV2String(output, expectedHash, SecureTransferClient.MAX_HASH_BYTES);
                output.write(transmittedContents);
                output.flush();
            }
        }
    }

    private static void forwardChangedUploadToRealServer(SSLServerSocket responder, int serverPort,
                                                          CountDownLatch accepted, CountDownLatch continueHandshake)
            throws Exception {
        try (SSLSocket downstream = (SSLSocket) responder.accept()) {
            downstream.setSoTimeout(5_000);
            accepted.countDown();
            if (!continueHandshake.await(5, TimeUnit.SECONDS)) throw new AssertionError("Handshake gate timed out");
            downstream.setEnabledProtocols(SecureTransferClient.availableTlsProtocols(downstream.getSupportedProtocols()));
            downstream.startHandshake();

            try (DataInputStream input = new DataInputStream(downstream.getInputStream());
                 DataOutputStream output = new DataOutputStream(downstream.getOutputStream())) {
                assertEquals(SecureTransferClient.MAGIC, input.readInt());
                assertEquals(SecureTransferClient.VERSION, input.readUnsignedByte());
                assertEquals(SecureTransferClient.OP_UPLOAD, input.readUnsignedByte());
                String fileName = SecureTransferClient.readV2String(input, SecureTransferClient.MAX_FILENAME_BYTES);
                long size = input.readLong();
                String expectedHash = SecureTransferClient.readV2String(input, SecureTransferClient.MAX_HASH_BYTES);
                assertTrue(size >= 0 && size <= SecureTransferClient.DEFAULT_MAX_FILE_BYTES);
                byte[] contents = input.readNBytes(Math.toIntExact(size));
                assertEquals(size, contents.length);

                try (SSLSocket upstream = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", serverPort)) {
                    upstream.setSoTimeout(5_000);
                    var parameters = upstream.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    upstream.setSSLParameters(parameters);
                    upstream.setEnabledProtocols(SecureTransferClient.availableTlsProtocols(upstream.getSupportedProtocols()));
                    upstream.startHandshake();
                    try (DataInputStream serverInput = new DataInputStream(upstream.getInputStream());
                         DataOutputStream serverOutput = new DataOutputStream(upstream.getOutputStream())) {
                        SecureTransferClient.writeHeader(serverOutput, "UPLOAD");
                        SecureTransferClient.writeV2String(serverOutput, fileName, SecureTransferClient.MAX_FILENAME_BYTES);
                        serverOutput.writeLong(size);
                        SecureTransferClient.writeV2String(serverOutput, expectedHash, SecureTransferClient.MAX_HASH_BYTES);
                        serverOutput.write(contents);
                        serverOutput.flush();

                        int status = serverInput.readUnsignedByte();
                        String message = SecureTransferClient.readV2String(serverInput, SecureTransferClient.MAX_STATUS_BYTES);
                        assertEquals(SecureTransferClient.ERROR, status);
                        assertTrue(message.contains("SHA-256"));
                        output.writeByte(status);
                        SecureTransferClient.writeV2String(output, message, SecureTransferClient.MAX_STATUS_BYTES);
                        output.flush();
                    }
                }
            }
        }
    }

    private static void assertNoUploadPartFiles() throws Exception {
        assertNoUploadPartFiles(root.resolve("storage"));
    }

    private static void assertNoUploadPartFiles(Path storageDirectory) throws Exception {
        try (var files = Files.list(storageDirectory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".fitnet-upload-")
                    || path.getFileName().toString().endsWith(".part")));
        }
    }

    private static boolean awaitUploadPartFile(Path storageDirectory, long timeout, TimeUnit unit) throws Exception {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        do {
            try (var files = Files.list(storageDirectory)) {
                if (files.anyMatch(path -> path.getFileName().toString().startsWith(".fitnet-upload-"))) return true;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        return false;
    }

    private static void assertNoDownloadTemporaryFiles(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".fitnet-download-")
                    || path.getFileName().toString().endsWith(".part")));
        }
    }

    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase().contains("win"); }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
    }
}
