package com.jcash.app.transfers.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank
        @Email
        String recipientEmail,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount
) {
}
