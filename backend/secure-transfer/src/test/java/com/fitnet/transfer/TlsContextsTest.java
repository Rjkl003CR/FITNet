package com.fitnet.transfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlsContextsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsPkcs12ServerKeystoreAndClientTruststore() throws Exception {
        String password = UUID.randomUUID().toString();
        Path keyStorePath = temporaryDirectory.resolve("server.p12");
        Path certificatePath = temporaryDirectory.resolve("server.cer");
        Path trustStorePath = temporaryDirectory.resolve("truststore.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool").toString();

        runKeytool(password, "FITNET_TEST_KEY_PASSWORD", keytool,
                "-genkeypair", "-alias", "fitnet-server", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "30", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", keyStorePath.toString(),
                "-storepass:env", "FITNET_TEST_KEY_PASSWORD", "-keypass:env", "FITNET_TEST_KEY_PASSWORD", "-noprompt");
        runKeytool(password, "FITNET_TEST_KEY_PASSWORD", keytool,
                "-exportcert", "-alias", "fitnet-server", "-keystore", keyStorePath.toString(),
                "-storepass:env", "FITNET_TEST_KEY_PASSWORD", "-file", certificatePath.toString());
        runKeytool(password, "FITNET_TEST_TRUST_PASSWORD", keytool,
                "-importcert", "-alias", "fitnet-server", "-file", certificatePath.toString(),
                "-storetype", "PKCS12", "-keystore", trustStorePath.toString(),
                "-storepass:env", "FITNET_TEST_TRUST_PASSWORD", "-noprompt");

        SSLContext serverContext = TlsContexts.serverFromKeyStore(keyStorePath, password.toCharArray());
        SSLContext clientContext = TlsContexts.clientFromTrustStore(trustStorePath, password.toCharArray());
        assertNotNull(serverContext.getServerSocketFactory());
        assertNotNull(clientContext.getSocketFactory());

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keyStorePath)) { keyStore.load(input, password.toCharArray()); }
        assertTrue(keyStore.isKeyEntry("fitnet-server"));

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(trustStorePath)) { trustStore.load(input, password.toCharArray()); }
        assertNotNull(trustStore.getCertificate("fitnet-server"));
    }

    private static void runKeytool(String password, String passwordVariable, String executable, String... arguments)
            throws Exception {
        ProcessBuilder builder = new ProcessBuilder(executable);
        builder.command().addAll(java.util.List.of(arguments));
        builder.redirectErrorStream(true);
        builder.environment().put(passwordVariable, password);
        Process process = builder.start();
        String output;
        try (var stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new IllegalStateException("keytool failed: " + output);
    }
}
