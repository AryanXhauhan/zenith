package com.zenith.ledger.controller;

import com.zenith.ledger.model.InstallmentPlan;
import com.zenith.ledger.service.InstallmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/installments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InstallmentController {

    private final InstallmentService installmentService;

    @GetMapping
    public List<InstallmentPlan> getAllPlans() {
        return installmentService.getAllPlans();
    }

    @PostMapping
    public ResponseEntity<InstallmentPlan> createPlan(
            @RequestParam String accountNumber,
            @RequestParam BigDecimal totalAmount,
            @RequestParam int months) {
        return ResponseEntity.ok(installmentService.createPlan(accountNumber, totalAmount, months));
    }

    @GetMapping("/account/{accountNumber}")
    public List<InstallmentPlan> getPlansByAccount(@PathVariable String accountNumber) {
        return installmentService.getPlansByAccount(accountNumber);
    }
}
