package com.simgan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SimganDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimganDemoApplication.class, args);
    }
}          
