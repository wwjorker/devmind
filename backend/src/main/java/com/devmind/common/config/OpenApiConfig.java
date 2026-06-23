package com.devmind.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI devMindOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DevMind API")
                        .version("v1")
                        .description("AI developer knowledge base backend API"));
    }
}
