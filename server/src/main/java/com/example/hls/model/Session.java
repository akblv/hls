package com.example.hls.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a streaming session collecting metrics for each available quality.
 */
public class Session {
    private final Instant startTime;
    private final Map<String, QualityMetrics> qualities = new ConcurrentHashMap<>();
    private final Deque<String> adsToInsert = new ArrayDeque<>();
    private final int frequencySegments;
    private int segmentsServed = 0;
    private boolean adDue = false;
    private final List<String> adSegments = List.of("ad-0.ts", "ad-1.ts", "ad-2.ts");

    public Session() {
        this(0);
    }

    public Session(int frequencySegments) {
        this.startTime = Instant.now();
        this.frequencySegments = Math.max(0, frequencySegments);
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

    /**
     * Records that another segment has been served and determines when an ad should be inserted.
     */
    public synchronized void incrementSegments() {
        if (frequencySegments <= 0) {
            return;
        }
        segmentsServed++;
        if (segmentsServed >= frequencySegments && !adDue) {
            adDue = true;
        }
    }

    /**
     * Whether the next playlist response should include an ad break.
     */
    public synchronized boolean shouldInsertAd() {
        return adDue;
    }

    /**
     * Returns the predefined ad segments to be inserted.
     */
    public List<String> getNextAdSegments() {
        return adSegments;
    }

    /**
     * Marks that the ad has been inserted and resets the counter.
     */
    public synchronized void markAdInserted() {
        adDue = false;
        segmentsServed = 0;
    }
}
