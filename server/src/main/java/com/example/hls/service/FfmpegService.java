package com.example.hls.service;

import com.example.hls.model.NginxRtmpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service responsible for spawning ffmpeg to transcode RTMP streams into HLS.
 */
@Service
public class FfmpegService {
    private static final Logger logger = LoggerFactory.getLogger(FfmpegService.class);


    private final String outputPath;

    private final String ffmpegPath;

    public FfmpegService(@Value("${hls.output-path:live}") String outputPath,
                         @Value("${ffmpeg.path}") String ffmpegPath) {
        this.outputPath = outputPath;
        this.ffmpegPath = ffmpegPath;
    }


    public void startTranscoding(NginxRtmpRequest request) {
        startVideoTranscoding(request);
    }

    /**
     * Starts transcoding a video + audio stream into HLS.
     */
    public void startVideoTranscoding(NginxRtmpRequest request) {
        String inputUrl = request.tcurl() + "/" + request.name();
        String streamOutput = String.format("%s/%s", outputPath, request.name());
        List<String> cmd = buildCommand(inputUrl, streamOutput);
        runAsync(cmd);
    }

    /**
     * Starts transcoding an audio only stream into HLS.
     */
    public void startAudioTranscoding(NginxRtmpRequest request) {
        String inputUrl = request.tcurl() + "/" + request.name();
        String streamOutput = String.format("%s/%s", outputPath, request.name());
        List<String> cmd = buildAudioCommand(inputUrl, streamOutput);
        runAsync(cmd);
    }

    private List<String> buildCommand(String inputUrl, String streamPath) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(inputUrl);

        // 720p
        command.add("-map");
        command.add("0:v");
        command.add("-s:v:0");
        command.add("1280x720");
        command.add("-c:v:0");
        command.add("libx264");
        command.add("-b:v:0");
        command.add("2500k");
        command.add("-preset");
        command.add("veryfast");
        command.add("-g");
        command.add("48");
        command.add("-sc_threshold");
        command.add("0");
        command.add("-keyint_min");
        command.add("48");

        // 480p
        command.add("-map");
        command.add("0:v");
        command.add("-s:v:2");
        command.add("854x480");
        command.add("-c:v:2");
        command.add("libx264");
        command.add("-b:v:2");
        command.add("1400k");
        command.add("-preset");
        command.add("veryfast");
        command.add("-g");
        command.add("48");
        command.add("-sc_threshold");
        command.add("0");
        command.add("-keyint_min");
        command.add("48");

        // 360p
        command.add("-map");
        command.add("0:v");
        command.add("-s:v:1");
        command.add("640x360");
        command.add("-c:v:1");
        command.add("libx264");
        command.add("-b:v:1");
        command.add("800k");
        command.add("-preset");
        command.add("veryfast");
        command.add("-g");
        command.add("48");
        command.add("-sc_threshold");
        command.add("0");
        command.add("-keyint_min");
        command.add("48");

        command.add("-var_stream_map");
        command.add("v:0,name:720p v:1,name:360p v:2,name:480p");
        command.add("-master_pl_name");
        command.add("master.m3u8");
        command.add("-hls_segment_filename");
        command.add(String.format("%s/%%v/segment_%%03d.ts", streamPath));
        command.add("-f");
        command.add("hls");
        command.add("-hls_time");
        command.add("4");
        command.add("-hls_flags");
        command.add("delete_segments+append_list+independent_segments");
        command.add("-hls_list_size");
        command.add("8");
        command.add(String.format("%s/%%v/playlist.m3u8", streamPath));
        return command;
    }

    private List<String> buildAudioCommand(String inputUrl, String streamPath) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        // duplicate input for each quality
        command.add("-i");
        command.add(inputUrl);
        command.add("-i");
        command.add(inputUrl);
        command.add("-i");
        command.add(inputUrl);

        // high quality
        command.add("-map");
        command.add("0:a");
        command.add("-c:a:0");
        command.add("aac");
        command.add("-b:a:0");
        command.add("192k");

        // medium quality
        command.add("-map");
        command.add("1:a");
        command.add("-c:a:1");
        command.add("aac");
        command.add("-b:a:1");
        command.add("128k");

        // low quality
        command.add("-map");
        command.add("2:a");
        command.add("-c:a:2");
        command.add("aac");
        command.add("-b:a:2");
        command.add("64k");

        command.add("-var_stream_map");
        command.add("a:0,name:high a:1,name:medium a:2,name:low");
        command.add("-master_pl_name");
        command.add("master.m3u8");
        command.add("-hls_segment_filename");
        command.add(String.format("%s/%%v/segment_%%03d.aac", streamPath));
        command.add("-f");
        command.add("hls");
        command.add("-hls_time");
        command.add("4");
        command.add("-hls_flags");
        command.add("delete_segments+append_list+independent_segments");
        command.add("-hls_list_size");
        command.add("8");
        command.add(String.format("%s/%%v/playlist.m3u8", streamPath));
        return command;
    }

    private void runAsync(List<String> command) {
        CompletableFuture.runAsync(() -> {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            try {
                logger.info("Starting ffmpeg command: {}", String.join(" ", command));
                Process p = pb.start();
                int exit = p.waitFor();
                logger.info("ffmpeg exited with status {}", exit);
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("ffmpeg execution failed", e);
            }
        }, CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS));
    }
}
