package com.example.hls.config;

import com.zenomedia.session.common.SessionTokenManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
class ApplicationConfiguration {

    @Bean
    SessionTokenManager sessionTokenManager(@Value("${server.session.token.secret:}") final String secret) {
        return new SessionTokenManager(secret);
    }

    @Bean
    Set<String> serverHostNames(@Value("${server.hostnames}") final String hostnames) {
        return Arrays.stream(hostnames.split(",")).map(String::trim).filter(StringUtils::hasText).collect(Collectors.toUnmodifiableSet());
    }
}
