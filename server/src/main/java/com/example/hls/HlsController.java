package com.example.hls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/hls")
public class HlsController {
    private static final Logger logger = LoggerFactory.getLogger(HlsController.class);

    @Value("${hls.base-path}")
    private String basePath;

    /**
     * Return the playlist inserting an advertisement block every two minutes of
     * content. The ad block consists of three segments of five seconds each
     * (15 seconds total).
     */
    @GetMapping(path = "/{name}.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> getPlaylist(@PathVariable String name) throws IOException {
        Path playlistPath = Paths.get(basePath, name + ".m3u8");
        List<String> original = Files.readAllLines(playlistPath);
        List<String> modified = new ArrayList<>();

        double elapsed = 0.0;
        for (int i = 0; i < original.size(); i++) {
            String line = original.get(i);

            if (line.startsWith("#EXTINF:")) {
                // Add EXTINF line and following segment line
                modified.add(line);
                double duration = parseDuration(line);
                if (i + 1 < original.size()) {
                    String segment = original.get(++i);
                    modified.add(segment);
                    elapsed += duration;
                    if (elapsed >= 120.0) {
                        insertAdBlock(modified);
                        elapsed = 0.0;
                    }
                }
            } else {
                modified.add(line);
            }
        }
        String body = String.join("\n", modified);
        logger.debug("Playlist {}", body);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                .body(body);
    }

    @GetMapping(value = "/{segment}.ts", produces = "video/MP2T")
    public ResponseEntity<Resource> getSegment(@PathVariable String segment) throws IOException {
        Path path = Paths.get(basePath, segment + ".ts");
        logger.debug("Serving segment {}", path);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new FileSystemResource(path));
    }

    @GetMapping(value = "/ads/{segment}.ts", produces = "video/MP2T")
    public ResponseEntity<Resource> getAdSegment(@PathVariable String segment) throws IOException {
        Path path = Paths.get(basePath, "ads", segment + ".ts");
        logger.debug("Serving ad segment {}", path);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new FileSystemResource(path));
    }

    private static double parseDuration(String extinfLine) {
        try {
            int start = extinfLine.indexOf(':') + 1;
            int end = extinfLine.indexOf(',');
            return Double.parseDouble(extinfLine.substring(start, end));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static void insertAdBlock(List<String> playlist) {
        playlist.add("#EXT-X-DISCONTINUITY");
        for (int i = 0; i < 3; i++) {
            playlist.add("#EXTINF:5.0,");
            playlist.add("ads/ad" + i + ".ts");
        }
    }
}
