package com.jcash.app.accounts;

public class InvalidCashInAmountException extends RuntimeException {

    public InvalidCashInAmountException() {
        super("Cash-in amount must be greater than zero");
    }
}
