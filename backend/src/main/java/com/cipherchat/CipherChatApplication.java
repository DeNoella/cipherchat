package com.cipherchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CipherChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(CipherChatApplication.class, args);
    }
}
