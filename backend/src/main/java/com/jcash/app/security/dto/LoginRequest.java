package com.jcash.app.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// identifier is an email (contains "@") or a mobile number. The PIN format is not
// validated here so that a malformed PIN still counts as a failed attempt.
public record LoginRequest(
        @NotBlank @Size(max = 255) String identifier,
        @NotBlank String pin
) {
}
