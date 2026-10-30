package com.jcash.app.users.dto;

import com.jcash.app.users.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String mobileNumber,
        String displayName,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getMobileNumber(),
                user.getDisplayName(),
                user.getCreatedAt()
        );
    }
}
