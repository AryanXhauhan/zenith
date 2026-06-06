package com.zenith.ledger;

import com.zenith.ledger.model.InstallmentPlan;
import com.zenith.ledger.repository.InstallmentRepository;
import com.zenith.ledger.service.InstallmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstallmentServiceTest {

    @Mock
    private InstallmentRepository installmentRepository;

    @InjectMocks
    private InstallmentService installmentService;

    @BeforeEach
    void setUp() {
        when(installmentRepository.save(any(InstallmentPlan.class))).thenAnswer(invocation -> {
            InstallmentPlan plan = invocation.getArgument(0);
            plan.setId(UUID.randomUUID());
            return plan;
        });
    }

    @Test
    @DisplayName("InstallmentService: correctly splits total amount and rounds to 2 decimals")
    void createPlan_splitsCorrectly() {
        BigDecimal totalAmount = new BigDecimal("1000.00");
        int months = 3;

        InstallmentPlan plan = installmentService.createPlan("ZNT-TEST-001", totalAmount, months);

        // 1000 / 3 = 333.3333... -> 333.33
        assertThat(plan.getInstallmentAmount()).isEqualByComparingTo("333.33");
        assertThat(plan.getTotalAmount()).isEqualByComparingTo("1000.00");
        assertThat(plan.getTotalInstallments()).isEqualTo(3);
        assertThat(plan.getPaidInstallments()).isEqualTo(0);
        assertThat(plan.getStatus()).isEqualTo("ACTIVE");
        assertThat(plan.getAccountNumber()).isEqualTo("ZNT-TEST-001");
        assertThat(plan.getNextDueDate()).isNotNull();
    }
}
