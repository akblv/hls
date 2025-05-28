package com.example.hls;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HlsServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(HlsServerApplication.class, args);
    }
}
