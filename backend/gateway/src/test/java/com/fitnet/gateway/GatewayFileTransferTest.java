package com.fitnet.gateway;

import com.fitnet.transfer.SecureTransferClient;
import com.fitnet.transfer.SecureTransferServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GatewayFileTransferTest {
    private static final String PASSWORD = "gateway-test-password";
    private static Path temp;
    private static SecureTransferServer tlsServer;
    private static GatewayServer gateway;
    private static HttpClient http;
    private static SSLContext clientContext;
    private static SSLContext serverContext;

    @BeforeAll
    static void startServices() throws Exception {
        temp = Files.createTempDirectory("fitnet-gateway-test-");
        Path keys = temp.resolve("server.p12");
        Path cert = temp.resolve("server.cer");
        Path trust = temp.resolve("trust.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool").toString();
        run(keytool, "-genkeypair", "-alias", "gateway-test", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "3", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", keys.toString(), "-storepass", PASSWORD,
                "-keypass", PASSWORD, "-noprompt");
        run(keytool, "-exportcert", "-alias", "gateway-test", "-keystore", keys.toString(), "-storepass", PASSWORD,
                "-file", cert.toString());
        run(keytool, "-importcert", "-alias", "gateway-test", "-file", cert.toString(), "-storetype", "PKCS12",
                "-keystore", trust.toString(), "-storepass", PASSWORD, "-noprompt");

        KeyStore keyStore = load(keys);
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, PASSWORD.toCharArray());
        serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagers.getKeyManagers(), null, null);
        KeyStore trustStore = load(trust);
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, trustManagers.getTrustManagers(), null);

        tlsServer = new SecureTransferServer(0, temp.resolve("stored"), serverContext);
        tlsServer.start();
        SecureTransferClient transferClient = clientForProtocol("localhost", tlsServer.getPort(), clientContext, 1);
        gateway = new GatewayServer(0, "http://localhost:3000", transferClient);
        gateway.start();
        http = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServices() {
        if (gateway != null) gateway.close();
        if (tlsServer != null) tlsServer.close();
        if (temp != null) {
            try (var paths = Files.walk(temp)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
                });
            } catch (java.io.IOException ignored) { }
        }
    }

    @Test
    void browserCanUploadListAndDownloadThroughGatewayAndTlsService() throws Exception {
        byte[] original = new byte[180_001];
        for (int i = 0; i < original.length; i++) original[i] = (byte) (i * 19);
        URI endpoint = URI.create("http://127.0.0.1:" + gateway.getPort() + "/api/files");

        HttpResponse<String> upload = http.send(HttpRequest.newBuilder(endpoint)
                .header("Origin", "http://localhost:3000")
                .header("X-Filename", "browser-upload.bin")
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(original)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, upload.statusCode(), upload.body());
        String expectedHash = sha256(original);
        String expectedMetadata = "{\"fileName\":\"browser-upload.bin\",\"size\":" + original.length +
                ",\"sha256\":\"" + expectedHash + "\"}";
        assertEquals(expectedMetadata, upload.body());
        assertNotNull(upload.headers().firstValue("Access-Control-Allow-Origin").orElse(null));

        HttpResponse<String> listing = http.send(HttpRequest.newBuilder(endpoint)
                .header("Origin", "http://localhost:3000").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listing.statusCode());
        assertTrue(listing.body().contains(expectedMetadata));

        HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/browser-upload.bin"))
                .header("Origin", "http://localhost:3000").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, download.statusCode());
        assertArrayEquals(original, download.body());
        assertTrue(download.headers().firstValue("Content-Disposition").orElse("").contains("browser-upload.bin"));
        assertEquals(expectedHash, download.headers().firstValue("X-File-SHA256").orElseThrow());
    }

    @Test
    void rejectsInvalidUploadFileNamesBeforeTransfer() throws Exception {
        URI endpoint = URI.create("http://127.0.0.1:" + gateway.getPort() + "/api/files");
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(endpoint)
                .header("X-Filename", "../invalid.bin")
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray("must not be stored".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains("File name must be"));
        assertFalse(Files.exists(temp.resolve("stored").resolve("invalid.bin")));
    }

    @Test
    void mapsDownstreamFileSizeRejectionsToPayloadTooLarge() throws Exception {
        Path limitedStorage = temp.resolve("limited-storage");
        try (SecureTransferServer limitedServer = new SecureTransferServer(0, limitedStorage, serverContext, 8)) {
            limitedServer.start();
            try (GatewayServer limitedGateway = new GatewayServer(0, "http://localhost:3000",
                    clientForProtocol("localhost", limitedServer.getPort(), clientContext, 1))) {
                limitedGateway.start();
                URI endpoint = URI.create("http://127.0.0.1:" + limitedGateway.getPort() + "/api/files");
                HttpResponse<String> response = http.send(HttpRequest.newBuilder(endpoint)
                        .header("X-Filename", "too-large.bin")
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[9]))
                        .build(), HttpResponse.BodyHandlers.ofString());

                assertEquals(413, response.statusCode(), response.body());
                assertFalse(Files.exists(limitedStorage.resolve("too-large.bin")));
            }
        }
    }

    @Test
    void rejectsOneMiBPlusOneByteUploadWithPayloadTooLargeAndRecoversForV1() throws Exception {
        assertOneMiBPlusOneByteRejectionAndRecovery(1);
    }

    @Test
    void rejectsOneMiBPlusOneByteUploadWithPayloadTooLargeAndRecoversForV2() throws Exception {
        assertOneMiBPlusOneByteRejectionAndRecovery(2);
    }

    @Test
    void reportsAbruptDownstreamDisconnectDuringUploadAsBadGatewayNotPayloadTooLarge() throws Exception {
        byte[] payload = new byte[1_048_577];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i * 13);
        try (SSLServerSocket abruptServer = (SSLServerSocket) serverContext.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             var pool = Executors.newSingleThreadExecutor();
             GatewayServer abruptGateway = new GatewayServer(0, "http://localhost:3000",
                     clientForProtocol("localhost", abruptServer.getLocalPort(), clientContext, 2))) {
            abruptServer.setSoTimeout(5_000);
            abruptGateway.start();
            var serverTask = pool.submit(() -> {
                try (SSLSocket socket = (SSLSocket) abruptServer.accept()) {
                    socket.setSoTimeout(5_000);
                    socket.startHandshake();
                    // Read a few bytes then abruptly close without sending any protocol status frame.
                    socket.getInputStream().readNBytes(32);
                }
                return null;
            });

            URI endpoint = URI.create("http://127.0.0.1:" + abruptGateway.getPort() + "/api/files");
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(endpoint)
                    .header("X-Filename", "abrupt-disconnect.bin")
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build(), HttpResponse.BodyHandlers.ofString());

            serverTask.get(5, TimeUnit.SECONDS);
            assertEquals(502, response.statusCode(), response.body());
            assertFalse(response.body().contains("size limit"), response.body());
        }
    }

    private void assertOneMiBPlusOneByteRejectionAndRecovery(int protocolVersion) throws Exception {
        long oneMiB = 1_048_576L;
        byte[] oversized = new byte[1_048_577];
        for (int i = 0; i < oversized.length; i++) oversized[i] = (byte) (i * 29);
        byte[] valid = new byte[65_537];
        for (int i = 0; i < valid.length; i++) valid[i] = (byte) (i * 37);

        Path limitedStorage = temp.resolve("limited-1mib-v" + protocolVersion);
        String oversizedName = "oversize-v" + protocolVersion + ".bin";
        String validName = "valid-after-reject-v" + protocolVersion + ".bin";
        String validHash = sha256(valid);

        try (SecureTransferServer limitedServer = new SecureTransferServer(0, limitedStorage, serverContext, oneMiB)) {
            limitedServer.start();
            try (GatewayServer limitedGateway = new GatewayServer(0, "http://localhost:3000",
                    clientForProtocol("localhost", limitedServer.getPort(), clientContext, protocolVersion))) {
                limitedGateway.start();
                URI endpoint = URI.create("http://127.0.0.1:" + limitedGateway.getPort() + "/api/files");

                HttpResponse<String> rejected = http.send(HttpRequest.newBuilder(endpoint)
                        .header("Origin", "http://localhost:3000")
                        .header("X-Filename", oversizedName)
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(oversized))
                        .build(), HttpResponse.BodyHandlers.ofString());

                assertEquals(413, rejected.statusCode(), rejected.body());
                assertEquals("{\"error\":\"File exceeds the configured size limit\"}", rejected.body());
                assertFalse(Files.exists(limitedStorage.resolve(oversizedName)));
                try (var files = Files.list(limitedStorage)) {
                    assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".part")));
                }

                HttpResponse<String> uploaded = http.send(HttpRequest.newBuilder(endpoint)
                        .header("Origin", "http://localhost:3000")
                        .header("X-Filename", validName)
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(valid))
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(201, uploaded.statusCode(), uploaded.body());
                assertEquals("{\"fileName\":\"" + validName + "\",\"size\":" + valid.length +
                        ",\"sha256\":\"" + validHash + "\"}", uploaded.body());

                HttpResponse<byte[]> downloaded = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/" + validName))
                        .header("Origin", "http://localhost:3000")
                        .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, downloaded.statusCode());
                assertArrayEquals(valid, downloaded.body());
                assertEquals(validHash, downloaded.headers().firstValue("X-File-SHA256").orElseThrow());
                try (var files = Files.list(limitedStorage)) {
                    assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".part")));
                }
            }
        }
    }

    @Test
    void browserCanUploadListAndDownloadThroughGatewayUsingProtocolV2() throws Exception {
        byte[] original = new byte[32_001];
        for (int i = 0; i < original.length; i++) original[i] = (byte) (i * 7);
        String fileName = "browser-upload-v2.bin";
        String expectedHash = sha256(original);
        String expectedMetadata = "{\"fileName\":\"" + fileName + "\",\"size\":" + original.length +
                ",\"sha256\":\"" + expectedHash + "\"}";
        try (GatewayServer v2Gateway = new GatewayServer(0, "http://localhost:3000",
                clientForProtocol("localhost", tlsServer.getPort(), clientContext, 2))) {
            v2Gateway.start();
            URI endpoint = URI.create("http://127.0.0.1:" + v2Gateway.getPort() + "/api/files");

            HttpResponse<String> upload = http.send(HttpRequest.newBuilder(endpoint)
                    .header("X-Filename", fileName)
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(original)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(201, upload.statusCode(), upload.body());
            assertEquals(expectedMetadata, upload.body());

            HttpResponse<String> listing = http.send(HttpRequest.newBuilder(endpoint).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, listing.statusCode());
            assertTrue(listing.body().contains(expectedMetadata));

            HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/" + fileName))
                    .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, download.statusCode());
            assertArrayEquals(original, download.body());
            assertEquals(expectedHash, download.headers().firstValue("X-File-SHA256").orElseThrow());
        }
    }

    @Test
    void reportsMissingFilesAsNotFoundAndBlocksUnapprovedOriginsFromBrowserReads() throws Exception {
        URI missing = URI.create("http://127.0.0.1:" + gateway.getPort() + "/api/files/does-not-exist.txt");
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(missing).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
        assertFalse(response.headers().firstValue("Access-Control-Allow-Origin").isPresent());
    }

    @Test
    void allowsTheBrowserUploadPreflightForTheConfiguredFrontendOrigin() throws Exception {
        URI endpoint = URI.create("http://127.0.0.1:" + gateway.getPort() + "/api/files");
        HttpResponse<Void> response = http.send(HttpRequest.newBuilder(endpoint)
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-filename")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(204, response.statusCode());
        assertEquals("http://localhost:3000", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().toLowerCase()
                .contains("x-filename"));
    }

    private static KeyStore load(Path path) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(path)) { store.load(input, PASSWORD.toCharArray()); }
        return store;
    }

    private static SecureTransferClient clientForProtocol(String host, int port, SSLContext context, int version) {
        String previous = System.getProperty("secure.transfer.protocol");
        try {
            System.setProperty("secure.transfer.protocol", Integer.toString(version));
            return new SecureTransferClient(host, port, context);
        } finally {
            if (previous == null) System.clearProperty("secure.transfer.protocol");
            else System.setProperty("secure.transfer.protocol", previous);
        }
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (var input = process.getInputStream()) { output = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        if (process.waitFor() != 0) throw new IllegalStateException("keytool failed: " + output);
    }
}
