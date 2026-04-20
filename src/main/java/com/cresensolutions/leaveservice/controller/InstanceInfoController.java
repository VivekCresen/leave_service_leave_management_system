package com.cresensolutions.leaveservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Quick health/identity endpoint — hit GET /instance to see which node responded.
 */
@RestController
@RequestMapping("/instance")
public class InstanceInfoController {

    @Value("${spring.application.name:leave-service}")
    private String appName;

    @Value("${server.port:8082}")
    private String port;

    @GetMapping
    public Map<String, String> info() {
        return Map.of(
                "service", appName,
                "port",    port,
                "instanceId", appName + ":" + port
        );
    }
}
