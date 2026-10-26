package com.gamelist.config;

import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(OkHttpClientConfig.class);

    @Bean
    public OkHttpClient sharedOkHttpClient() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, new SecureRandom());

            ConnectionSpec modernSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                    .allEnabledCipherSuites()
                    .build();

            ConnectionSpec compatibleSpec = new ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
                    .allEnabledCipherSuites()
                    .build();

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    })
                    .hostnameVerifier((hostname, session) -> true)
                    .connectionSpecs(Arrays.asList(modernSpec, compatibleSpec, ConnectionSpec.CLEARTEXT))
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true)
                    .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
                    .build();
        } catch (Exception e) {
            logger.warn("Failed to create custom SSL context, using default: {}", e.getMessage());
            return new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
                    .build();
        }
    }
}
