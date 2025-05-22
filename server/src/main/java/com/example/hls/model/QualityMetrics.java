package com.example.hls.model;

/**
 * Stores metrics related to a specific stream quality.
 */
public class QualityMetrics {
    private String lastDownloadedChunk;
    private long downloadedBytes;

    public synchronized String getLastDownloadedChunk() {
        return lastDownloadedChunk;
    }

    public synchronized long getDownloadedBytes() {
        return downloadedBytes;
    }

    public synchronized void update(String chunk, long bytes) {
        this.lastDownloadedChunk = chunk;
        this.downloadedBytes += bytes;
    }
}
