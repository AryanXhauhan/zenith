package com.zenith.ledger.repository;

import com.zenith.ledger.model.InstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface InstallmentRepository extends JpaRepository<InstallmentPlan, UUID> {
    List<InstallmentPlan> findByAccountNumber(String accountNumber);
    List<InstallmentPlan> findByStatus(String status);
}
