package com.gehu.sfs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SfsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SfsApplication.class, args);
    }
}
