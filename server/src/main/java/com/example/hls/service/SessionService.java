package com.example.hls.service;

import com.example.hls.model.QualityMetrics;
import com.example.hls.model.Session;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages {@link Session} instances and updates their metrics.
 */
@Service
public class SessionService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Returns the session for the given id, creating it if necessary.
     */
    public Session getSession(String sessionId) {
        return getSession(sessionId, 0);
    }

    /**
     * Returns the session for the given id, creating it with the provided
     * frequency if necessary.
     */
    public Session getSession(String sessionId, int frequencySegments) {
        return sessions.computeIfAbsent(sessionId, id -> new Session(frequencySegments));
    }

    /**
     * Updates metrics for a particular quality.
     */
    public void updateMetrics(String sessionId, String quality, String chunk, long bytes) {
        Session session = getSession(sessionId);
        QualityMetrics metrics = session.getMetrics(quality);
        metrics.update(chunk, bytes);
    }

    /**
     * Adds an ad to be inserted for the session.
     */
    public void queueAd(String sessionId, String adSegment) {
        getSession(sessionId).addAd(adSegment);
    }
}
