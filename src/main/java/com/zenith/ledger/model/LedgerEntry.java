package com.zenith.ledger.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Module 2 – The Double-Entry Ledger Entry
 *
 * FinTech's cardinal rule: "Money is never created or destroyed."
 * For every transaction, the system creates EXACTLY two LedgerEntry rows
 * so that the sum of (CREDIT - DEBIT) across all entries is always ZERO.
 *
 * | transaction_id | account_id  | entry_type | amount  |
 * |----------------|-------------|------------|---------|
 * | txn-001        | acc-Alice   | DEBIT      | 100.00  |  ← Alice's balance ↓
 * | txn-001        | acc-Bob     | CREDIT     | 100.00  |  ← Bob's balance   ↑
 *
 * Both rows are written inside the same @Transactional block.
 * If either write fails, the whole transaction rolls back (ACID).
 */
@Entity
@Table(name = "ledger_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Links both legs of a transfer. */
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6)
    private EntryType entryType;

    /** Always stored as a positive number. Semantics determined by entryType. */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public enum EntryType { DEBIT, CREDIT }
}
