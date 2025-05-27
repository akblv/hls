package com.example.hls;

import com.example.hls.controller.HlsController;
import com.example.hls.service.HlsService;
import com.example.hls.service.FfmpegService;
import com.example.hls.service.session.SessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HlsController.class)
class HlsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HlsService hlsService;

    @MockBean
    private SessionTokenService tokenService;

    @MockBean
    private FfmpegService ffmpegService;

    @BeforeEach
    void setup() {
        when(hlsService.getPlaylist(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("playlist");
    }

    @Test
    void returnsPlaylist() throws Exception {
        mockMvc.perform(get("/live/stream/foo/playlist.m3u8"))
                .andExpect(status().isOk())
                .andExpect(content().string("playlist"));
    }

    @Test
    void returnsPlaylistWithQuality() throws Exception {
        mockMvc.perform(get("/live/stream/foo/720p/playlist.m3u8"))
                .andExpect(status().isOk())
                .andExpect(content().string("playlist"));
    }

    @Test
    void rejectsInvalidStreamOnValidate() throws Exception {
        mockMvc.perform(post("/live/stream/validate").param("name", "bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsValidStreamOnValidate() throws Exception {
        mockMvc.perform(post("/live/stream/validate").param("name", "abcdef12345tv"))
                .andExpect(status().isOk());
    }
}
