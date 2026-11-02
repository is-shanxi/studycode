package com.certpractice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point for the certification practice platform.
 */
@SpringBootApplication
@EnableScheduling
public class CertPracticeApplication {

    public static void main(final String[] args) {
        SpringApplication.run(CertPracticeApplication.class, args);
    }
}
