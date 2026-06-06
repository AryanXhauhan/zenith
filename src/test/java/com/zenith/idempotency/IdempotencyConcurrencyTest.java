package com.zenith.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zenith.config.ZenithProperties;
import com.zenith.idempotency.IdempotencyService.IdempotencyRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class IdempotencyConcurrencyTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ZenithProperties properties;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private final ConcurrentHashMap<String, String> mockRedisStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(anyString(), eq("LOCKED"), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return mockRedisStore.putIfAbsent(key, "LOCKED") == null;
        });
    }

    @Test
    @DisplayName("IdempotencyService: acquireLock allows only one concurrent request")
    void acquireLock_concurrentRequests_onlyOneSucceeds() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        String sharedKey = UUID.randomUUID().toString();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await(); // wait until all threads are ready
                    boolean acquired = idempotencyService.acquireLock(sharedKey);
                    if (acquired) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Release all threads simultaneously
        latch.countDown();
        Thread.sleep(500); // Wait for threads to finish execution

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
    }
}
