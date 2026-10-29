package com.jcash.app.transfers.exception;

public class InvalidTransferAmountException extends RuntimeException {

    public InvalidTransferAmountException() {
        super("Transfer amount must be greater than zero");
    }
}
