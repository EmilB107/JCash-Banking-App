package com.jcash.app.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        // @Email alone accepts "a@b"; the regexp also requires a dot in the domain.
        @NotBlank
        @Email(regexp = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
        String email,

        @NotBlank
        @Pattern(regexp = "09\\d{9}|\\+639\\d{9}")
        String mobileNumber,

        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String pin
) {
}
