package com.zenith.ledger.service;

import com.zenith.ledger.model.InstallmentPlan;
import com.zenith.ledger.repository.InstallmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstallmentService {

    private final InstallmentRepository repository;

    @Transactional
    public InstallmentPlan createPlan(String accountNumber, BigDecimal totalAmount, int months) {
        BigDecimal installment = totalAmount.divide(BigDecimal.valueOf(months), 2, java.math.RoundingMode.HALF_UP);
        
        InstallmentPlan plan = InstallmentPlan.builder()
                .accountNumber(accountNumber)
                .totalAmount(totalAmount)
                .installmentAmount(installment)
                .totalInstallments(months)
                .paidInstallments(0)
                .remainingAmount(totalAmount)
                .nextDueDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .status("ACTIVE")
                .build();
        
        return repository.save(plan);
    }

    public List<InstallmentPlan> getAllPlans() {
        return repository.findAll();
    }

    public List<InstallmentPlan> getPlansByAccount(String accountNumber) {
        return repository.findByAccountNumber(accountNumber);
    }
}
