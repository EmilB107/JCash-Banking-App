package com.jcash.app.transfers.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("Idempotency key is required and must be at most 255 characters");
    }
}
