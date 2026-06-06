package com.zenith.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * AOP Aspect that intercepts all methods annotated with @Idempotent.
 *
 * Transparently wraps controller methods — the controller code needs NO
 * changes, just add the @Idempotent annotation.
 *
 * Logic:
 *  1. Extract idempotencyKey from the first argument's JSON "idempotencyKey" field.
 *  2. Check IdempotencyService (Redis).
 *  3. If cached → return immediately.
 *  4. If not cached → call through, then cache the result.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper       objectMapper;

    @Around("@annotation(Idempotent)")
    public Object enforceIdempotency(ProceedingJoinPoint pjp) throws Throwable {

        // ── Extract idempotencyKey from first argument ──────────────────────
        String idempotencyKey = extractKey(pjp.getArgs());

        if (idempotencyKey == null) {
            log.debug("Idempotency: no key found, passing through");
            return pjp.proceed();
        }

        // ── Check cache ─────────────────────────────────────────────────────
        Optional<IdempotencyService.IdempotencyRecord> existing =
                idempotencyService.findExisting(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyService.IdempotencyRecord record = existing.get();
            log.info("Idempotency: replay key={} status={}", idempotencyKey, record.httpStatus());

            // Return the exact same ResponseEntity as the original response
            return ResponseEntity
                    .status(record.httpStatus())
                    .header("X-Idempotency-Replay", "true")
                    .body(record.responseBody());
        }

        // Try to acquire an execution lock for this idempotency key to prevent concurrent processing
        boolean lockAcquired = idempotencyService.acquireLock(idempotencyKey);
        if (!lockAcquired) {
            log.warn("Idempotency: concurrent request detected for key={}", idempotencyKey);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.CONFLICT)
                    .header("X-Idempotency-Error", "Concurrent request detected")
                    .body("A request with this idempotency key is already being processed.");
        }

        try {
            // ── Execute and cache ───────────────────────────────────────────────
            Object result = pjp.proceed();

            if (result instanceof ResponseEntity<?> responseEntity) {
                idempotencyService.store(
                        idempotencyKey,
                        responseEntity.getStatusCode().value(),
                        responseEntity.getBody()
                );
            }
            return result;
        } catch (Exception e) {
            // If the transaction fails, release the lock so it can be retried
            idempotencyService.releaseLock(idempotencyKey);
            throw e;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String extractKey(Object[] args) {
        if (args == null || args.length == 0) return null;
        for (Object arg : args) {
            if (arg == null) continue;
            // Check if argument has idempotencyKey field (e.g., TransactionRequest record)
            try {
                JsonNode node = objectMapper.valueToTree(arg);
                JsonNode keyNode = node.get("idempotencyKey");
                if (keyNode != null && !keyNode.isNull()) {
                    return keyNode.asText();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
