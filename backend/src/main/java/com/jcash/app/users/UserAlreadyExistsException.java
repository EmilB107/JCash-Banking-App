package com.jcash.app.users;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("Email is already registered: " + email);
    }
}
