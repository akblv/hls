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
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reactive version of the Scala AdMediaService using WebClient.
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

    public Mono<List<AdCatalogItem>> retrieveAdMediaPaginated(String mount, String from, String size, String page) {
        String url = UriComponentsBuilder.fromUriString(config.getAdMediaServiceUrl())
                .path("/media/items")
                .queryParam("stream", mount)
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParam("from", from)
                .toUriString();
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> converter.deserializeJson(body, new TypeReference<List<AdCatalogItem>>() {}))
                .doOnError(e -> logger.warn("Failed retrieving ad media page {}", page, e))
                .onErrorReturn(Collections.emptyList());
    }

    public Mono<AdCatalogItem> retrieveAdMediaForUrl(String url) {
        CacheEntry<AdCatalogItem> cached = adCatalogCacheByUrl.get(url);
        return cached != null ? Mono.just(cached.value()) : retrieveAdMediaForUrlImpl(url);
    }

    public Mono<AdCatalogItem> retrieveAdMediaForUrlImpl(String url) {
        String requestUrl = UriComponentsBuilder.fromUriString(config.getAdMediaServiceUrl())
                .path("/media/items/find")
                .queryParam("url", url)
                .toUriString();
        return webClient.get()
                .uri(requestUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> cacheAdCatalogItem(converter.deserializeJson(body, AdCatalogItem.class)))
                .onErrorMap(e -> new RuntimeException(e));
    }

    public AdCatalogItem cacheAdCatalogItem(AdCatalogItem adCatalogItem) {
        if (adCatalogItem.getContentHash() != null) {
            adCatalogCacheByUrl.put(adCatalogItem.getUrl(), new CacheEntry<>(adCatalogItem, System.currentTimeMillis()));
        } else {
            logger.warn("Got ad media item without a content hash: {}", adCatalogItem);
        }
        return adCatalogItem;
    }

    public Mono<byte[]> retrieveTranscodedFileForAdMedia(String mediaId, String codecId, Integer loudnessTarget) {
        String lt = loudnessTarget != null ? loudnessTarget.toString() : "";
        String requestUrl = UriComponentsBuilder.fromUriString(config.getAdMediaServiceUrl())
                .path("/media/items/" + mediaId + "/files/transcoded")
                .queryParam("codecId", codecId)
                .queryParam("loudnessTarget", lt)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-item-id", mediaId);
        return webClient.get()
                .uri(requestUrl)
                .headers(h -> h.addAll(headers))
                .retrieve()
                .bodyToMono(byte[].class)
                .onErrorMap(e -> new RuntimeException(e));
    }

    public Mono<CodecSettingInfo> retrieveCodecSettingInfoForCodec(CodecInfo codecInfo) {
        int cacheKey = codecInfo.hashCode();
        CacheEntry<CodecSettingInfo> cached = codecSettingInfoCache.get(cacheKey);
        return cached != null ? Mono.just(cached.value()) : retrieveCodecSettingInfoForCodecImpl(cacheKey, codecInfo);
    }

    private Mono<CodecSettingInfo> retrieveCodecSettingInfoForCodecImpl(int cacheKey, CodecInfo codecInfo) {
        CodecSettingInfo codec = getCodecFrom(codecInfo);
        String url = config.getAdMediaServiceUrl() + "/codecsettings/codecsetting";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = converter.serializeJson(codec);
        return webClient.post()
                .uri(url)
                .headers(h -> h.addAll(headers))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(resp -> {
                    CodecSettingInfo result = converter.deserializeJson(resp, CodecSettingInfo.class);
                    codecSettingInfoCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis()));
                    return result;
                })
                .onErrorMap(e -> new RuntimeException(e));
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

    public Mono<AdResponse> getAd(String breakId, long duration, boolean preroll, SessionContext session) {
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
        return webClient.post()
                .uri(url)
                .headers(h -> h.addAll(entity.getHeaders()))
                .bodyValue(session)
                .retrieve()
                .bodyToMono(AdResponse.class)
                .doOnError(e -> logger.error("Failed to get ad from {}", url, e))
                .onErrorReturn(new AdResponse());
    }

    public Mono<Void> skipNext(SessionContext session) {
        String url = config.getAdProviderUrl() + "/skip-next";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Device-Ip", session.getClientIp());
        HttpEntity<SessionContext> entity = new HttpEntity<>(session, headers);
        return webClient.post()
                .uri(url)
                .headers(h -> h.addAll(entity.getHeaders()))
                .bodyValue(session)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> logger.debug("Skip next ad by calling: {}", url))
                .doOnError(e -> logger.warn("Failed skip next ad by calling: {}", url, e))
                .then();
    }


}
