package com.jcash.app.accounts.exception;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID userId) {
        super("Account not found for user: " + userId);
    }
}
