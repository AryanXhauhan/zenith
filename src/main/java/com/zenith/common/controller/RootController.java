package com.zenith.common.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, String> index() {
        return Map.of(
            "status", "UP",
            "service", "Zenith Settlement Engine",
            "version", "1.0.0-PROD",
            "environment", "Docker/Cloud-Native"
        );
    }
}
