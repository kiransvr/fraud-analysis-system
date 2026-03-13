package com.fraud.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MlServiceProperties.class)
public class HttpClientConfig {

    @Bean
    RestClient mlRestClient(RestClient.Builder builder, MlServiceProperties properties) {
        String baseUrl = properties.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8001";
        }
        return builder.baseUrl(baseUrl).build();
    }
}
