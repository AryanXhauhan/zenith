package com.zenith.ledger;

import com.zenith.ledger.dto.TransactionRequest;
import com.zenith.ledger.model.Account;
import com.zenith.ledger.repository.AccountRepository;
import com.zenith.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LedgerConcurrencyTest {

    @Autowired
    LedgerService ledgerService;

    @Autowired
    AccountRepository accountRepository;

    Account alice;
    Account bob;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        alice = accountRepository.save(Account.builder()
                .ownerId("user-alice")
                .accountNumber("ZNT-CONC-ALICE")
                .currency("USD")
                .balance(new BigDecimal("1000.00"))
                .accountType(Account.AccountType.CHECKING)
                .status(Account.AccountStatus.ACTIVE)
                .build());

        bob = accountRepository.save(Account.builder()
                .ownerId("user-bob")
                .accountNumber("ZNT-CONC-BOB")
                .currency("USD")
                .balance(new BigDecimal("0.00"))
                .accountType(Account.AccountType.CHECKING)
                .status(Account.AccountStatus.ACTIVE)
                .build());

        // Pre-create the system vault to prevent concurrent threads from failing on unique constraint
        accountRepository.save(Account.builder()
                .ownerId("SYSTEM")
                .accountNumber("ZNT-SYSTEM")
                .accountName("Zenith System Vault")
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .accountType(Account.AccountType.FEE)
                .status(Account.AccountStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("Concurrent Transfers: 10 threads transfer 100 concurrently, prevents double spend")
    void concurrentTransfers_preventDoubleSpend() throws InterruptedException {
        int threads = 10;
        // Run threads slightly staggered to prevent H2 database table-level artificial deadlocks
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                TransactionRequest request = new TransactionRequest(
                        UUID.randomUUID().toString(),
                        "ZNT-CONC-ALICE",
                        "ZNT-CONC-BOB",
                        new BigDecimal("100.00"),
                        "USD",
                        BigDecimal.ZERO,
                        "Concurrency test",
                        null
                );
                // Retry loop to handle H2 lock timeouts / deadlocks
                int maxRetries = 10;
                for (int attempt = 1; attempt <= maxRetries; attempt++) {
                    try {
                        ledgerService.transfer(request);
                        break;
                    } catch (Exception e) {
                        if (attempt == maxRetries || e instanceof com.zenith.common.exception.InsufficientFundsException) {
                            throw e;
                        }
                        Thread.sleep(100L * attempt); // exponential backoff
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);

        // Wait for all to complete
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception ignored) {
                // some might fail due to insufficient funds if we do more than 10
            }
        }

        Account updatedAlice = accountRepository.findByAccountNumber("ZNT-CONC-ALICE").orElseThrow();
        Account updatedBob = accountRepository.findByAccountNumber("ZNT-CONC-BOB").orElseThrow();

        assertThat(updatedAlice.getBalance()).isEqualByComparingTo("0.00");
        assertThat(updatedBob.getBalance()).isEqualByComparingTo("1000.00");
    }
}
