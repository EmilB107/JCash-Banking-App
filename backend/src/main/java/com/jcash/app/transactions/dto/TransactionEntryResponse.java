package com.jcash.app.transactions.dto;

import com.jcash.app.transactions.TransactionDirection;
import com.jcash.app.transactions.TransactionEntry;
import com.jcash.app.transactions.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionEntryResponse(
        UUID id,
        UUID account,
        TransactionType transactionType,
        TransactionDirection direction,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        String details,
        UUID transferReference,
        Instant createdAt) {

    public static TransactionEntryResponse from(TransactionEntry entry) {
        return new TransactionEntryResponse(
                entry.getId(),
                entry.getAccount().getId(),
                entry.getTransactionType(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getBalanceAfter(),
                entry.getDetails(),
                entry.getTransferReference(),
                entry.getCreatedAt()
        );
    }
}
