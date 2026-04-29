package com.zenith.gateway.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zenith.config.ZenithProperties;
import com.zenith.gateway.model.ApiKey;
import com.zenith.gateway.service.ApiKeyService;
import com.zenith.gateway.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Module 1 – The Gateway Interceptor
 *
 * Intercepts every incoming request and:
 *   1. Extracts X-Zenith-Key from the request header.
 *   2. Validates it (Redis cache → DB fallback).
 *   3. Enforces per-tier token-bucket rate limiting via Redis Lua script.
 *   4. Attaches the resolved ApiKey to the request for downstream use.
 *
 * Excluded paths: /actuator/**, /api/public/**
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyInterceptor implements HandlerInterceptor {

    public static final String API_KEY_ATTR = "zenith.apiKey";

    private final ZenithProperties  properties;
    private final ApiKeyService     apiKeyService;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper      objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String path = request.getRequestURI();

        // ── Skip public/health paths and CORS Preflight ─────────────────────
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || 
            path.startsWith("/actuator") || 
            path.startsWith("/api/public")) {
            return true;
        }

        // ── 1. Extract key header ───────────────────────────────────────────
        String rawKey = request.getHeader(properties.apiKey().header());
        Optional<ApiKey> resolved = apiKeyService.validate(rawKey);

        if (resolved.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED,
                    "INVALID_API_KEY",
                    "Missing or invalid " + properties.apiKey().header() + " header.");
            return false;
        }

        ApiKey apiKey = resolved.get();

        // ── 2. Rate limiting ────────────────────────────────────────────────
        // Use first 16 chars of key hash as client identifier (safe to log)
        String clientId = ApiKeyService.sha256Hex(rawKey != null ? rawKey : "").substring(0, 16);

        if (!rateLimiterService.isAllowed(clientId, apiKey.getTier())) {
            response.setHeader("X-RateLimit-Tier",  apiKey.getTier().name());
            response.setHeader("Retry-After",       "1");
            writeError(response, HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED",
                    "You have exceeded the rate limit for tier " + apiKey.getTier().name()
                            + ". Please slow down your requests.");
            return false;
        }

        // ── 3. Attach to request context ────────────────────────────────────
        request.setAttribute(API_KEY_ATTR, apiKey);
        response.setHeader("X-Zenith-Tier", apiKey.getTier().name());

        log.debug("Gateway: PASS path={} tier={} client={}", path, apiKey.getTier(), clientId);
        return true;
    }

    // ── Helper ─────────────────────────────────────────────────────────────
    private void writeError(HttpServletResponse response,
                            HttpStatus status,
                            String code,
                            String message) throws Exception {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "timestamp", Instant.now().toString(),
                "status",    status.value(),
                "error",     code,
                "message",   message
        ));
    }
}
