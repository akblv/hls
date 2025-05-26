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

import java.util.Map;

@RestController
@RequestMapping("/live/audio")
public class AudioHlsController {
    private static final Logger logger = LoggerFactory.getLogger(AudioHlsController.class);

    private final HlsService hlsService;
    private final SessionTokenService tokenService;
    private final FfmpegService ffmpegService;

    @Autowired
    public AudioHlsController(HlsService hlsService, SessionTokenService tokenService, FfmpegService ffmpegService) {
        this.hlsService = hlsService;
        this.tokenService = tokenService;
        this.ffmpegService = ffmpegService;
    }

    @PostMapping("/validate")
    public ResponseEntity<Void> validate(@RequestParam Map<String, String> params) {
        NginxRtmpRequest request = new NginxRtmpRequest(params);
        logger.info("Validating audio stream {}", request);
        ffmpegService.startAudioTranscoding(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/done")
    public ResponseEntity<Void> done(@RequestParam Map<String, String> params) {
        logger.info("Audio stream ended {}", params);
        return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/{stream}/{playlist}.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<?> getPlaylist(@PathVariable String stream, @PathVariable String playlist, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        String body = hlsService.getPlaylist(stream, playlist, "", user);
        logger.debug("Audio playlist {}", body);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                .body(body);
    }

    @GetMapping(path = "{stream}/{quality}/{playlist}.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<?> getPlaylistWithQuality(@PathVariable String stream, @PathVariable String quality, @PathVariable String playlist, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        String body = hlsService.getPlaylist(stream, playlist, quality, user);
        logger.debug("Audio playlist {}", body);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                .body(body);
    }

    @GetMapping(value = "{stream}/{segment}.aac", produces = "audio/aac")
    public ResponseEntity<Resource> getSegment(@PathVariable String stream, @PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        byte[] data = hlsService.getAudioSegment(stream, segment, "", user);
        logger.debug("Serving audio segment {}", segment);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new ByteArrayResource(data));
    }

    @GetMapping(value = "{stream}/{quality}/{segment}.aac", produces = "audio/aac")
    public ResponseEntity<Resource> getSegmentWithQuality(@PathVariable String stream, @PathVariable String quality, @PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        byte[] data = hlsService.getAudioSegment(stream, segment, quality, user);
        logger.debug("Serving audio segment {} for quality {}", segment, quality);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new ByteArrayResource(data));
    }

    @GetMapping(value = "{stream}/{quality}/ads/{segment}.aac", produces = "audio/aac")
    public ResponseEntity<Resource> getAdSegment(@PathVariable String stream, @PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        byte[] data = hlsService.getAudioAdSegment(segment, "", user);
        logger.debug("Serving audio ad segment {}", segment);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new ByteArrayResource(data));
    }

    @GetMapping(value = "{stream}/{quality}/ads/{ad_quality}/{segment}.aac", produces = "audio/aac")
    public ResponseEntity<Resource> getAdSegmentWithQuality(@PathVariable String stream, @PathVariable String ad_quality, @PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        byte[] data = hlsService.getAudioAdSegment(segment, ad_quality, user);
        logger.debug("Serving audio ad segment {} for quality {}", segment, ad_quality);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new ByteArrayResource(data));
    }
}
