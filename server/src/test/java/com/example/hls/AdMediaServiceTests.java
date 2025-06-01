package com.example.hls;

import com.example.hls.config.AdMediaConfiguration;
import com.example.hls.model.ad.AdResponse;
import com.example.hls.model.ad.SessionContext;
import com.example.hls.service.AdMediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.hls.util.JsonConverter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.junit.jupiter.api.Assertions.*;

class AdMediaServiceTests {

    @Test
    void getAdReturnsResponse() throws Exception {
        AdMediaConfiguration config = new AdMediaConfiguration();

        AdResponse expected = new AdResponse();
        expected.setId("123");

        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(expected);

        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((req, res) -> res.status(200)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .sendString(Mono.just(body)))
                .bindNow();

        ReflectionTestUtils.setField(config, "adProviderUrl", "http://localhost:" + server.port());

        WebClient client = WebClient.builder().baseUrl("http://localhost:" + server.port()).build();
        AdMediaService service = new AdMediaService(client, config, new JsonConverter(new ObjectMapper()));

        AdResponse response = service.getAd("b1", 30L, true, new SessionContext("s1", "1.1.1.1"));

        assertEquals("123", response.getId());
        server.disposeNow();
    }

    @Test
    void getAdHandlesError() {
        AdMediaConfiguration config = new AdMediaConfiguration();

        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((req, res) -> res.status(500).send())
                .bindNow();

        ReflectionTestUtils.setField(config, "adProviderUrl", "http://localhost:" + server.port());

        WebClient client = WebClient.builder().baseUrl("http://localhost:" + server.port()).build();
        AdMediaService service = new AdMediaService(client, config, new JsonConverter(new ObjectMapper()));

        AdResponse response = service.getAd("b1", 30L, true, new SessionContext("s1", "1.1.1.1"));

        assertNull(response.getId());
        assertTrue(response.getAdDetailsList().isEmpty());
        server.disposeNow();
    }

    @Test
    void skipNextPosts() {
        AdMediaConfiguration config = new AdMediaConfiguration();

        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((req, res) -> res.status(200).send())
                .bindNow();

        ReflectionTestUtils.setField(config, "adProviderUrl", "http://localhost:" + server.port());

        WebClient client = WebClient.builder().baseUrl("http://localhost:" + server.port()).build();
        AdMediaService service = new AdMediaService(client, config, new JsonConverter(new ObjectMapper()));

        service.skipNext(new SessionContext("s1", "1.1.1.1"));
        server.disposeNow();
    }
}
