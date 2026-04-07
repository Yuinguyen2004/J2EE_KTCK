package com.billiard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * @desc Bootstraps the modular monolith backend so later beads can add feature
 * modules without revisiting the application entry point.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BilliardShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilliardShopApplication.class, args);
    }
}
