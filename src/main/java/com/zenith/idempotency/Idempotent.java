package com.zenith.idempotency;

import java.lang.annotation.*;

/**
 * Marks a controller method as idempotent.
 *
 * When applied, IdempotencyAspect will:
 *  1. Extract the idempotencyKey from the request body's "idempotencyKey" field.
 *  2. Check Redis for a cached response.
 *  3. If found → return the cached response immediately (no DB hit).
 *  4. If not found → let the method execute, then cache its response.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    // Marker annotation – no parameters needed
}
