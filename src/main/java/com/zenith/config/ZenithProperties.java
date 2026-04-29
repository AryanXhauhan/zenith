package com.zenith.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for all zenith.* properties in application.yml
 */
@ConfigurationProperties(prefix = "zenith")
public record ZenithProperties(
        ApiKeyConfig apiKey,
        RateLimitConfig rateLimit,
        IdempotencyConfig idempotency,
        Web3Config web3
) {

    public record ApiKeyConfig(
            String header,
            String masterKey
    ) {}

    public record RateLimitConfig(
            TierConfig free,
            TierConfig pro,
            TierConfig enterprise
    ) {}

    public record TierConfig(
            int rps,
            int burst
    ) {}

    public record IdempotencyConfig(
            long ttlSeconds
    ) {}

    public record Web3Config(
            String rpcUrl,
            String privateKey,
            String escrowContractAddress,
            long chainId
    ) {}
}
