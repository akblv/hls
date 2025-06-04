package com.example.hls.service.session;

import com.zenomedia.session.common.SessionTokenManager;
import com.zenomedia.session.common.model.SessionTokenParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionTokenService {
    private static final Logger logger = LoggerFactory.getLogger(SessionTokenService.class);

    private final SessionTokenManager sessionTokenManager;
    private final Set<String> serverHostNames;

    @Autowired
    public SessionTokenService(final SessionTokenManager sessionTokenManager,
                               final Set<String> serverHostNames) {
        this.sessionTokenManager = sessionTokenManager;
        this.serverHostNames = serverHostNames;
    }

    public boolean isValid(final String token, final String stream) {
        if (!StringUtils.hasText(token)) {
            return false;
        }

        long now = System.currentTimeMillis();
        final SessionTokenParams params = sessionTokenManager.getParams(token);
        logger.info("Params {}", params);
        logger.info("Expired: {}", params.getExpiration() > now);
        logger.info("Stream equals {}", Objects.equals(stream, params.getStream()));
        logger.info("Hostnames contains {}", serverHostNames.contains(params.getHost()));


        return true;
    }
}
