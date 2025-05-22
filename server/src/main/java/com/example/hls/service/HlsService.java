package com.example.hls.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HlsService {
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

    public String getPlaylist(String streamName, String quality, String userId) {
        String base = buildQualityPath(originBaseUrl, quality);
        String url = base + "/" + streamName + ".m3u8";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        String playlist = response.getBody();
        if (playlist == null) {
            return "";
        }
        UserSession session = getSession(userId);
        List<String> lines = new ArrayList<>(Arrays.asList(playlist.split("\n")));
        if (session.shouldInsertAd()) {
            lines.add("#EXT-X-DISCONTINUITY");
            for (String ad : session.getNextAdSegments()) {
                lines.add("#EXTINF:" + segmentDurationSeconds + ".0,");
                String prefix = quality == null || quality.isEmpty()
                        ? "ads/"
                        : "../ads/" + quality + "/";
                lines.add(prefix + ad);
            }
            lines.add("#EXT-X-DISCONTINUITY");
            session.markAdInserted();
        }
        return String.join("\n", lines);
    }

    public byte[] getSegment(String segmentName, String quality, String userId) {
        ResponseEntity<byte[]> response = downloadChunk(originBaseUrl, quality, segmentName);
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
        return restTemplate.getForEntity(url, byte[].class);
    }

    private String buildQualityPath(String originBaseUrl, String quality) {
        String base = originBaseUrl;
        if (Objects.nonNull(quality) && !quality.isEmpty()) {
            base += String.format("/%s", quality);
        }
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

    private String buildSegmentPath(String basePath, String segmentName, String quality) {
        return buildQualityPath(basePath, quality) + "/" + segmentName + ".ts";
    }

    private static class UserSession {
        private final int frequencySegments;
        private int segmentsServed = 0;
        private boolean adDue = false;
        private final List<String> adSegments = Arrays.asList("ad0.ts", "ad1.ts", "ad2.ts");

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
