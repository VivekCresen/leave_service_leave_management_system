package com.cresensolutions.leaveservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void leaveServiceOpenApi_returnsConfiguredBean() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.leaveServiceOpenApi();

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Leave Service API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openAPI.getInfo().getContact().getName()).isEqualTo("Cresen Solutions");
    }
}
