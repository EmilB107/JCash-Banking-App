package com.jcash.app.transactions.exception;

public class InvalidPageRequestException extends RuntimeException {

    public InvalidPageRequestException(int maxPageSize) {
        super("Page must be zero or greater and size must be between 1 and "
                + maxPageSize);
    }
}
