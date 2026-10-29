package com.jcash.app.transfers;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException() {
        super("Idempotency key has already been used with a different transfer request");
    }
}
