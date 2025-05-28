package com.example.hls.model;

/**
 * Simple holder for cached values.
 */
public record CacheEntry<T>(T value, long timestamp) {
}
