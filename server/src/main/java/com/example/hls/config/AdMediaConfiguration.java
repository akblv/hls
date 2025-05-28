package com.example.hls.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AdMediaConfiguration {

    @Value("${admedia.url:}")
    private String adMediaServiceUrl;

    @Value("${admedia.requests.timeout:5000}")
    private int requestTimeout;

    @Value("${admedia.codec.cache.expiration:600000}")
    private int codecSettingInfoCacheExpiration;

    @Value("${admedia.catalog.cache.expiration:600000}")
    private int adCatalogCacheExpiration;


    public String getAdMediaServiceUrl() {
        return adMediaServiceUrl;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public int getCodecSettingInfoCacheExpiration() {
        return codecSettingInfoCacheExpiration;
    }

    public int getAdCatalogCacheExpiration() {
        return adCatalogCacheExpiration;
    }
}
