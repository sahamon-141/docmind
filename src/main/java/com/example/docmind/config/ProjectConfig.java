package com.example.docmind.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectConfig {
    @Bean
    public OpenAPI openAPI(){
        return new OpenAPI().info(
                new Info()
                        .title("DocMind")
                        .description("REST API for DocMind")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Sahajit")
                                .email("sahajitmondal07@gmail.com")
                        )
        );
    }
}
