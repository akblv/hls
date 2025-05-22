package com.example.hls;

import com.example.hls.service.SessionService;
import com.example.hls.model.Session;
import com.example.hls.model.QualityMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTests {

    @Test
    void metricsAreUpdated() {
        SessionService service = new SessionService();
        service.updateMetrics("s1", "720p", "chunk0.ts", 100);
        Session session = service.getSession("s1");
        QualityMetrics metrics = session.getMetrics("720p");
        assertEquals("chunk0.ts", metrics.getLastDownloadedChunk());
        assertEquals(100, metrics.getDownloadedBytes());
    }
}
