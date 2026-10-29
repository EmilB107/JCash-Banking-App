package com.jcash.app.transfers.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResponse(
        UUID senderAccountId,
        UUID recipientAccountId,
        String recipientEmail,
        BigDecimal amount,
        String currency,
        BigDecimal senderBalanceAfter,
        BigDecimal recipientBalanceAfter
) {
}
