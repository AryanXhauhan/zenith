package com.zenith.gateway;

import com.zenith.gateway.model.ApiKey;
import com.zenith.gateway.service.ApiKeyService;
import com.zenith.gateway.service.RateLimiterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    com.zenith.gateway.repository.ApiKeyRepository apiKeyRepository;

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    @Mock
    ValueOperations<String, Object> valueOps;

    @Mock
    com.zenith.config.ZenithProperties properties;

    @Mock
    com.zenith.config.ZenithProperties.ApiKeyConfig apiKeyConfig;

    @InjectMocks
    ApiKeyService apiKeyService;

    private void setupMocks() {
        lenient().when(properties.apiKey()).thenReturn(apiKeyConfig);
        lenient().when(apiKeyConfig.masterKey()).thenReturn("master-key");
    }

    @Test
    @DisplayName("Master Key bypass returns Enterprise key")
    void validate_masterKey_returnsEnterprise() {
        setupMocks();
        Optional<ApiKey> result = apiKeyService.validate("master-key");
        assertThat(result).isPresent();
        assertThat(result.get().getTier()).isEqualTo(ApiKey.Tier.ENTERPRISE);
    }
    @DisplayName("Null key returns empty")
    void validate_null_returnsEmpty() {
        assertThat(apiKeyService.validate(null)).isEmpty();
    }

    @Test
    @DisplayName("Blank key returns empty")
    void validate_blank_returnsEmpty() {
        assertThat(apiKeyService.validate("   ")).isEmpty();
    }

    @Test
    @DisplayName("Cache HIT returns ApiKey without DB call")
    void validate_cacheHit_returnsCachedKey() {
        setupMocks();
        ApiKey cached = ApiKey.builder()
                .name("test-key")
                .tier(ApiKey.Tier.PRO)
                .isActive(true)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(cached);

        Optional<ApiKey> result = apiKeyService.validate("some-raw-key");

        assertThat(result).isPresent();
        assertThat(result.get().getTier()).isEqualTo(ApiKey.Tier.PRO);
        verifyNoInteractions(apiKeyRepository);     // DB never hit
    }

    @Test
    @DisplayName("Cache MISS + DB hit → key returned + cached")
    void validate_cacheMiss_dbHit_returnsCachedAndStored() {
        setupMocks();
        ApiKey dbKey = ApiKey.builder()
                .name("db-key")
                .tier(ApiKey.Tier.FREE)
                .isActive(true)
                .keyHash(ApiKeyService.sha256Hex("raw-key"))
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);        // Cache miss
        when(apiKeyRepository.findByKeyHashAndIsActiveTrue(anyString()))
                .thenReturn(Optional.of(dbKey));

        Optional<ApiKey> result = apiKeyService.validate("raw-key");

        assertThat(result).isPresent();
        assertThat(result.get().getTier()).isEqualTo(ApiKey.Tier.FREE);
        verify(valueOps).set(anyString(), eq(dbKey), any());    // Stored in cache
    }

    @Test
    @DisplayName("sha256Hex is deterministic")
    void sha256Hex_isDeterministic() {
        String hash1 = ApiKeyService.sha256Hex("hello");
        String hash2 = ApiKeyService.sha256Hex("hello");
        assertThat(hash1).isEqualTo(hash2).hasSize(64);
    }
}
