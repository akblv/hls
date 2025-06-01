package com.example.hls.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.hls.model.NginxRtmpRequest;
import com.example.hls.service.HlsService;
import com.example.hls.service.FfmpegService;
import com.example.hls.service.session.SessionTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

import java.util.Map;


@RestController
@RequestMapping("/live/stream")
public class HlsController {
    private static final Logger logger = LoggerFactory.getLogger(HlsController.class);
    private static final Pattern STREAM_PATTERN =
            Pattern.compile("^([0-9a-z]{11}(tv|uv|vv)|[0-9a-z]{7}mb)$");

    private final HlsService hlsService;
    private final SessionTokenService tokenService;
    private final FfmpegService ffmpegService;

    @Autowired
    public HlsController(HlsService hlsService, SessionTokenService tokenService, FfmpegService ffmpegService) {
        this.hlsService = hlsService;
        this.tokenService = tokenService;
        this.ffmpegService = ffmpegService;
    }

    boolean isStreamValid(String stream) {
        return stream != null && STREAM_PATTERN.matcher(stream).matches();
    }


    @PostMapping("/validate")
    public ResponseEntity<Void> validate(@RequestParam Map<String, String> params) {
        NginxRtmpRequest request = new NginxRtmpRequest(params);
        logger.info("Validating key {}", request);
        if (!isStreamValid(request.name())) {
            logger.warn("Invalid stream name {}", request.name());
            return ResponseEntity.badRequest().build();
        }
        // start transcoding asynchronously after acknowledging the publish
        ffmpegService.startTranscoding(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/done")
    public ResponseEntity<Void> done(@RequestParam Map<String, String> params) {
        logger.info("Stream ended {}", params);
        return ResponseEntity.ok().build();

    }

    /**
     * Return the playlist with a simple advertisement insertion after every third segment.
     */
    @GetMapping(path = "/{stream}/{playlist}.m3u8", produces = "application/vnd.apple.mpegurl")
    public Mono<ResponseEntity<?>> getPlaylist(@PathVariable String stream, @PathVariable String playlist, HttpServletRequest request) {
//        String token = request.getParameter("zt");
//        if (!tokenService.isValid(token, name)) {
//            String newToken = tokenService.generateToken(name);
//            String url = request.getRequestURL().toString() + "?zt=" + newToken;
//            return ResponseEntity.status(307).header("Location", url).build();
//        }

        String user = request.getRemoteAddr();
        return hlsService.getPlaylist(stream, playlist, "", user)
                .map(body -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noCache())
                        .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                        .body(body));
    }

    @GetMapping(path = "{stream}/{quality}/{playlist}.m3u8", produces = "application/vnd.apple.mpegurl")
    public Mono<ResponseEntity<?>> getPlaylistWithQuality(@PathVariable String stream, @PathVariable String quality, @PathVariable String playlist, HttpServletRequest request) {
//        String token = request.getParameter("zt");
//        if (!tokenService.isValid(token, name)) {
//            String newToken = tokenService.generateToken(name);
//            String url = request.getRequestURL().toString() + "?zt=" + newToken;
//            return ResponseEntity.status(307).header("Location", url).build();
//        }

        String user = request.getRemoteAddr();
        return hlsService.getPlaylist(stream, playlist, quality, user)
                .map(body -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noCache())
                        .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                        .body(body));
    }

    @GetMapping(value = "{stream}/{segment}.ts", produces = "video/MP2T")
    public Mono<ResponseEntity<Resource>> getSegment(@PathVariable String stream, @PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        return hlsService.getSegment(stream, segment, "", user)
                .map(data -> {
                    logger.debug("Serving segment {}", segment);
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.noCache())
                            .body(new ByteArrayResource(data));
                });
    }

    @GetMapping(value = "{stream}/{quality}/{segment}.ts", produces = "video/MP2T")
    public Mono<ResponseEntity<Resource>> getSegmentWithQuality(@PathVariable String stream, @PathVariable String quality, @PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        return hlsService.getSegment(stream, segment, quality, user)
                .map(data -> {
                    logger.debug("Serving segment {} for quality {}", segment, quality);
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.noCache())
                            .body(new ByteArrayResource(data));
                });
    }

    @GetMapping(value = "{stream}/{quality}/ads/{segment}.ts", produces = "video/MP2T")
    public Mono<ResponseEntity<Resource>> getAdSegment(@PathVariable String stream, @PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        return hlsService.getAdSegment(segment, "", user)
                .map(data -> {
                    logger.debug("Serving ad segment {}", segment);
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.noCache())
                            .body(new ByteArrayResource(data));
                });
    }

    @GetMapping(value = "{stream}/{quality}/ads/{ad_quality}/{segment}.ts", produces = "video/MP2T")
    public Mono<ResponseEntity<Resource>> getAdSegmentWithQuality(@PathVariable String stream, @PathVariable String ad_quality, @PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        return hlsService.getAdSegment(segment, ad_quality, user)
                .map(data -> {
                    logger.debug("Serving ad segment {} for quality {}", segment, ad_quality);
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.noCache())
                            .body(new ByteArrayResource(data));
                });
    }
}
