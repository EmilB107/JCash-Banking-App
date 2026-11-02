package com.jcash.app.accounts.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CashInRequest(
        // Demo funds: capped per request so balances stay realistic. @Digits
        // rejects a third decimal instead of letting NUMERIC(19, 2) round it.
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("100000.00")
        @Digits(integer = 6, fraction = 2)
        BigDecimal amount
) {
}
