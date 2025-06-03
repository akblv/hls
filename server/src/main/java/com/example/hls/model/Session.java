package com.example.hls.model;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a streaming session collecting metrics for each available quality.
 */
public class Session {
    private final String sessionId = String.valueOf(System.currentTimeMillis());
    private final Instant startTime;
    private final Map<String, QualityMetrics> qualities = new ConcurrentHashMap<>();
    private final Deque<String> adsToInsert = new ArrayDeque<>();
    private final AtomicInteger frequencySegments = new AtomicInteger(0);
    private final AtomicInteger segmentsServed = new AtomicInteger(0);
    private final AtomicBoolean adDue = new AtomicBoolean(Boolean.FALSE);
    private final List<String> adSegments = List.of("ad-0.ts", "ad-1.ts", "ad-2.ts");


    public Session(int frequencySegments) {
        this.startTime = Instant.now();
        this.frequencySegments.set(frequencySegments);
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
    public void addAd(String adSegment) {
        adsToInsert.add(adSegment);
    }

    /**
     * Retrieves and removes the next ad segment to insert, or {@code null} if none.
     */
    public String pollAd() {
        return adsToInsert.poll();
    }

    /**
     * Records that another segment has been served and determines when an ad should be inserted.
     */
    public void incrementSegments() {
        int freq = frequencySegments.get();
        if (freq <= 0) {
            return;
        }
        int served = segmentsServed.incrementAndGet();
        boolean shouldInsertAd = adDue.get();
        if (served >= freq && !shouldInsertAd) {
            adDue.set(Boolean.TRUE);
            System.out.println("Ad due");
        }
        System.out.println("Served " + served + " segments should insert ad? " + shouldInsertAd );
    }

    /**
     * Whether the next playlist response should include an ad break.
     */
    public boolean shouldInsertAd() {
        System.out.println("Should insert ad? " + adDue.get() + "");
        return adDue.get();
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
    public void markAdInserted() {
        adDue.set(Boolean.FALSE);
        segmentsServed.set(0);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Session session = (Session) o;
        return Objects.equals(sessionId, session.sessionId) && startTime.equals(session.startTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, startTime);
    }

    @Override
    public String toString() {
        return "Session{" +
                "startTime=" + startTime +
                ", sessionId='" + sessionId + '\'' +
                '}';
    }
}
