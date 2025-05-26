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

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        when(hlsService.getPlaylist(anyString(), anyString(), anyString())).thenReturn("playlist");
        when(tokenService.generateToken(anyString())).thenReturn("newtoken");
        when(tokenService.isValid(isNull(), anyString())).thenReturn(false);
        when(tokenService.isValid(anyString(), anyString())).thenReturn(false);
        when(tokenService.isValid(eq("validtoken"), anyString())).thenReturn(true);
    }

    @Test
    void redirectsWhenTokenMissing() throws Exception {
        mockMvc.perform(get("/hls/foo.m3u8"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", startsWith("http://localhost/hls/foo.m3u8?zt=")));
    }

    @Test
    void redirectsWhenTokenInvalid() throws Exception {
        mockMvc.perform(get("/hls/foo.m3u8").param("zt", "bad"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", startsWith("http://localhost/hls/foo.m3u8?zt=")));
    }

    @Test
    void returnsPlaylistWhenTokenValid() throws Exception {
        mockMvc.perform(get("/hls/foo.m3u8").param("zt", "validtoken"))
                .andExpect(status().isOk())
                .andExpect(content().string("playlist"));
    }
}
