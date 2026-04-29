package com.zenith.ledger.controller;

import com.zenith.idempotency.Idempotent;
import com.zenith.ledger.dto.TransactionRequest;
import com.zenith.ledger.dto.TransactionResponse;
import com.zenith.ledger.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for the Double-Entry Ledger.
 * All routes require a valid X-Zenith-Key (enforced by ApiKeyInterceptor).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LedgerController {

    private final LedgerService ledgerService;

    /**
     * POST /api/v1/ledger/transfer
     *
     * Initiates an atomic double-entry transfer.
     * The idempotencyKey in the body is REQUIRED; a retry with the same key
     * will be short-circuited by the IdempotencyAspect (Module 3).
     */
    @Idempotent
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = "X-Simulate-Failure", defaultValue = "false") boolean simulateFailure) {

        TransactionResponse response = ledgerService.transfer(request, simulateFailure);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/ledger/transactions/{id}
     */
    @GetMapping("/transactions/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable UUID id) {
        return ResponseEntity.ok(ledgerService.getTransaction(id));
    }

    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<List<TransactionResponse>> getAccountHistory(@PathVariable String accountNumber) {
        return ResponseEntity.ok(ledgerService.getAccountHistory(accountNumber));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(ledgerService.getRealTimeStats());
    }
}
