package com.example.hls;

import com.example.hls.controller.HlsController;
import com.example.hls.service.HlsService;
import com.example.hls.service.FfmpegService;
import com.example.hls.service.session.SessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebFluxTest(HlsController.class)
class HlsControllerTests {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private HlsService hlsService;

    @MockBean
    private SessionTokenService tokenService;

    @MockBean
    private FfmpegService ffmpegService;

    @BeforeEach
    void setup() {
        when(hlsService.getPlaylist(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just("playlist"));
    }

    @Test
    void returnsPlaylist() {
        webClient.get().uri("/live/stream/foo/playlist.m3u8")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("playlist");
    }

    @Test
    void returnsPlaylistWithQuality() {
        webClient.get().uri("/live/stream/foo/720p/playlist.m3u8")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("playlist");
    }

    @Test
    void rejectsInvalidStreamOnValidate() {
        webClient.post().uri(uriBuilder -> uriBuilder.path("/live/stream/validate").queryParam("name", "bad").build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void acceptsValidStreamOnValidate() {
        webClient.post().uri(uriBuilder -> uriBuilder.path("/live/stream/validate").queryParam("name", "abcdef12345tv").build())
                .exchange()
                .expectStatus().isOk();
    }
}
