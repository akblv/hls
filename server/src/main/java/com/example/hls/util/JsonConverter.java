package com.example.hls.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class JsonConverter {
    private final ObjectMapper objectMapper;

    public JsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T convert(Object from, final Class<T> clazz) {
        try {
            return objectMapper.convertValue(from, clazz);
        } catch (Exception e) {
            throw new ConverterException(from, clazz);
        }
    }

    public <T> T deserializeJson(String val, final Class<T> clazz) {
        if (Objects.isNull(val)) {
            throw new ConverterException("null value", clazz);
        }
        try {
            return objectMapper.readValue(val, clazz);
        } catch (Exception e) {
            throw new ConverterException(val, clazz);
        }
    }

    public <T> T deserializeJson(String val, TypeReference<T> ref) {
        if (Objects.isNull(val)) {
            throw new ConverterException("null value", Object.class);
        }
        try {
            return objectMapper.readValue(val, ref);
        } catch (Exception e) {
            throw new ConverterException(val, Object.class);
        }
    }

    public String serializeJson(Object val) {
        if (Objects.isNull(val)) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(val);
        } catch (Exception e) {
            throw new ConverterException(val, String.class);
        }
    }

    public byte[] serializeBytes(Object val) {
        if (Objects.isNull(val)) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(val);
        } catch (Exception e) {
            throw new ConverterException(val, byte[].class);
        }
    }
}
