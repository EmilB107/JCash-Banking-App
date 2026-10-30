package com.jcash.app.transfers.exception;

public class RecipientNotFoundException extends RuntimeException {

    public RecipientNotFoundException(String recipient) {
        super("Recipient account not found: " + recipient);
    }
}
