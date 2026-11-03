package com.jcash.app.users.exception;

// Only for the signed-in user (looked up by the session's email): it is answered
// with 401 and ends the session (see RestExceptionHandler). Other lookups, such
// as transfer recipients, use their own exceptions.
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String email) {
        super("User not found with email: " + email);
    }
}
