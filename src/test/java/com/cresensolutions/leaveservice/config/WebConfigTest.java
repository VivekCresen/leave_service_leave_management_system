package com.cresensolutions.leaveservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WebConfigTest {

    @Test
    void addCorsMappings_registersLeavesAndHolidaysPaths() {
        WebConfig config = new WebConfig();
        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);

        when(registry.addMapping(anyString())).thenReturn(registration);
        when(registration.allowedOrigins(anyString())).thenReturn(registration);
        when(registration.allowedMethods(any(String[].class))).thenReturn(registration);
        when(registration.allowedHeaders(anyString())).thenReturn(registration);

        config.addCorsMappings(registry);

        verify(registry).addMapping("/api/leaves/**");
        verify(registry).addMapping("/api/holidays/**");
    }
}
