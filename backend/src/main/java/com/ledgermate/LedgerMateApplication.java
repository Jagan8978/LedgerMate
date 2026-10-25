package com.ledgermate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * LedgerMate entry point. The default in-memory user (with a generated password) is excluded
 * because authentication is implemented with our own users table and JWT cookies.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class LedgerMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerMateApplication.class, args);
    }
}
