package com.example.hls.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Configuration
public class SslConfiguration {
    private final String keystoreFile;
    private final String password;

    public SslConfiguration(@Value("${keystore.jks}") String keystoreFile,
                            @Value("${keystore.password}") String password) {
        this.keystoreFile = keystoreFile;
        this.password = password;
    }

    @Bean
    @Qualifier("sslContext")
    public SSLContext sslContext() throws Exception {
        char[] passwordChars = password.toCharArray();
        KeyStore keystore = keyStore(keystoreFile, passwordChars);
        return SSLContextBuilder.create()
                .loadKeyMaterial(keystore, passwordChars)
                .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                .build();
    }

    @Bean
    @Qualifier("nettySSLContext")
    public SslContext nettySSLContext() throws Exception {
        char[] passwordChars = password.toCharArray();
        KeyStore keystore = keyStore(keystoreFile, passwordChars);
        String alias = keystore.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) keystore.getKey(alias, passwordChars);
        Certificate[] certificateChain = keystore.getCertificateChain(alias);
        if (privateKey == null || certificateChain == null) {
            throw new IllegalArgumentException("Could not retrieve key or certificate chain from keystore.");
        }
        X509Certificate[] x509Certificates = new X509Certificate[certificateChain.length];
        for (int i = 0; i < certificateChain.length; i++) {
            x509Certificates[i] = (X509Certificate) certificateChain[i];
        }
        return SslContextBuilder.forClient()
                .keyManager(privateKey, password, x509Certificates)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
    }

    private KeyStore keyStore(String file, char[] password)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        File key = ResourceUtils.getFile(file);
        try (InputStream in = new java.io.FileInputStream(key)) {
            keyStore.load(in, password);
        }
        return keyStore;
    }
}
