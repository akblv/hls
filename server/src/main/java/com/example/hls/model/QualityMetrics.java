package com.example.hls.model;

/**
 * Stores metrics related to a specific stream quality.
 */
public class QualityMetrics {
    private String lastDownloadedSequenceNumber;
    private String lastDownloadedChunk;
    private long downloadedBytes;

    public String getLastDownloadedChunk() {
        return lastDownloadedChunk;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public String getLastDownloadedSequenceNumber() {
        return lastDownloadedSequenceNumber;
    }

    public void update(String chunk, long bytes) {
        String[] splitLine = chunk.split("_");
        this.lastDownloadedSequenceNumber = splitLine[splitLine.length - 1];
        this.lastDownloadedChunk = chunk;
        this.downloadedBytes += bytes;
    }
}
