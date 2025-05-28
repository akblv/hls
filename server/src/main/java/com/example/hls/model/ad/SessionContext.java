package com.example.hls.model.ad;

public class SessionContext {
    private String id;
    private String clientIp;

    public SessionContext() {
    }

    public SessionContext(String id, String clientIp) {
        this.id = id;
        this.clientIp = clientIp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
}
