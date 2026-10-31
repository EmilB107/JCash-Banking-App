package com.jcash.app.transactions;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionEntryRepository
        extends JpaRepository<TransactionEntry, UUID> {

    Page<TransactionEntry> findByAccountId(UUID accountId, Pageable pageable);
}
