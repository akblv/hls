package com.example.hls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.hls.service.HlsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/hls")
public class HlsController {
    private static final Logger logger = LoggerFactory.getLogger(HlsController.class);

    private final HlsService hlsService;

    @Autowired
    public HlsController(HlsService hlsService) {
        this.hlsService = hlsService;
    }

    /**
     * Return the playlist with a simple advertisement insertion after every third segment.
     */
    @GetMapping(path = "/{name}.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> getPlaylist(@PathVariable String name, javax.servlet.http.HttpServletRequest request) {
        String user = request.getRemoteAddr();
        String body = hlsService.getPlaylist(name, user);
        logger.debug("Playlist {}", body);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                .body(body);
    }

    @GetMapping(value = "/{segment}.ts", produces = "video/MP2T")
    public ResponseEntity<Resource> getSegment(@PathVariable String segment, javax.servlet.http.HttpServletRequest request) {
        String user = request.getRemoteAddr();
        byte[] data = hlsService.getSegment(segment, user);
        logger.debug("Serving segment {}", segment);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new ByteArrayResource(data));
    }

    @GetMapping(value = "/ads/{segment}.ts", produces = "video/MP2T")
    public ResponseEntity<Resource> getAdSegment(@PathVariable String segment) {
        byte[] data = hlsService.getAdSegment(segment);
        logger.debug("Serving ad segment {}", segment);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new ByteArrayResource(data));
    }
}
