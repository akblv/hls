package com.zenomedia.logic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zenomedia.Sys;
import com.zenomedia.common.model.ads.AdCatalogItem;
import com.zenomedia.common.model.transcode.CodecSettingInfo;
import com.zenomedia.model.CodecInfo;
import com.zenomedia.config.AdMediaConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synchronous version of the Scala AdMediaService using RestTemplate.
 */
@Service
public class AdMediaService {

    private static final Logger logger = LogManager.getLogger(AdMediaService.class);

    private final RestTemplate restTemplate;
    private final AdMediaConfiguration config;

    private final Map<Integer, CacheEntry<CodecSettingInfo>> codecSettingInfoCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AdCatalogItem>> adCatalogCacheByUrl = new ConcurrentHashMap<>();

    @Autowired
    public AdMediaService(RestTemplate restTemplate, AdMediaConfiguration config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    /**
     * Periodic cleanup of expired cache entries.
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanUpCaches() {
        logger.debug("Ad media cache cleanup triggered");
        long now = System.currentTimeMillis();
        codecSettingInfoCache.entrySet().removeIf(e -> now - e.getValue().timestamp > config.getCodecSettingInfoCacheExpiration());
        adCatalogCacheByUrl.entrySet().removeIf(e -> now - e.getValue().timestamp > config.getAdCatalogCacheExpiration());
    }

    public void warmup() {
        // Placeholder for warmup logic if needed
    }

    public List<AdCatalogItem> retrieveAdMediaPaginated(String mount, String from, String size, String page) {
        String url = UriComponentsBuilder.fromHttpUrl(config.getAdMediaServiceUrl())
                .path("/media/items")
                .queryParam("stream", mount)
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParam("from", from)
                .toUriString();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Sys.mapper.readValue(response.getBody(), new TypeReference<List<AdCatalogItem>>(){});
            }
        } catch (Exception e) {
            logger.warn("Failed retrieving ad media page {}", page, e);
        }
        return Collections.emptyList();
    }

    public AdCatalogItem retrieveAdMediaForUrl(String url) {
        CacheEntry<AdCatalogItem> cached = adCatalogCacheByUrl.get(url);
        return cached != null ? cached.value : retrieveAdMediaForUrlImpl(url);
    }

    public AdCatalogItem retrieveAdMediaForUrlImpl(String url) {
        String requestUrl = UriComponentsBuilder.fromHttpUrl(config.getAdMediaServiceUrl())
                .path("/media/items/find")
                .queryParam("url", url)
                .toUriString();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(requestUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return cacheAdCatalogItem(Sys.mapper.readValue(response.getBody(), AdCatalogItem.class));
            }
            throw new RuntimeException("Failed url: " + requestUrl + " with http status: " + response.getStatusCodeValue());
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
        String requestUrl = UriComponentsBuilder.fromHttpUrl(config.getAdMediaServiceUrl())
                .path("/media/items/" + mediaId + "/files/transcoded")
                .queryParam("codecId", codecId)
                .queryParam("loudnessTarget", lt)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-item-id", mediaId);
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new RuntimeException("Failed url: " + requestUrl + " with http status: " + response.getStatusCodeValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CodecSettingInfo retrieveCodecSettingInfoForCodec(CodecInfo codecInfo) {
        int cacheKey = codecInfo.hashCode();
        CacheEntry<CodecSettingInfo> cached = codecSettingInfoCache.get(cacheKey);
        return cached != null ? cached.value : retrieveCodecSettingInfoForCodecImpl(cacheKey, codecInfo);
    }

    private CodecSettingInfo retrieveCodecSettingInfoForCodecImpl(int cacheKey, CodecInfo codecInfo) {
        CodecSettingInfo codec = getCodecFrom(codecInfo);
        String url = config.getAdMediaServiceUrl() + "/codecsettings/codecsetting";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<byte[]> entity = new HttpEntity<>(Sys.mapper.writeValueAsBytes(codec), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                CodecSettingInfo result = Sys.mapper.readValue(response.getBody(), CodecSettingInfo.class);
                codecSettingInfoCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis()));
                return result;
            }
            throw new RuntimeException("Failed url: " + url + " http status: " + response.getStatusCodeValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CodecSettingInfo getCodecFrom(CodecInfo codecInfo) {
        int sampleRate = codecInfo.sampleRate();
        int bitRate = codecInfo.bitRate();
        String codec = codecInfo.codec();
        int channels = codecInfo.channels();
        Integer duration = codecInfo.duration();
        String profile = codecInfo.profile();

        CodecSettingInfo.Builder builder = CodecSettingInfo.builder()
                .codec(codec)
                .bitRate(bitRate)
                .sampleRate(sampleRate)
                .channels(channels);
        if (profile != null) {
            builder.profile(profile);
        }
        if (duration != null) {
            builder.duration(duration);
        }
        return builder.build();
    }

    /** Simple holder for cached values. */
    private record CacheEntry<T>(T value, long timestamp) {}
}
