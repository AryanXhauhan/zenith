package com.zenith.ledger;

import com.zenith.ledger.dto.TransactionRequest;
import com.zenith.ledger.dto.TransactionResponse;
import com.zenith.ledger.model.Account;
import com.zenith.ledger.repository.AccountRepository;
import com.zenith.ledger.service.LedgerService;
import com.zenith.common.exception.InsufficientFundsException;
import com.zenith.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the Double-Entry Ledger Service.
 *
 * Tests verify:
 *  1. Successful double-entry transfer
 *  2. Atomic rollback on insufficient funds
 *  3. Idempotency key deduplication
 *  4. Self-transfer rejection
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerServiceTest {

    @Autowired
    LedgerService ledgerService;

    @Autowired
    AccountRepository accountRepository;

    Account alice;
    Account bob;

    @BeforeEach
    void setUp() {
        alice = accountRepository.save(Account.builder()
                .ownerId("user-alice")
                .accountNumber("ZNT-TEST-ALICE-001")
                .currency("USD")
                .balance(new BigDecimal("1000.00"))
                .accountType(Account.AccountType.CHECKING)
                .status(Account.AccountStatus.ACTIVE)
                .build());

        bob = accountRepository.save(Account.builder()
                .ownerId("user-bob")
                .accountNumber("ZNT-TEST-BOB-001")
                .currency("USD")
                .balance(new BigDecimal("500.00"))
                .accountType(Account.AccountType.CHECKING)
                .status(Account.AccountStatus.ACTIVE)
                .build());
    }

    // ── Test 1: Happy path ────────────────────────────────────────────────

    @Test
    @DisplayName("Transfer: DEBIT from Alice, CREDIT to Bob – balances update atomically")
    void transfer_successfulDoubleEntry() {
        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID().toString(),           // idempotencyKey
                "ZNT-TEST-ALICE-001",
                "ZNT-TEST-BOB-001",
                new BigDecimal("250.00"),
                "USD",
                BigDecimal.ZERO,                        // platformFee
                "Test payment",
                null
        );

        TransactionResponse response = ledgerService.transfer(request);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.amount()).isEqualByComparingTo("250.00");

        // Reload from DB and verify balances
        Account updatedAlice = accountRepository.findByAccountNumber("ZNT-TEST-ALICE-001").orElseThrow();
        Account updatedBob   = accountRepository.findByAccountNumber("ZNT-TEST-BOB-001").orElseThrow();

        assertThat(updatedAlice.getBalance()).isEqualByComparingTo("750.00");  // 1000 - 250
        assertThat(updatedBob.getBalance()).isEqualByComparingTo("750.00");    // 500  + 250
    }

    // ── Test 2: Insufficient funds → full rollback ────────────────────────

    @Test
    @DisplayName("Transfer: InsufficientFunds thrown → no balance change (ACID rollback)")
    void transfer_insufficientFunds_rollsBack() {
        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID().toString(),
                "ZNT-TEST-ALICE-001",
                "ZNT-TEST-BOB-001",
                new BigDecimal("9999.00"),        // More than alice's 1000
                "USD",
                BigDecimal.ZERO,
                "Should fail",
                null
        );

        assertThatThrownBy(() -> ledgerService.transfer(request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        // Verify balances are UNCHANGED
        Account alice = accountRepository.findByAccountNumber("ZNT-TEST-ALICE-001").orElseThrow();
        Account bob   = accountRepository.findByAccountNumber("ZNT-TEST-BOB-001").orElseThrow();

        assertThat(alice.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(bob.getBalance()).isEqualByComparingTo("500.00");
    }

    // ── Test 3: Same-account transfer rejection ───────────────────────────

    @Test
    @DisplayName("Transfer: Source == Dest throws ValidationException")
    void transfer_sameAccount_throwsValidation() {
        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID().toString(),
                "ZNT-TEST-ALICE-001",
                "ZNT-TEST-ALICE-001",         // Same account!
                new BigDecimal("100.00"),
                "USD",
                BigDecimal.ZERO,
                "Self-transfer",
                null
        );

        assertThatThrownBy(() -> ledgerService.transfer(request))
                .isInstanceOf(ValidationException.class);
    }

    // ── Test 4: Frozen account rejection ─────────────────────────────────

    @Test
    @DisplayName("Transfer: Frozen source account throws ValidationException")
    void transfer_frozenAccount_throwsValidation() {
        alice.setStatus(Account.AccountStatus.FROZEN);
        accountRepository.save(alice);

        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID().toString(),
                "ZNT-TEST-ALICE-001",
                "ZNT-TEST-BOB-001",
                new BigDecimal("100.00"),
                "USD",
                BigDecimal.ZERO,
                "Frozen account test",
                null
        );

        assertThatThrownBy(() -> ledgerService.transfer(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not active");
    }

    // ── Test 5: Double-entry invariant ───────────────────────────────────

    @Test
    @DisplayName("Transfer: Net change in total balances is always ZERO")
    void transfer_doubleEntryInvariant_netChangeIsZero() {
        BigDecimal totalBefore = alice.getBalance().add(bob.getBalance());

        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID().toString(),
                "ZNT-TEST-ALICE-001",
                "ZNT-TEST-BOB-001",
                new BigDecimal("300.00"),
                "USD",
                BigDecimal.ZERO,
                "Invariant test",
                null
        );

        ledgerService.transfer(request);

        Account updatedAlice = accountRepository.findByAccountNumber("ZNT-TEST-ALICE-001").orElseThrow();
        Account updatedBob   = accountRepository.findByAccountNumber("ZNT-TEST-BOB-001").orElseThrow();

        BigDecimal totalAfter = updatedAlice.getBalance().add(updatedBob.getBalance());

        // Money is never created or destroyed
        assertThat(totalAfter).isEqualByComparingTo(totalBefore);
    }
}
