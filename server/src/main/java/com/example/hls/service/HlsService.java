package com.example.hls.service;

import ch.qos.logback.core.util.StringUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HlsService {
    private static final Logger logger = LoggerFactory.getLogger(HlsService.class);
    @Value("${hls.origin-base-url}")
    private String originBaseUrl;

    @Value("${hls.ad-base-url:${hls.origin-base-url}/ads}")
    private String adBaseUrl;

    @Value("${hls.ad-frequency-minutes:2}")
    private int adFrequencyMinutes;

    /**
     * approximate duration of each segment in seconds
     */
    @Value("${hls.segment-duration-seconds:5}")
    private int segmentDurationSeconds;

    private RestTemplate restTemplate;

    private final SessionService sessionService;

    @Autowired
    public HlsService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        restTemplate = new RestTemplate();
    }

    private UserSession getSession(String userId) {
        int freqSegments = (adFrequencyMinutes * 60) / segmentDurationSeconds;
        return sessions.computeIfAbsent(userId, k -> new UserSession(freqSegments));
    }

    public String getPlaylist(String streamName, String playlist, String quality, String userId) {
        String baseUrl = String.format(String.format("%s/%s", originBaseUrl, streamName));
        String base = buildQualityPath(baseUrl, quality);
        String url = base + "/" + playlist + ".m3u8";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        String m3u8 = response.getBody();
        if (m3u8 == null) {
            return "";
        }
        UserSession session = getSession(userId);
        List<String> lines = new ArrayList<>(Arrays.asList(m3u8.split("\n")));
        if (session.shouldInsertAd()) {
            lines.add("#EXT-X-DISCONTINUITY");
            session.getNextAdSegments().forEach(ad -> {
                lines.add("#EXTINF:" + segmentDurationSeconds + ".0,");
                String prefix = StringUtil.isNullOrEmpty(quality) ? "ads/" : "ads/" + quality + "/";
                lines.add(prefix + ad);
            });
            lines.add("#EXT-X-DISCONTINUITY");
            session.markAdInserted();
        }
        return String.join("\n", lines);
    }

    public byte[] getSegment(String stream, String segmentName, String quality, String userId) {
        String baseUrl = String.format(String.format("%s/%s", originBaseUrl, stream));
        ResponseEntity<byte[]> response = downloadChunk(baseUrl, quality, segmentName);
        UserSession session = getSession(userId);
        session.incrementSegments();
        byte[] data = response.getBody();
        int length = data == null ? 0 : data.length;
        sessionService.updateMetrics(userId,
                quality == null ? "" : quality,
                segmentName + ".ts",
                length);
        return data;
    }

    private ResponseEntity<byte[]> downloadChunk(String originBaseUrl, String quality, String segmentName) {
        String url = buildSegmentPath(originBaseUrl, quality, segmentName);
        logger.debug("Downloading chunk {}", url);
        return restTemplate.getForEntity(url, byte[].class);
    }

    private String buildQualityPath(String originBaseUrl, String quality) {
        String base = originBaseUrl;
        if (Objects.nonNull(quality) && !quality.isEmpty()) {
            base += String.format("/%s", quality);
        }
        logger.debug("Building quality path {}", base);
        return base;
    }

    public byte[] getAdSegment(String segmentName, String quality, String userId) {
        ResponseEntity<byte[]> response = downloadChunk(adBaseUrl, quality, segmentName);
        byte[] data = response.getBody();
        int length = data == null ? 0 : data.length;
        sessionService.updateMetrics(userId,
                Optional.ofNullable(quality).orElse(""),
                buildSegmentPath("ads", segmentName, quality),
                length);

        return data;
    }

    private String buildSegmentPath(String basePath, String quality, String segmentName) {
        return String.format("%s/%s.ts", buildQualityPath(basePath, quality), segmentName);
    }

    private static class UserSession {
        private final int frequencySegments;
        private int segmentsServed = 0;
        private boolean adDue = false;
        private final List<String> adSegments = Arrays.asList("ad-0.ts", "ad-1.ts", "ad-2.ts");

        UserSession(int frequencySegments) {
            this.frequencySegments = Math.max(1, frequencySegments);
        }

        void incrementSegments() {
            segmentsServed++;
            if (segmentsServed >= frequencySegments && !adDue) {
                adDue = true;
            }
        }

        boolean shouldInsertAd() {
            return adDue;
        }

        List<String> getNextAdSegments() {
            return adSegments;
        }

        void markAdInserted() {
            adDue = false;
            segmentsServed = 0;
        }
    }
}
