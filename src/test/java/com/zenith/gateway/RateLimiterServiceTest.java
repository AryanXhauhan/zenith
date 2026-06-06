package com.zenith.gateway;

import com.zenith.config.ZenithProperties;
import com.zenith.gateway.model.ApiKey;
import com.zenith.gateway.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZenithProperties properties;

    @InjectMocks
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        ZenithProperties.RateLimitConfig rateLimitConfig = new ZenithProperties.RateLimitConfig(
                new ZenithProperties.TierConfig(5, 10),     // FREE
                new ZenithProperties.TierConfig(100, 200),  // PRO
                new ZenithProperties.TierConfig(1000, 2000) // ENTERPRISE
        );
        when(properties.rateLimit()).thenReturn(rateLimitConfig);
    }

    @Test
    @DisplayName("RateLimiter: Allows request when Lua script returns 1L")
    void isAllowed_returnsTrue_whenLuaReturns1() {
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(1L);

        boolean allowed = rateLimiterService.isAllowed("client-1", ApiKey.Tier.FREE);

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("RateLimiter: Blocks request when Lua script returns 0L")
    void isAllowed_returnsFalse_whenLuaReturns0() {
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(0L);

        boolean allowed = rateLimiterService.isAllowed("client-2", ApiKey.Tier.PRO);

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("RateLimiter: Blocks request when Lua script returns null (fallback safety)")
    void isAllowed_returnsFalse_whenLuaReturnsNull() {
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(null);

        boolean allowed = rateLimiterService.isAllowed("client-3", ApiKey.Tier.ENTERPRISE);

        assertThat(allowed).isFalse();
    }
}
