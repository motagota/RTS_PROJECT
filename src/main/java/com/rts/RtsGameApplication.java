package com.rts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RtsGameApplication {
    public static void main(String[] args) {
        SpringApplication.run(RtsGameApplication.class, args);
    }
}