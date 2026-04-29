package com.zenith.ledger.repository;

import com.zenith.ledger.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);

    List<LedgerEntry> findByAccount_IdOrderByCreatedAtDesc(UUID accountId);
}
