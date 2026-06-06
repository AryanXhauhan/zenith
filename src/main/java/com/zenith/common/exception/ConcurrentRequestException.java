package com.zenith.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an identical idempotency key is already being processed concurrently.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConcurrentRequestException extends RuntimeException {
    public ConcurrentRequestException(String message) {
        super(message);
    }
}
