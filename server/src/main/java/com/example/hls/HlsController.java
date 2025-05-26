package com.example.hls;

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
@RequestMapping("/live/stream")
public class HlsController {
    private static final Logger logger = LoggerFactory.getLogger(HlsController.class);

    private final HlsService hlsService;
    private final SessionTokenService tokenService;
    private final FfmpegService ffmpegService;

    @Autowired
    public HlsController(HlsService hlsService, SessionTokenService tokenService, FfmpegService ffmpegService) {
        this.hlsService = hlsService;
        this.tokenService = tokenService;
        this.ffmpegService = ffmpegService;
    }


    @PostMapping("/validate")
    public ResponseEntity<Void> validate(@RequestParam Map<String, String> params) {
        NginxRtmpRequest request = new NginxRtmpRequest(params);
        logger.info("Validating key {}", request);
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
    @GetMapping(path = "/{name}.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<?> getPlaylist(@PathVariable String name, HttpServletRequest request) {
//        String token = request.getParameter("zt");
//        if (!tokenService.isValid(token, name)) {
//            String newToken = tokenService.generateToken(name);
//            String url = request.getRequestURL().toString() + "?zt=" + newToken;
//            return ResponseEntity.status(307).header("Location", url).build();
//        }

        String user = request.getRemoteAddr();
        String body = hlsService.getPlaylist(name, "", user);
        logger.debug("Playlist {}", body);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                .body(body);
    }

    @GetMapping(path = "/{quality}/{name}.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<?> getPlaylistWithQuality(@PathVariable String quality, @PathVariable String name, HttpServletRequest request) {
//        String token = request.getParameter("zt");
//        if (!tokenService.isValid(token, name)) {
//            String newToken = tokenService.generateToken(name);
//            String url = request.getRequestURL().toString() + "?zt=" + newToken;
//            return ResponseEntity.status(307).header("Location", url).build();
//        }

        String user = request.getRemoteAddr();
        String body = hlsService.getPlaylist(name, quality, user);
        logger.debug("Playlist {}", body);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                .body(body);
    }

    @GetMapping(value = "/{segment}.ts", produces = "video/MP2T")
    public ResponseEntity<Resource> getSegment(@PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        byte[] data = hlsService.getSegment(segment, "", user);
        logger.debug("Serving segment {}", segment);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new ByteArrayResource(data));
    }

    @GetMapping(value = "/{quality}/{segment}.ts", produces = "video/MP2T")
    public ResponseEntity<Resource> getSegmentWithQuality(@PathVariable String quality, @PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        byte[] data = hlsService.getSegment(segment, quality, user);
        logger.debug("Serving segment {} for quality {}", segment, quality);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new ByteArrayResource(data));
    }

    @GetMapping(value = "/ads/{segment}.ts", produces = "video/MP2T")
    public ResponseEntity<Resource> getAdSegment(@PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        byte[] data = hlsService.getAdSegment(segment, "", user);
        logger.debug("Serving ad segment {}", segment);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new ByteArrayResource(data));
    }

    @GetMapping(value = "/ads/{quality}/{segment}.ts", produces = "video/MP2T")
    public ResponseEntity<Resource> getAdSegmentWithQuality(@PathVariable String quality, @PathVariable String segment, HttpServletRequest request) {
        String user = request.getRemoteAddr();
        byte[] data = hlsService.getAdSegment(segment, quality, user);
        logger.debug("Serving ad segment {} for quality {}", segment, quality);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new ByteArrayResource(data));
    }
}
