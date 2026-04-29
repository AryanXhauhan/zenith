package com.zenith.ledger.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "installment_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstallmentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private BigDecimal installmentAmount;

    @Column(nullable = false)
    private int totalInstallments;

    @Column(nullable = false)
    private int paidInstallments;

    @Column(nullable = false)
    private BigDecimal remainingAmount;

    @Column(nullable = false)
    private Instant nextDueDate;

    @Column(nullable = false)
    private String status; // ACTIVE | COMPLETED | DEFAULTED

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = "ACTIVE";
        if (this.paidInstallments == 0) this.remainingAmount = this.totalAmount;
    }
}
