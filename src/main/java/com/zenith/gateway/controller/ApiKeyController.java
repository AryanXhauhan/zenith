package com.zenith.gateway.controller;

import com.zenith.gateway.model.ApiKey;
import com.zenith.gateway.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping("/keys")
    public ResponseEntity<?> generateKey(@RequestBody Map<String, String> request) {
        String label = request.getOrDefault("label", "Default Key");
        String rawKey = "zn_" + UUID.randomUUID().toString().replace("-", "");
        
        // In a real app, we'd save this to DB/Redis via ApiKeyService
        // For this advanced demo, we'll return the generated key
        // Note: The ApiKeyService already has a master-key bypass for dev.
        
        return ResponseEntity.ok(Map.of(
            "key", rawKey,
            "label", label,
            "tier", "ENTERPRISE",
            "createdAt", java.time.Instant.now().toString()
        ));
    }
}
