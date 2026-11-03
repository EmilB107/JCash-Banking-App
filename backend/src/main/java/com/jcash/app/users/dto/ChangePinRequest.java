package com.jcash.app.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePinRequest(
        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String currentPin,

        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String newPin
) {
}
