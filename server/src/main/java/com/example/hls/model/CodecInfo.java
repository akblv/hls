package com.example.hls.model;

import java.util.Optional;

public record CodecInfo(String sampleRate, String bitRate, String codec, String channels, Optional<Double> duration, Optional<String> profile) {
}
