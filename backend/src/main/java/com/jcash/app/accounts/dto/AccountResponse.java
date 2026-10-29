package com.jcash.app.accounts.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String currency,
        BigDecimal balance,
        Instant createdAt,
        Instant updatedAt
) {
}
