package com.jcash.app.transfers;

import com.jcash.app.accounts.Account;
import com.jcash.app.transfers.dto.TransferResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "transfer_idempotency_keys")
public class TransferIdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_account_id", nullable = false)
    private Account senderAccount;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @ManyToOne
    @JoinColumn(name = "recipient_account_id")
    private Account recipientAccount;

    @Column(name = "sender_balance_after", precision = 19, scale = 2)
    private BigDecimal senderBalanceAfter;

    @Column(name = "recipient_balance_after", precision = 19, scale = 2)
    private BigDecimal recipientBalanceAfter;

    @Column(length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TransferIdempotencyKey() {
    }

    public TransferIdempotencyKey(
            Account senderAccount,
            String idempotencyKey,
            String recipientEmail,
            BigDecimal amount) {
        this.senderAccount = senderAccount;
        this.idempotencyKey = idempotencyKey;
        this.recipientEmail = recipientEmail;
        this.amount = amount;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void markCompleted(
            Account completedRecipientAccount,
            BigDecimal completedSenderBalanceAfter,
            BigDecimal completedRecipientBalanceAfter,
            String completedCurrency) {
        this.recipientAccount = completedRecipientAccount;
        this.senderBalanceAfter = completedSenderBalanceAfter;
        this.recipientBalanceAfter = completedRecipientBalanceAfter;
        this.currency = completedCurrency;
        this.completed = true;
    }

    public TransferResponse toResponse() {
        if (!completed || recipientAccount == null || senderBalanceAfter == null
                || recipientBalanceAfter == null || currency == null) {
            throw new IllegalStateException("Transfer idempotency record is not completed");
        }

        return new TransferResponse(
                senderAccount.getId(),
                recipientAccount.getId(),
                recipientEmail,
                amount,
                currency,
                senderBalanceAfter,
                recipientBalanceAfter
        );
    }
}
