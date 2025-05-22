package com.example.hls.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a streaming session collecting metrics for each available quality.
 */
public class Session {
    private final Instant startTime;
    private final Map<String, QualityMetrics> qualities = new ConcurrentHashMap<>();
    private final Deque<String> adsToInsert = new ArrayDeque<>();

    public Session() {
        this.startTime = Instant.now();
    }

    /**
     * Returns the time in seconds the user has been listening.
     */
    public long getListeningSeconds() {
        return Duration.between(startTime, Instant.now()).getSeconds();
    }

    /**
     * Returns quality metrics for the requested quality, creating them if missing.
     */
    public QualityMetrics getMetrics(String quality) {
        return qualities.computeIfAbsent(quality, q -> new QualityMetrics());
    }

    /**
     * Queue an ad segment to be inserted for this session.
     */
    public synchronized void addAd(String adSegment) {
        adsToInsert.add(adSegment);
    }

    /**
     * Retrieves and removes the next ad segment to insert, or {@code null} if none.
     */
    public synchronized String pollAd() {
        return adsToInsert.poll();
    }
}
