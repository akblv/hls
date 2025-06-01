package com.example.hls.util;

public class ConverterException extends RuntimeException {
    public ConverterException(Object value, Class<?> targetType) {
        super("Failed to convert " + value + " to " + targetType.getSimpleName());
    }
}
