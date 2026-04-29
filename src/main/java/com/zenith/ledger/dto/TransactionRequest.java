package com.zenith.ledger.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Inbound DTO for a transfer request.
 *
 * idempotencyKey must be supplied by the client (UUID v4 recommended).
 * The same key on a retry returns the cached response without a new transaction.
 */
public record TransactionRequest(

        @NotBlank(message = "idempotencyKey is required")
        String idempotencyKey,

        @NotBlank(message = "sourceAccountNumber is required")
        String sourceAccountNumber,

        @NotBlank(message = "destAccountNumber is required")
        String destAccountNumber,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.00000001", message = "amount must be greater than zero")
        @Digits(integer = 20, fraction = 8)
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3)
        String currency,

        @NotNull(message = "platformFee is required")
        @DecimalMin(value = "0.0", message = "platformFee cannot be negative")
        BigDecimal platformFee,

        @Size(max = 500)
        String description,

        Map<String, Object> metadata
) {}
