package com.example.hls.model;

import java.util.Map;

/**
 * Representation of the parameters passed by nginx-rtmp module hooks.
 */
public record NginxRtmpRequest(
        String app,
        String flashver,
        String swfurl,
        String tcurl,
        String pageurl,
        String addr,
        String clientid,
        String call,
        String name,
        String type) {

    public NginxRtmpRequest(Map<String, String> params) {
        this(
                params.getOrDefault("app", ""),
                params.getOrDefault("flashver", ""),
                params.getOrDefault("swfurl", ""),
                params.getOrDefault("tcurl", ""),
                params.getOrDefault("pageurl", ""),
                params.getOrDefault("addr", ""),
                params.getOrDefault("clientid", ""),
                params.getOrDefault("call", ""),
                params.getOrDefault("name", ""),
                params.getOrDefault("type", ""));
    }
}
