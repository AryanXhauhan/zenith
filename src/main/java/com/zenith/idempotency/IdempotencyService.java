package com.zenith.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zenith.config.ZenithProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Module 3 – Idempotency Service
 *
 * Prevents duplicate transactions when clients retry requests.
 *
 * Storage: Redis with a configurable TTL (default 24 hours).
 * Key format: zenith:idempotency:<idempotencyKey>
 *
 * Flow:
 *  ┌─────────────────────────────────────────────────────┐
 *  │  Client sends request with idempotencyKey = "abc"   │
 *  │                                                      │
 *  │  1. Check Redis for "zenith:idempotency:abc"        │
 *  │     → HIT  : return cached response (no DB write)   │
 *  │     → MISS : execute business logic, cache result   │
 *  └─────────────────────────────────────────────────────┘
 *
 * This guarantees:
 *   - "Pay" button pressed twice → only ONE debit from account
 *   - Network retry → same idempotent response returned
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "zenith:idempotency:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ZenithProperties              properties;
    private final ObjectMapper                  objectMapper;

    private static final String LOCK_PREFIX = "zenith:idempotency:lock:";
    private static final Duration LOCK_TTL  = Duration.ofMinutes(1);

    /**
     * Attempts to acquire an execution lock for the idempotency key.
     *
     * @param idempotencyKey the deduplication key
     * @return true if lock was acquired, false if another request is processing it
     */
    public boolean acquireLock(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return false;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + idempotencyKey, "LOCKED", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Releases the execution lock for the idempotency key, allowing retries.
     *
     * @param idempotencyKey the deduplication key
     */
    public void releaseLock(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        redisTemplate.delete(LOCK_PREFIX + idempotencyKey);
    }

    /**
     * Looks up a previously cached response for the given idempotency key.
     *
     * @param idempotencyKey the client-supplied deduplication key
     * @return cached IdempotencyRecord if this key was already processed
     */
    public Optional<IdempotencyRecord> findExisting(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();

        Object cached = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        if (cached == null) return Optional.empty();

        try {
            IdempotencyRecord record = objectMapper.convertValue(cached, IdempotencyRecord.class);
            log.info("Idempotency: HIT key={} – returning cached response", idempotencyKey);
            return Optional.of(record);
        } catch (Exception e) {
            log.warn("Idempotency: failed to deserialize cached record key={}", idempotencyKey, e);
            return Optional.empty();
        }
    }

    /**
     * Stores the response for a completed request so future retries receive it.
     *
     * @param idempotencyKey the deduplication key
     * @param httpStatus     the HTTP status of the original response
     * @param responseBody   the serialized JSON response body
     */
    public void store(String idempotencyKey, int httpStatus, Object responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;

        IdempotencyRecord record = new IdempotencyRecord(idempotencyKey, httpStatus, responseBody);
        Duration ttl = Duration.ofSeconds(properties.idempotency().ttlSeconds());

        redisTemplate.opsForValue().set(KEY_PREFIX + idempotencyKey, record, ttl);
        log.debug("Idempotency: STORED key={} ttl={}s", idempotencyKey, ttl.getSeconds());
    }

    public record IdempotencyRecord(
            String idempotencyKey,
            int    httpStatus,
            Object responseBody
    ) {}
}
