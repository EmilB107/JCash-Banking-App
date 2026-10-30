package com.jcash.app.users.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String mobileNumber,
        String displayName,
        Instant createdAt
) {
}
