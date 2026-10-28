package com.example.mini_nanofaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@EnableRetry
public class MiniNanofaasApplication {

    static void main(String[] args) {
        SpringApplication.run(MiniNanofaasApplication.class, args);
    }

    // Per creare il function dispatcher ho bisogno di creare fin da subito un RestClient builder
    // è un oggetto generico, che poi io specializzo
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
