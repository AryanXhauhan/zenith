package com.zenith.gateway.service;

import com.zenith.gateway.model.ApiKey;
import com.zenith.gateway.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Validates API keys with a two-level lookup:
 *  1. Redis cache  → microsecond latency  (TTL: 5 min)
 *  2. PostgreSQL   → fallback on cache miss, then re-populates cache
 *
 * Keys are stored only as SHA-256 hashes – we NEVER persist the raw key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String CACHE_PREFIX  = "zenith:apikey:";
    private static final Duration CACHE_TTL   = Duration.ofMinutes(5);
    private static final String INVALID_MARKER = "INVALID";

    private final ApiKeyRepository apiKeyRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.zenith.config.ZenithProperties properties;

    /**
     * Validates the raw API key supplied in the request header.
     *
     * @param rawKey the value from X-Zenith-Key header
     * @return the resolved ApiKey, or empty if invalid / expired
     */
    public Optional<ApiKey> validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();

        // ── 0. Master Key Bypass (Dev/Master access) ────────────────────────
        if (rawKey.equals(properties.apiKey().masterKey())) {
            return Optional.of(ApiKey.builder()
                    .name("Master System Key")
                    .tier(ApiKey.Tier.ENTERPRISE)
                    .isActive(true)
                    .build());
        }

        String hash    = sha256Hex(rawKey);
        String cacheKey = CACHE_PREFIX + hash;

        // ── 1. Cache hit ────────────────────────────────────────────────────
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (INVALID_MARKER.equals(cached)) {
                log.debug("API key cache: known-invalid key hash={}", hash.substring(0, 8));
                return Optional.empty();
            }
            if (cached instanceof ApiKey cachedKey) {
                log.debug("API key cache: HIT tier={}", cachedKey.getTier());
                return Optional.of(cachedKey);
            }
        }

        // ── 2. DB lookup ────────────────────────────────────────────────────
        Optional<ApiKey> found = apiKeyRepository.findByKeyHashAndIsActiveTrue(hash);

        if (found.isEmpty()) {
            // Cache the negative result to prevent DB hammering
            redisTemplate.opsForValue().set(cacheKey, INVALID_MARKER, Duration.ofSeconds(30));
            log.warn("API key validation failed for hash prefix={}", hash.substring(0, 8));
            return Optional.empty();
        }

        ApiKey apiKey = found.get();

        // Check expiry
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(Instant.now())) {
            redisTemplate.opsForValue().set(cacheKey, INVALID_MARKER, Duration.ofSeconds(30));
            log.warn("API key expired: name={}", apiKey.getName());
            return Optional.empty();
        }

        // Populate cache
        redisTemplate.opsForValue().set(cacheKey, apiKey, CACHE_TTL);
        log.debug("API key cache: MISS – loaded from DB tier={}", apiKey.getTier());
        return Optional.of(apiKey);
    }

    /** Computes SHA-256 hex of the raw key. */
    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
