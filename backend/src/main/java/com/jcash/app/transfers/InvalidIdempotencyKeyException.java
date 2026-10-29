package com.jcash.app.transfers;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("Idempotency key is required and must be at most 255 characters");
    }
}
