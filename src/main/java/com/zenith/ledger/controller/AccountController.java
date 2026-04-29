package com.zenith.ledger.controller;

import com.zenith.ledger.model.Account;
import com.zenith.ledger.repository.AccountRepository;
import com.zenith.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountRepository accountRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Account createAccount(@RequestBody Account account) {
        return accountRepository.findByAccountNumber(account.getAccountNumber())
                .map(existing -> {
                    if (account.getBalance() != null) {
                        existing.setBalance(account.getBalance());
                    }
                    return accountRepository.save(existing);
                })
                .orElseGet(() -> {
                    if (account.getBalance() == null) {
                        account.setBalance(BigDecimal.ZERO);
                    }
                    if (account.getStatus() == null) {
                        account.setStatus(Account.AccountStatus.ACTIVE);
                    }
                    if (account.getAccountType() == null) {
                        account.setAccountType(Account.AccountType.CHECKING);
                    }
                    if (account.getOwnerId() == null) {
                        account.setOwnerId("system-user");
                    }
                    return accountRepository.save(account);
                });
    }

    @GetMapping("/{accountNumber}")
    public Account getAccount(@PathVariable String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
    }

    @GetMapping
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }
}
