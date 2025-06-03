package com.example.hls.service;

import ch.qos.logback.core.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.example.hls.model.Session;
import java.util.*;

@Service
public class HlsService {
    private static final Logger logger = LoggerFactory.getLogger(HlsService.class);
    @Value("${hls.origin-base-url}")
    private String originBaseUrl;

    @Value("${hls.ad-base-url}")
    private String adBaseUrl;

    @Value("${hls.ad-frequency-minutes:2}")
    private int adFrequencyMinutes;

    /**
     * approximate duration of each segment in seconds
     */
    @Value("${hls.segment-duration-seconds:5}")
    private int segmentDurationSeconds;

    private final WebClient webClient;

    private final SessionService sessionService;

    @Autowired
    public HlsService(WebClient webClient, SessionService sessionService) {
        this.webClient = webClient;
        this.sessionService = sessionService;
    }

    private Session getSession(String userId) {
//        int freqSegments = (adFrequencyMinutes * 60) / segmentDurationSeconds;
        int freqSegments = (adFrequencyMinutes * 10) / segmentDurationSeconds;
        return sessionService.getSession(userId, freqSegments);
    }

    public Mono<String> getPlaylist(String streamName, String playlist, String quality, String userId) {
        String baseUrl = String.format("%s/%s", originBaseUrl, streamName);
        String base = buildQualityPath(baseUrl, quality);
        String url = base + "/" + playlist + ".m3u8";
        return webClient.get().uri(url).retrieve().bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(m3u8 -> {
                    if (m3u8.isEmpty()) {
                        return "";
                    }
                    Session session = getSession(userId);
                    List<String> lines = new ArrayList<>(Arrays.asList(m3u8.split("\n")));
                    boolean shouldInsertAd = session.shouldInsertAd() && !playlist.equals("master");
                    logger.info("Session {} should insert ad {}", session.toString(), session.shouldInsertAd());
                    if (shouldInsertAd) {
                        session.markAdInserted();
                        lines.add("#EXT-X-DISCONTINUITY");
                        session.getNextAdSegments().forEach(ad -> {
                            lines.add("#EXTINF:" + segmentDurationSeconds + ".0,");
                            String prefix = StringUtil.isNullOrEmpty(quality) ? "ads/" : "ads/" + quality + "/";
                            lines.add(prefix + ad);
                        });
                        lines.add("#EXT-X-DISCONTINUITY");
                        logger.info("Insert ad segments into session, {}", String.join("\n", lines));
                    }
                    return String.join("\n", lines);
                });
    }

    public Mono<byte[]> getSegment(String stream, String segmentName, String quality, String userId) {
        String baseUrl = String.format("%s/%s", originBaseUrl, stream);
        return downloadChunk(baseUrl, quality, segmentName)
                .map(data -> {
                    Session session = getSession(userId);
                    session.incrementSegments();
                    int length = data == null ? 0 : data.length;
                    sessionService.updateMetrics(userId, StringUtil.isNullOrEmpty(quality) ? "" : quality,
                            segmentName + ".ts",
                            length);
                    return data;
                });
    }

    public Mono<byte[]> getAudioSegment(String stream, String segmentName, String quality, String userId) {
        String baseUrl = String.format("%s/%s", originBaseUrl, stream);
        return downloadAudioChunk(baseUrl, quality, segmentName)
                .map(data -> {
                    Session session = getSession(userId);
                    session.incrementSegments();
                    int length = data == null ? 0 : data.length;
                    sessionService.updateMetrics(userId,
                            StringUtil.isNullOrEmpty(quality) ? "" : quality,
                            segmentName + ".aac",
                            length);
                    return data;
                });
    }

    private Mono<byte[]> downloadChunk(String originBaseUrl, String quality, String segmentName) {
        String url = buildSegmentPath(originBaseUrl, quality, segmentName, "ts");
        logger.debug("Downloading chunk {}", url);
        return webClient.get().uri(url).retrieve().bodyToMono(byte[].class);
    }

    private Mono<byte[]> downloadAudioChunk(String originBaseUrl, String quality, String segmentName) {
        String url = buildSegmentPath(originBaseUrl, quality, segmentName, "aac");
        logger.debug("Downloading chunk {}", url);
        return webClient.get().uri(url).retrieve().bodyToMono(byte[].class);
    }

    private String buildQualityPath(String originBaseUrl, String quality) {
        String base = originBaseUrl;
        if (Objects.nonNull(quality) && !quality.isEmpty()) {
            base += String.format("/%s", quality);
        }
        logger.debug("Building quality path {}", base);
        return base;
    }

    public Mono<byte[]> getAdSegment(String segmentName, String quality, String userId) {

        return downloadChunk(adBaseUrl, quality, segmentName)
                .map(data -> {
                    int length = data == null ? 0 : data.length;
                    sessionService.updateMetrics(userId,
                            Optional.ofNullable(quality).orElse(""),
                            buildSegmentPath("ads", quality, segmentName),
                            length);
                    return data;
                });
    }

    public Mono<byte[]> getAudioAdSegment(String segmentName, String quality, String userId) {
        return downloadAudioChunk(adBaseUrl, quality, segmentName)
                .map(data -> {
                    int length = data == null ? 0 : data.length;
                    sessionService.updateMetrics(userId,
                            Optional.ofNullable(quality).orElse(""),
                            buildSegmentPath("ads", quality, segmentName, "aac"),
                            length);
                    return data;
                });
    }

    private String buildSegmentPath(String basePath, String quality, String segmentName) {
        return buildSegmentPath(basePath, quality, segmentName, "ts");
    }

    private String buildSegmentPath(String basePath, String quality, String segmentName, String ext) {
        return String.format("%s/%s.%s", buildQualityPath(basePath, quality), segmentName, ext);
    }

}
