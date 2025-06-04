package com.example.hls.controller;

import com.example.hls.model.NginxRtmpRequest;
import com.example.hls.service.FfmpegService;
import com.example.hls.service.HlsService;
import com.example.hls.service.session.SessionContextService;
import com.example.hls.service.session.SessionTokenService;
import com.zenomedia.session.common.model.SessionTokenParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;


@RestController
@RequestMapping("/live/stream")
public class HlsController {
    private static final Logger logger = LoggerFactory.getLogger(HlsController.class);
    private static final Pattern STREAM_PATTERN =
            Pattern.compile("^([0-9a-z]{11}(tv|uv|vv)|[0-9a-z]{7}mb)$");

    private final HlsService hlsService;
    private final SessionTokenService tokenService;
    private final FfmpegService ffmpegService;
    private final SessionContextService sessionContextService;


    @Autowired
    public HlsController(HlsService hlsService, SessionTokenService tokenService, FfmpegService ffmpegService, SessionContextService sessionContextService) {
        this.hlsService = hlsService;
        this.tokenService = tokenService;
        this.ffmpegService = ffmpegService;
        this.sessionContextService = sessionContextService;
    }

    boolean isStreamValid(String stream) {
        return StringUtils.hasText(stream) && STREAM_PATTERN.matcher(stream).matches();
    }


    @PostMapping("/validate")
    public Mono<ResponseEntity<Object>> validate(ServerWebExchange request) {
        return request.getFormData()
                .map(data -> {
                    NginxRtmpRequest nginxRtmpRequest = new NginxRtmpRequest(data);
                    logger.info("Validating key {}", data);
                    if (!isStreamValid(nginxRtmpRequest.name())) {
                        logger.warn("Invalid stream name {}", nginxRtmpRequest.name());
                        return ResponseEntity.badRequest().build();
                    }
                    ffmpegService.startTranscoding(nginxRtmpRequest);
                    // start transcoding asynchronously after acknowledging the publish
                    return ResponseEntity.ok().build();
                })
                .doOnError(err -> ResponseEntity.badRequest().build());

    }

    @PostMapping("/done")
    public Mono<ResponseEntity<Object>> done(ServerWebExchange request) {
        return request.getFormData().map(data -> {
            logger.info("Stream ended {}", data);
            return ResponseEntity.ok().build();
        }).doOnError(err -> ResponseEntity.badRequest().build());

    }

    /**
     * Return the playlist with a simple advertisement insertion after every third segment.
     */
    @GetMapping(path = "/{stream}/{playlist}.m3u8", produces = "application/vnd.apple.mpegurl")
    public Mono<ResponseEntity<?>> getPlaylist(@PathVariable String stream, @PathVariable String playlist, @RequestParam String zt, ServerHttpRequest request) {
        if (!tokenService.isValid(zt, stream)) {
            logger.warn("Invalid token {} for stream {}", zt, stream);
        }
        final SessionTokenParams params = tokenService.getParams(zt);
        return sessionContextService.requestEnhancedSessionContext(request, stream, params.getId()).flatMap(sessionContext -> {
            String user = request.getRemoteAddress().getHostString();
            logger.info("Serving playlist {} for user {}", playlist, user);
            return hlsService.getPlaylist(stream, playlist, "", user)
                    .map(body -> ResponseEntity.ok()
                            .cacheControl(CacheControl.noCache())
                            .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                            .body(body));
        });


    }

    @GetMapping(path = "{stream}/{quality}/{playlist}.m3u8", produces = "application/vnd.apple.mpegurl")
    public Mono<ResponseEntity<?>> getPlaylistWithQuality(@PathVariable String stream, @PathVariable String quality, @PathVariable String playlist, ServerHttpRequest request) {
//        String token = request.getParameter("zt");
//        if (!tokenService.isValid(token, name)) {
//            String newToken = tokenService.generateToken(name);
//            String url = request.getRequestURL().toString() + "?zt=" + newToken;
//            return ResponseEntity.status(307).header("Location", url).build();
//        }

        String user = request.getRemoteAddress().getHostString();
        logger.info("Serving playlist {} for user {}", playlist, user);
        return hlsService.getPlaylist(stream, playlist, quality, user)
                .map(body -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noCache())
                        .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                        .body(body));
    }

    @GetMapping(value = "{stream}/{segment}.ts", produces = "video/MP2T")
    public Mono<ResponseEntity<Resource>> getSegment(@PathVariable String stream, @PathVariable String segment, ServerHttpRequest request) {
        String user = request.getRemoteAddress().getHostString();
        return hlsService.getSegment(stream, segment, "", user)
                .map(data -> {
                    logger.debug("Serving segment {}", segment);
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.noCache())
                            .body(new ByteArrayResource(data));
                });
    }

    @GetMapping(value = "{stream}/{quality}/{segment}.ts", produces = "video/MP2T")
    public Mono<ResponseEntity<Resource>> getSegmentWithQuality(@PathVariable String stream, @PathVariable String quality, @PathVariable String segment, ServerHttpRequest request) {
        String user = request.getRemoteAddress().getHostString();
        logger.info("Serving segment {} for user {}", segment, user);
        return hlsService.getSegment(stream, segment, quality, user)
                .map(data -> {
                    logger.debug("Serving segment {} for quality {}", segment, quality);
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.noCache())
                            .body(new ByteArrayResource(data));
                });
    }

    @GetMapping(value = "{stream}/{quality}/ads/{segment}.ts", produces = "video/MP2T")
    public Mono<ResponseEntity<Resource>> getAdSegment(@PathVariable String stream, @PathVariable String segment, ServerHttpRequest request) {
        String user = request.getRemoteAddress().getHostString();
        return hlsService.getAdSegment(segment, "", user)
                .map(data -> {
                    logger.debug("Serving ad segment {}", segment);
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.noCache())
                            .body(new ByteArrayResource(data));
                });
    }

    @GetMapping(value = "{stream}/{quality}/ads/{ad_quality}/{segment}.ts", produces = "video/MP2T")
    public Mono<ResponseEntity<Resource>> getAdSegmentWithQuality(@PathVariable String stream, @PathVariable String ad_quality, @PathVariable String segment, ServerHttpRequest request) {
        String user = request.getRemoteAddress().getHostString();
        return hlsService.getAdSegment(segment, ad_quality, user)
                .map(data -> {
                    logger.debug("Serving ad segment {} for quality {}", segment, ad_quality);
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.noCache())
                            .body(new ByteArrayResource(data));
                });
    }
}
