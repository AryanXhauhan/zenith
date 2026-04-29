package com.zenith.ledger.service;

import com.zenith.common.exception.InsufficientFundsException;
import com.zenith.common.exception.ResourceNotFoundException;
import com.zenith.common.exception.ValidationException;
import com.zenith.ledger.dto.TransactionRequest;
import com.zenith.ledger.dto.TransactionResponse;
import com.zenith.ledger.model.Account;
import com.zenith.ledger.model.LedgerEntry;
import com.zenith.ledger.model.Transaction;
import com.zenith.ledger.repository.AccountRepository;
import com.zenith.ledger.repository.LedgerEntryRepository;
import com.zenith.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module 2 – The Double-Entry Ledger Service
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │                    DOUBLE-ENTRY INVARIANT                        │
 * │  For every transaction:                                          │
 * │    SUM of DEBITS  == SUM of CREDITS  (always balanced)           │
 * │    Source balance  decreases by `amount`                         │
 * │    Dest   balance  increases by `amount`                         │
 * │                                                                  │
 * │  If ANY step fails → full PostgreSQL rollback via @Transactional │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * Isolation.REPEATABLE_READ prevents:
 *   - Dirty reads   (reading uncommitted changes)
 *   - Non-repeatable reads (row changed between reads in same txn)
 *
 * Pessimistic locking (SELECT FOR UPDATE) prevents concurrent
 * balance updates that could cause double-spending.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository     accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Executes a double-entry transfer atomically.
     *
     * @param request validated transfer request
     * @return the completed transaction
     * @throws InsufficientFundsException  if source has insufficient balance
     * @throws ResourceNotFoundException   if either account does not exist
     * @throws ValidationException         if currencies mismatch or accounts are same
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public TransactionResponse transfer(TransactionRequest request) {
        return transfer(request, false);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public TransactionResponse transfer(TransactionRequest request, boolean simulateFailure) {
        log.info("Processing transfer request: amount={}, source={}, dest={}", 
                request.amount(), request.sourceAccountNumber(), request.destAccountNumber());

        // ── Step 1: Load accounts with PESSIMISTIC WRITE lock ──────────────
        // ORDER BY account number to prevent deadlock (always lock in same order)
        String srcNum  = request.sourceAccountNumber();
        String destNum = request.destAccountNumber();

        Account source = accountRepository.findByAccountNumber(srcNum)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + srcNum));
        Account dest   = accountRepository.findByAccountNumber(destNum)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + destNum));

        // Lock both accounts in a consistent order to prevent deadlocks
        UUID firstLockId  = source.getId().compareTo(dest.getId()) < 0 ? source.getId() : dest.getId();
        UUID secondLockId = source.getId().compareTo(dest.getId()) < 0 ? dest.getId()   : source.getId();

        accountRepository.findByIdForUpdate(firstLockId);
        accountRepository.findByIdForUpdate(secondLockId);

        // ── Step 2: Validate ────────────────────────────────────────────────
        if (source.getId().equals(dest.getId())) {
            throw new ValidationException("Source and destination accounts must differ.");
        }
        if (!source.getCurrency().equals(request.currency())) {
            throw new ValidationException("Source account currency " + source.getCurrency()
                    + " does not match transaction currency " + request.currency());
        }
        if (source.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new ValidationException("Source account is not active: " + source.getStatus());
        }
        if (dest.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new ValidationException("Destination account is not active: " + dest.getStatus());
        }
        BigDecimal totalDebit = request.amount().add(request.platformFee());

        if (source.getBalance().compareTo(totalDebit) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + source.getBalance()
                            + " " + source.getCurrency()
                            + ", Required (including fee): " + totalDebit);
        }

        // Zenith System Account for Fees
        Account systemVault = accountRepository.findByAccountNumber("ZNT-SYSTEM")
                .orElseGet(() -> {
                    Account vault = Account.builder()
                            .ownerId("SYSTEM")
                            .accountNumber("ZNT-SYSTEM")
                            .accountName("Zenith System Vault")
                            .balance(BigDecimal.ZERO)
                            .currency(request.currency())
                            .accountType(Account.AccountType.FEE)
                            .status(Account.AccountStatus.ACTIVE)
                            .build();
                    return accountRepository.save(vault);
                });

        // ── Step 3: Create Transaction header ───────────────────────────────
        Transaction txn = Transaction.builder()
                .idempotencyKey(request.idempotencyKey())
                .reference(generateReference())
                .status(Transaction.TransactionStatus.PROCESSING)
                .amount(request.amount())
                .currency(request.currency())
                .sourceAccount(source)
                .destAccount(dest)
                .description(request.description())
                .metadata(request.metadata())
                .build();
        txn = transactionRepository.save(txn);

        // ── Step 4: Write DEBIT entry (source) ──────────────────────────────
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transactionId(txn.getId())
                .account(source)
                .entryType(LedgerEntry.EntryType.DEBIT)
                .amount(totalDebit)
                .currency(request.currency())
                .description("DEBIT: " + request.description() + " (Incl. Platform Fee)")
                .build();
        ledgerEntryRepository.save(debitEntry);

        // ── Step 5: Write CREDIT entries (destination & system) ──────────────
        // Merchant Credit
        LedgerEntry creditEntry = LedgerEntry.builder()
                .transactionId(txn.getId())
                .account(dest)
                .entryType(LedgerEntry.EntryType.CREDIT)
                .amount(request.amount())
                .currency(request.currency())
                .description("CREDIT: " + request.description())
                .build();
        ledgerEntryRepository.save(creditEntry);

        // Zenith Profit Credit
        if (request.platformFee().compareTo(BigDecimal.ZERO) > 0) {
            LedgerEntry feeEntry = LedgerEntry.builder()
                    .transactionId(txn.getId())
                    .account(systemVault)
                    .entryType(LedgerEntry.EntryType.CREDIT)
                    .amount(request.platformFee())
                    .currency(request.currency())
                    .description("FEE: Zenith Platform Fee")
                    .build();
            ledgerEntryRepository.save(feeEntry);
        }

        // ── Step 6: FAILURE SIMULATION POINT ───────────────────────────────
        if (simulateFailure) {
            log.error("!!! SIMULATED FAILURE TRIGGERED !!! Rolling back transaction context...");
            throw new com.zenith.common.exception.SimulatedFailureException("System crashed after ledger logging but before balance updates.");
        }

        // ── Step 7: Update running balances ─────────────────────────────────
        log.info("Persisting final balances for source, dest, and system vault...");
        source.setBalance(source.getBalance().subtract(totalDebit));
        dest.setBalance(dest.getBalance().add(request.amount()));
        systemVault.setBalance(systemVault.getBalance().add(request.platformFee()));
        
        accountRepository.save(source);
        accountRepository.save(dest);
        accountRepository.save(systemVault);

        // ── Step 7: Mark transaction COMPLETED ──────────────────────────────
        txn.setStatus(Transaction.TransactionStatus.COMPLETED);
        txn.setCompletedAt(Instant.now());
        txn = transactionRepository.save(txn);

        log.info("Ledger: transfer COMPLETED txnId={} reference={}", txn.getId(), txn.getReference());
        return TransactionResponse.from(txn);
    }

    /**
     * Marks a transaction as blockchain-confirmed after Web3 settlement.
     * Called by Web3Service once the on-chain tx is mined.
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmBlockchainSettlement(UUID transactionId, String blockchainTxHash) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        txn.setBlockchainTxHash(blockchainTxHash);
        txn.setBlockchainConfirmed(true);
        transactionRepository.save(txn);

        log.info("Ledger: blockchain confirmed txnId={} hash={}", transactionId, blockchainTxHash);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAccountHistory(String accountNumber) {
        log.info("Ledger: fetching history for account={}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        return ledgerEntryRepository.findByAccount_IdOrderByCreatedAtDesc(account.getId())
                .stream()
                .map(entry -> {
                    Transaction txn = transactionRepository.findById(entry.getTransactionId()).orElse(null);
                    return new TransactionResponse(
                            entry.getTransactionId(),
                            txn != null ? txn.getReference() : "N/A",
                            txn != null ? txn.getIdempotencyKey() : "N/A",
                            txn != null ? txn.getStatus().name() : "COMPLETED",
                            entry.getAmount(),
                            entry.getCurrency(),
                            txn != null ? txn.getSourceAccount().getAccountNumber() : "UNKNOWN",
                            txn != null ? txn.getDestAccount().getAccountNumber() : "UNKNOWN",
                            entry.getDescription(),
                            txn != null ? txn.getBlockchainTxHash() : null,
                            txn != null && txn.isBlockchainConfirmed(),
                            entry.getCreatedAt(),
                            txn != null ? txn.getCompletedAt() : entry.getCreatedAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRealTimeStats() {
        long totalTx = transactionRepository.count();
        long completedTx = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.COMPLETED)
                .count();

        BigDecimal volume = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.COMPLETED)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double successRate = totalTx == 0 ? 100.0 : (double) completedTx / totalTx * 100.0;

        return Map.of(
                "totalVolume", volume,
                "successRate", Math.round(successRate * 10.0) / 10.0,
                "totalTransactions", totalTx
        );
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID id) {
        return transactionRepository.findById(id)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String generateReference() {
        return "ZNT-" + Instant.now().toEpochMilli() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
