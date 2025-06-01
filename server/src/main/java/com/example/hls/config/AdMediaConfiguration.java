package com.example.hls.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;


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

    @Value("${adprovider.url:}")
    private String adProviderUrl;

    @Value("${adprovider.requests.timeout:5000}")
    private int adProviderTimeout;


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

    public String getAdProviderUrl() {
        return adProviderUrl;
    }

    public int getAdProviderTimeout() {
        return adProviderTimeout;
    }
}
