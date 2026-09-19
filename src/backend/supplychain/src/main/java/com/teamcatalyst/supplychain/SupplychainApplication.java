package com.teamcatalyst.supplychain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SupplychainApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplychainApplication.class, args);
    }

}
