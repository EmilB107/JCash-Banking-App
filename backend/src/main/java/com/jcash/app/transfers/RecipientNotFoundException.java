package com.jcash.app.transfers;

public class RecipientNotFoundException extends RuntimeException {

    public RecipientNotFoundException(String recipientEmail) {
        super("Recipient account not found for email: " + recipientEmail);
    }
}
