package com.zenith.ledger.dto;

import com.zenith.ledger.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound DTO for a completed or pending transaction.
 */
public record TransactionResponse(
        UUID   id,
        String reference,
        String idempotencyKey,
        String status,
        BigDecimal amount,
        String currency,
        String sourceAccountNumber,
        String destAccountNumber,
        String description,
        String blockchainTxHash,
        boolean blockchainConfirmed,
        Instant createdAt,
        Instant completedAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getReference(),
                t.getIdempotencyKey(),
                t.getStatus().name(),
                t.getAmount(),
                t.getCurrency(),
                t.getSourceAccount().getAccountNumber(),
                t.getDestAccount().getAccountNumber(),
                t.getDescription(),
                t.getBlockchainTxHash(),
                t.isBlockchainConfirmed(),
                t.getCreatedAt(),
                t.getCompletedAt()
        );
    }
}
