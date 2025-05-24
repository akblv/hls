package com.example.hls.service.session;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionTokenService {
    private static class TokenInfo {
        final String mount;
        final Instant expires;

        TokenInfo(String mount, Instant expires) {
            this.mount = mount;
            this.expires = expires;
        }
    }

    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();
    private static final long EXPIRATION_SECONDS = 3600; // 1 hour

    public String generateToken(String mount) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new TokenInfo(mount, Instant.now().plusSeconds(EXPIRATION_SECONDS)));
        return token;
    }

    public boolean isValid(String token, String mount) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        TokenInfo info = tokens.get(token);
        if (info == null) {
            return false;
        }
        if (!info.mount.equals(mount)) {
            return false;
        }
        if (info.expires.isBefore(Instant.now())) {
            tokens.remove(token);
            return false;
        }
        return true;
    }
}
