package com.jcash.app.transfers;

public class InvalidTransferAmountException extends RuntimeException {

    public InvalidTransferAmountException() {
        super("Transfer amount must be greater than zero");
    }
}
