package com.cresensolutions.leaveservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI leaveServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Leave Service API")
                        .description("API documentation for leave request and leave management endpoints.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Cresen Solutions")
                                .email("support@cresensolutions.com")));
    }
}
