package com.jcash.app.accounts.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CashInRequest(
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal amount
) {
}
