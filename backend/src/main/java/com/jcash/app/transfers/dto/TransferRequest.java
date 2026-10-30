package com.jcash.app.transfers.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        // Email (contains "@") or mobile number of the recipient.
        @NotBlank
        @Size(max = 255)
        String recipient,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount
) {
}
