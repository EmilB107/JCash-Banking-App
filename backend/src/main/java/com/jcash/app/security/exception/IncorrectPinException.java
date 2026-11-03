package com.jcash.app.security.exception;

public class IncorrectPinException extends RuntimeException {

    public IncorrectPinException(int attemptsLeft) {
        super("Current PIN is incorrect. " + attemptsLeft
                + (attemptsLeft == 1 ? " attempt" : " attempts")
                + " left before your account is locked.");
    }
}
