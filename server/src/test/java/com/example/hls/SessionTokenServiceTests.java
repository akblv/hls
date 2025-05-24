package com.example.hls;

import com.example.hls.service.session.SessionTokenService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionTokenServiceTests {

    @Test
    void tokenIsValidated() {
        SessionTokenService service = new SessionTokenService();
        String token = service.generateToken("stream1");
        assertTrue(service.isValid(token, "stream1"));
        assertFalse(service.isValid(token, "other"));
        assertFalse(service.isValid("bad", "stream1"));
    }
}
