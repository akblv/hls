package com.example.hls.config;

import io.netty.handler.ssl.SslContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfiguration {

    @Bean
    public WebClient webClient(SslContext nettySSLContext) {
        HttpClient httpConnector = HttpClient.create().secure(t -> t.sslContext(nettySSLContext));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpConnector))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                        .build())
                .build();
    }
}
