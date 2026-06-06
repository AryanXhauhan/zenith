package com.zenith.gateway.service;

import com.zenith.config.ZenithProperties;
import com.zenith.gateway.model.ApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Token-Bucket Rate Limiter backed by a Redis Lua script.
 *
 * Algorithm: Token Bucket
 *  - Each client has a "bucket" in Redis.
 *  - Every second, the bucket refills up to `rps` tokens.
 *  - A request costs 1 token; if the bucket is empty → 429.
 *
 * The Lua script is ATOMIC – no race conditions possible.
 *
 * Redis key format: zenith:ratelimit:<tier>:<clientId>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final String KEY_PREFIX = "zenith:ratelimit:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ZenithProperties properties;

    /**
     * Atomic Lua token-bucket script.
     * KEYS[1] = bucket key
     * ARGV[1] = max tokens (burst capacity)
     * ARGV[2] = refill rate (tokens per second)
     * ARGV[3] = current time in milliseconds
     * ARGV[4] = cost (always 1)
     *
     * Returns: 1 → allowed, 0 → rate-limited
     */
    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT;
    static {
        TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>();
        TOKEN_BUCKET_SCRIPT.setResultType(Long.class);
        TOKEN_BUCKET_SCRIPT.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
    }

    /**
     * Checks whether the given client is within its rate limit.
     *
     * @param clientId unique identifier for the caller (e.g., API key hash prefix)
     * @param tier     the billing tier of the client
     * @return true if the request should proceed, false if it should be rejected (429)
     */
    public boolean isAllowed(String clientId, ApiKey.Tier tier) {
        ZenithProperties.TierConfig config = getTierConfig(tier);
        String bucketKey = KEY_PREFIX + tier.name().toLowerCase() + ":" + clientId;
        long now = System.currentTimeMillis();

        Long result = stringRedisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                List.of(bucketKey),
                String.valueOf(config.burst()),
                String.valueOf(config.rps()),
                String.valueOf(now),
                "1"
        );

        boolean allowed = result != null && result == 1L;
        if (!allowed) {
            log.warn("Rate limit exceeded: clientId={} tier={}", clientId, tier);
        }
        return allowed;
    }

    private ZenithProperties.TierConfig getTierConfig(ApiKey.Tier tier) {
        switch (tier) {
            case FREE:
                return properties.rateLimit().free();
            case PRO:
                return properties.rateLimit().pro();
            case ENTERPRISE:
                return properties.rateLimit().enterprise();
            default:
                return properties.rateLimit().free();
        }
    }
}
