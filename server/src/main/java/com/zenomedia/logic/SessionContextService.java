package com.zenomedia.logic;

import com.zenomedia.Sys;
import com.zenomedia.common.model.events.session.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import com.example.hls.util.JsonConverter;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

@Service
public class SessionContextService {
    private static final Logger logger = LoggerFactory.getLogger(SessionContextService.class);

    private final WebClient webClient;
    private volatile String sessionContextUrl;
    private final long timeoutMillis;
    private final Counter errorsCounter;
    private final JsonConverter converter;
    private final Map<String, String> upstreamsCache = new ConcurrentHashMap<>();

    public SessionContextService(WebClient webClient,
                                 MeterRegistry meterRegistry,
                                 JsonConverter converter,
                                 @Value("${sessioncontext.url}") String sessionContextUrl,
                                 @Value("${sessioncontext.requests.timeout:5000}") long timeoutMillis) {
        this.webClient = webClient;
        this.converter = converter;
        this.sessionContextUrl = sessionContextUrl;
        this.timeoutMillis = timeoutMillis;
        this.errorsCounter = meterRegistry.counter("stream.session.context.errors");
    }

    /**
     * Update the session context service URL at runtime.
     */
    public void setSessionContextUrl(String sessionContextUrl) {
        this.sessionContextUrl = sessionContextUrl;
    }

    public Mono<SessionContext> requestEnhancedSessionContext(ServerHttpRequest request,
                                                              String stream,
                                                              String sessionId) {
        logger.debug("Requesting enhanced session context");
        SessionContext sessionContext = getSessionContext(request, stream, sessionId);
        String query = request.getURI().getQuery();
        String urlWithQuery = (Objects.nonNull(query) && !query.isEmpty()) ? sessionContextUrl + "?" + query : sessionContextUrl;

        return webClient.post()
                .uri(urlWithQuery)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-session-id", sessionContext.getId())
                .bodyValue(sessionContext)
                .exchangeToMono(resp -> handleResponse(resp, sessionContext, stream))
                .timeout(Duration.ofMillis(timeoutMillis))
                .doOnError(err -> {
                    logger.error(String.format("Failed to get listener context from: %s, IP: %s, for stream: %s",
                            sessionContextUrl, sessionContext.getConnection().getClientIp(), stream), err);
                    errorsCounter.increment();
                })
                .onErrorReturn(sessionContext);
    }

    private Mono<SessionContext> handleResponse(ClientResponse response, SessionContext sessionContext, String stream) {
        if (response.statusCode().equals(HttpStatus.OK)) {
            return response.bodyToMono(String.class)
                    .map(body -> {
                        logger.debug(body);
                        try {
                            SessionContext enhanced = converter.deserializeJson(body, SessionContext.class);
                            if (Objects.nonNull(enhanced.getContent())) {
                                Optional<String> upstream = enhanced.getContent().getUpstream();
                                if (Objects.nonNull(upstream) && upstream.isPresent()) {
                                    upstreamsCache.put(stream, upstream.get());
                                }
                            }
                            return enhanced;
                        } catch (Exception e) {
                            logger.warn("Failed to parse enhanced session context", e);
                            return sessionContext;
                        }
                    });
        } else {
            return response.bodyToMono(String.class).defaultIfEmpty("")
                    .map(body -> {
                        logger.error(String.format("Failed to get listener context from: %s, for stream: %s, IP: %s, response: %d %s %s",
                                sessionContextUrl, stream, sessionContext.getConnection().getClientIp(),
                                response.rawStatusCode(), response.statusCode().getReasonPhrase(), body));
                        errorsCounter.increment();
                        return sessionContext;
                    });
        }
    }

    private SessionContext getSessionContext(ServerHttpRequest request, String stream, String sessionId) {
        String ipOverride = Sys.conf.hasPath("local.external.ip") ? Sys.conf.getString("local.external.ip") : null;

        InetSocketAddress local = request.getLocalAddress();
        InetSocketAddress remote = request.getRemoteAddress();

        RequestHeaders requestHeaders = RequestHeaders.builder()
                .xListenerId(request.getHeaders().getFirst("X-Listener-Id"))
                .userAgent(request.getHeaders().getFirst(org.springframework.http.HttpHeaders.USER_AGENT))
                .language(request.getHeaders().getFirst(org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE))
                .referer(request.getHeaders().getFirst(org.springframework.http.HttpHeaders.REFERER))
                .build();

        RequestInfo requestInfo = RequestInfo.builder()
                .method(request.getMethodValue())
                .uri(request.getURI().toString())
                .headers(request.getHeaders().toSingleValueMap())
                .build();

        Connection listenerConnection = Connection.builder()
                .connected(System.currentTimeMillis())
                .domain(request.getURI().getHost())
                .serverIp(Objects.nonNull(ipOverride) ? ipOverride : (Objects.nonNull(local) ? local.getHostString() : null))
                .clientIp(Objects.nonNull(ipOverride) ? ipOverride : (Objects.nonNull(remote) ? remote.getHostString() : null))
                .ssl("https".equalsIgnoreCase(request.getURI().getScheme()))
                .hls(request.getURI().getPath().startsWith("/hls/"))
                .headers(requestHeaders)
                .request(requestInfo)
                .build();

        Map<String, String> params = request.getQueryParams().toSingleValueMap();
        ApplicationInfo applicationInfo = ApplicationInfo.builder()
                .advertisingId(params.get("advertisingId"))
                .userId(params.get("userId"))
                .gdpr(params.get("gdpr"))
                .gdprConsent(params.get("gdpr_consent"))
                .dnt(params.get("dnt"))
                .lsid(params.get("lsid"))
                .bundleId(params.get(params.containsKey("bundle-id") ? "bundle-id" : "bundleId"))
                .storeId(params.get(params.containsKey("store-id") ? "store-id" : "storeId"))
                .storeUrl(params.get(params.containsKey("store-url") ? "store-url" : "storeUrl"))
                .name(params.get(params.containsKey("dist") ? "dist" : "DIST"))
                .build();

        Content.Builder contentBuilder = Content.builder().stream(stream);
        String upstream = upstreamsCache.get(stream);
        if (Objects.nonNull(upstream)) {
            contentBuilder.upstream(upstream);
        }

        return SessionContext.builder()
                .id(sessionId)
                .connection(listenerConnection)
                .listener(Listener.builder().application(applicationInfo).build())
                .content(contentBuilder.build())
                .build();
    }
}
