package com.example.hls.model;

import org.springframework.util.MultiValueMap;

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

    public NginxRtmpRequest(MultiValueMap<String, String> params) {
        this(
                params.getFirst("app"),
                params.getFirst("flashver"),
                params.getFirst("swfurl"),
                params.getFirst("tcurl"),
                params.getFirst("pageurl"),
                params.getFirst("addr"),
                params.getFirst("clientid"),
                params.getFirst("call"),
                params.getFirst("name"),
                params.getFirst("type"));
    }
}
