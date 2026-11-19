package com.fitnet.transfer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

/** Loads explicit key and trust stores; there is deliberately no trust-all fallback. */
final class TlsContexts {
    private TlsContexts() { }

    static SSLContext serverFromSystemProperties() throws Exception {
        Path keyStorePath = configuredPath("secure.transfer.keystore", "FITNET_TLS_KEYSTORE",
                "secure-transfer/certs/server.p12");
        char[] password = requiredPassword("FITNET_TLS_KEYSTORE_PASSWORD");
        return serverFromKeyStore(keyStorePath, password);
    }

    static SSLContext serverFromKeyStore(Path keyStorePath, char[] password) throws Exception {
        KeyStore keyStore = load(keyStorePath, password);
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        return context;
    }

    static SSLContext clientFromSystemProperties() throws Exception {
        Path trustStorePath = configuredPath("secure.transfer.truststore", "FITNET_TLS_TRUSTSTORE",
                "secure-transfer/certs/truststore.p12");
        char[] password = requiredPassword("FITNET_TLS_TRUSTSTORE_PASSWORD");
        return clientFromTrustStore(trustStorePath, password);
    }

    static SSLContext clientFromTrustStore(Path trustStorePath, char[] password) throws Exception {
        KeyStore trustStore = load(trustStorePath, password);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trust.getTrustManagers(), null);
        return context;
    }

    private static Path configuredPath(String property, String environmentVariable, String defaultPath) {
        String configured = System.getProperty(property);
        if (configured == null || configured.isBlank()) configured = System.getenv(environmentVariable);
        if (configured == null || configured.isBlank()) configured = defaultPath;
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private static KeyStore load(Path path, char[] password) throws Exception {
        if (!Files.isRegularFile(path)) throw new IllegalStateException("TLS store not found: " + path.toAbsolutePath());
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(path)) { store.load(input, password); }
        return store;
    }

    private static char[] requiredPassword(String variable) {
        String password = System.getenv(variable);
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Set the " + variable + " environment variable before starting FITNet");
        }
        return password.toCharArray();
    }
}
