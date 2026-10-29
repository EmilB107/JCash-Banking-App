package com.jcash.app.transfers.exception;

public class IdempotencyKeyInProgressException extends RuntimeException {

    public IdempotencyKeyInProgressException() {
        super("A transfer with this idempotency key is still being processed");
    }
}
