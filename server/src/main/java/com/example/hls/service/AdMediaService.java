package com.example.hls.service;

import com.example.hls.config.AdMediaConfiguration;
import com.example.hls.model.CacheEntry;
import com.example.hls.model.CodecInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.example.hls.model.ad.*;
import com.zenomedia.common.model.ads.AdCatalogItem;
import com.zenomedia.common.model.transcode.CodecSettingInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.hls.util.JsonConverter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synchronous version of the Scala AdMediaService using WebClient.
 */
@Service
public class AdMediaService {

    private static final Logger logger = LogManager.getLogger(AdMediaService.class);

    private final WebClient webClient;
    private final AdMediaConfiguration config;
    private final JsonConverter converter;

    private final Map<Integer, CacheEntry<CodecSettingInfo>> codecSettingInfoCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AdCatalogItem>> adCatalogCacheByUrl = new ConcurrentHashMap<>();

    @Autowired
    public AdMediaService(WebClient webClient, AdMediaConfiguration config, JsonConverter converter) {
        this.webClient = webClient;
        this.config = config;
        this.converter = converter;
    }

    /**
     * Periodic cleanup of expired cache entries.
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanUpCaches() {
        logger.debug("Ad media cache cleanup triggered");
        long now = System.currentTimeMillis();
        codecSettingInfoCache.entrySet().removeIf(e -> now - e.getValue().timestamp() > config.getCodecSettingInfoCacheExpiration());
        adCatalogCacheByUrl.entrySet().removeIf(e -> now - e.getValue().timestamp() > config.getAdCatalogCacheExpiration());
    }

    public void warmup() {
        // Placeholder for warmup logic if needed
    }

    public List<AdCatalogItem> retrieveAdMediaPaginated(String mount, String from, String size, String page) {
        String url = UriComponentsBuilder.fromUriString(config.getAdMediaServiceUrl())
                .path("/media/items")
                .queryParam("stream", mount)
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParam("from", from)
                .toUriString();
        try {
            ResponseEntity<String> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .toEntity(String.class)
                    .block();
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return converter.deserializeJson(response.getBody(), new TypeReference<List<AdCatalogItem>>() {
                });
            }
        } catch (Exception e) {
            logger.warn("Failed retrieving ad media page {}", page, e);
        }
        return Collections.emptyList();
    }

    public AdCatalogItem retrieveAdMediaForUrl(String url) {
        CacheEntry<AdCatalogItem> cached = adCatalogCacheByUrl.get(url);
        return cached != null ? cached.value() : retrieveAdMediaForUrlImpl(url);
    }

    public AdCatalogItem retrieveAdMediaForUrlImpl(String url) {
        String requestUrl = UriComponentsBuilder.fromUriString(config.getAdMediaServiceUrl())
                .path("/media/items/find")
                .queryParam("url", url)
                .toUriString();
        try {
            ResponseEntity<String> response = webClient.get()
                    .uri(requestUrl)
                    .retrieve()
                    .toEntity(String.class)
                    .block();
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return cacheAdCatalogItem(converter.deserializeJson(response.getBody(), AdCatalogItem.class));
            }
            throw new RuntimeException("Failed url: " + requestUrl + " with http status: " + (response != null ? response.getStatusCode() : "no response"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public AdCatalogItem cacheAdCatalogItem(AdCatalogItem adCatalogItem) {
        if (adCatalogItem.getContentHash() != null) {
            adCatalogCacheByUrl.put(adCatalogItem.getUrl(), new CacheEntry<>(adCatalogItem, System.currentTimeMillis()));
        } else {
            logger.warn("Got ad media item without a content hash: {}", adCatalogItem);
        }
        return adCatalogItem;
    }

    public byte[] retrieveTranscodedFileForAdMedia(String mediaId, String codecId, Integer loudnessTarget) {
        String lt = loudnessTarget != null ? loudnessTarget.toString() : "";
        String requestUrl = UriComponentsBuilder.fromUriString(config.getAdMediaServiceUrl())
                .path("/media/items/" + mediaId + "/files/transcoded")
                .queryParam("codecId", codecId)
                .queryParam("loudnessTarget", lt)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-item-id", mediaId);
        try {
            ResponseEntity<byte[]> response = webClient.get()
                    .uri(requestUrl)
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .toEntity(byte[].class)
                    .block();
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new RuntimeException("Failed url: " + requestUrl + " with http status: " + (response != null ? response.getStatusCode() : "no response"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CodecSettingInfo retrieveCodecSettingInfoForCodec(CodecInfo codecInfo) {
        int cacheKey = codecInfo.hashCode();
        CacheEntry<CodecSettingInfo> cached = codecSettingInfoCache.get(cacheKey);
        return cached != null ? cached.value() : retrieveCodecSettingInfoForCodecImpl(cacheKey, codecInfo);
    }

    private CodecSettingInfo retrieveCodecSettingInfoForCodecImpl(int cacheKey, CodecInfo codecInfo) {
        CodecSettingInfo codec = getCodecFrom(codecInfo);
        String url = config.getAdMediaServiceUrl() + "/codecsettings/codecsetting";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = converter.serializeJson(codec);
            ResponseEntity<String> response = webClient.post()
                    .uri(url)
                    .headers(h -> h.addAll(headers))
                    .bodyValue(body)
                    .retrieve()
                    .toEntity(String.class)
                    .block();
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                CodecSettingInfo result = converter.deserializeJson(response.getBody(), CodecSettingInfo.class);
                codecSettingInfoCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis()));
                return result;
            }
            throw new RuntimeException("Failed url: " + url + " http status: " + (response != null ? response.getStatusCode() : "no response"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CodecSettingInfo getCodecFrom(CodecInfo codecInfo) {

        final CodecSettingInfo.CodecSettingInfoBuilder builder = CodecSettingInfo.builder()
                .codec(codecInfo.codec())
                .bitRate(codecInfo.bitRate())
                .sampleRate(codecInfo.sampleRate())
                .channels(codecInfo.channels());
        codecInfo.profile().map(builder::profile);
        codecInfo.duration().map(builder::duration);

        return builder.build();
    }

    public AdResponse getAd(String breakId, long duration, boolean preroll, SessionContext session) {
        ZoneId zoneId = preroll ? ZoneId.PREROLL : ZoneId.MIDROLL;
        String url = UriComponentsBuilder.fromUriString(config.getAdProviderUrl())
                .queryParam("breakId", breakId)
                .queryParam("zoneId", zoneId)
                .queryParam("duration", duration)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Device-Ip", session.getClientIp());

        HttpEntity<SessionContext> entity = new HttpEntity<>(session, headers);

        try {
            ResponseEntity<AdResponse> response = webClient.post()
                    .uri(url)
                    .headers(h -> h.addAll(entity.getHeaders()))
                    .bodyValue(session)
                    .retrieve()
                    .toEntity(AdResponse.class)
                    .block();
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            logger.error("Failed to get ad from {} http status {}", url, response != null ? response.getStatusCode() : "no response");
        } catch (Exception e) {
            logger.error("Failed to get ad from {}", url, e);
        }
        return new AdResponse();
    }

    public void skipNext(SessionContext session) {
        String url = config.getAdProviderUrl() + "/skip-next";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Device-Ip", session.getClientIp());
        HttpEntity<SessionContext> entity = new HttpEntity<>(session, headers);
        try {
            ResponseEntity<Void> response = webClient.post()
                    .uri(url)
                    .headers(h -> h.addAll(entity.getHeaders()))
                    .bodyValue(session)
                    .retrieve()
                    .toEntity(Void.class)
                    .block();
            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Skip next ad by calling: {}", url);
            } else if (response != null) {
                logger.warn("Failed skip next ad by calling: {} {}", url, response.getStatusCode());
            } else {
                logger.warn("Failed skip next ad by calling: {}", url);
            }
        } catch (Exception e) {
            logger.warn("Failed skip next ad by calling: {}", url, e);
        }
    }


}
