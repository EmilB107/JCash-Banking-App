package com.jcash.app.users.exception;

public class MobileNumberAlreadyRegisteredException extends RuntimeException {

    public MobileNumberAlreadyRegisteredException(String mobileNumber) {
        super("Mobile number is already registered: " + mobileNumber);
    }
}
