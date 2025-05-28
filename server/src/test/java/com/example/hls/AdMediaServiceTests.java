package com.example.hls;

import com.example.hls.config.AdMediaConfiguration;
import com.example.hls.model.ad.AdResponse;
import com.example.hls.model.ad.SessionContext;
import com.example.hls.service.AdMediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AdMediaServiceTests {

    @Test
    void getAdReturnsResponse() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        AdMediaConfiguration config = new AdMediaConfiguration();
        ReflectionTestUtils.setField(config, "adProviderUrl", "http://ads");

        AdMediaService service = new AdMediaService(restTemplate, config, new ObjectMapper());

        AdResponse expected = new AdResponse();
        expected.setId("123");

        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(expected);

        server.expect(requestTo("http://ads?breakId=b1&zoneId=PREROLL&duration=30"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        AdResponse response = service.getAd("b1", 30L, true, new SessionContext("s1", "1.1.1.1"));

        assertEquals("123", response.getId());
        server.verify();
    }

    @Test
    void getAdHandlesError() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        AdMediaConfiguration config = new AdMediaConfiguration();
        ReflectionTestUtils.setField(config, "adProviderUrl", "http://ads");

        AdMediaService service = new AdMediaService(restTemplate, config, new ObjectMapper());

        server.expect(requestTo("http://ads?breakId=b1&zoneId=PREROLL&duration=30"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        AdResponse response = service.getAd("b1", 30L, true, new SessionContext("s1", "1.1.1.1"));

        assertNull(response.getId());
        assertTrue(response.getAdDetailsList().isEmpty());
        server.verify();
    }

    @Test
    void skipNextPosts() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        AdMediaConfiguration config = new AdMediaConfiguration();
        ReflectionTestUtils.setField(config, "adProviderUrl", "http://ads");

        AdMediaService service = new AdMediaService(restTemplate, config, new ObjectMapper());

        server.expect(requestTo("http://ads/skip-next"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        service.skipNext(new SessionContext("s1", "1.1.1.1"));
        server.verify();
    }
}
