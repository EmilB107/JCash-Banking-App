package com.jcash.app.transactions;

import com.jcash.app.accounts.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_entries")
public class TransactionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionDirection direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, length = 255)
    private String details;

    // The other side of a transfer; null for cash-in.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_account_id")
    private Account counterpartyAccount;

    // Shared by the DEBIT and CREDIT entries of one transfer; null for cash-in.
    @Column(name = "transfer_reference")
    private UUID transferReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TransactionEntry() {
    }

    public TransactionEntry(
            Account account,
            TransactionType transactionType,
            TransactionDirection direction,
            BigDecimal amount,
            String currency,
            BigDecimal balanceAfter,
            String details) {
        this(account, transactionType, direction, amount, currency, balanceAfter,
                details, null, null);
    }

    public TransactionEntry(
            Account account,
            TransactionType transactionType,
            TransactionDirection direction,
            BigDecimal amount,
            String currency,
            BigDecimal balanceAfter,
            String details,
            Account counterpartyAccount,
            UUID transferReference) {
        this.account = account;
        this.transactionType = transactionType;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.balanceAfter = balanceAfter;
        this.details = details;
        this.counterpartyAccount = counterpartyAccount;
        this.transferReference = transferReference;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public TransactionDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getDetails() {
        return details;
    }

    public Account getCounterpartyAccount() {
        return counterpartyAccount;
    }

    public UUID getTransferReference() {
        return transferReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
