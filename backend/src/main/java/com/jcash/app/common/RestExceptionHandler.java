package com.jcash.app.common;

import com.jcash.app.accounts.AccountNotFoundException;
import com.jcash.app.accounts.InvalidCashInAmountException;
import com.jcash.app.users.UserAlreadyExistsException;
import com.jcash.app.users.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleUserAlreadyExists(UserAlreadyExistsException exception) {
        return new ApiError("EMAIL_ALREADY_REGISTERED", exception.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleBadCredentials() {
        return new ApiError("INVALID_CREDENTIALS", "Email or password is incorrect");
    }

    @ExceptionHandler({UserNotFoundException.class, AccountNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(RuntimeException exception) {
        return new ApiError("RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidCashInAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidCashInAmount(InvalidCashInAmountException exception) {
        return new ApiError("INVALID_CASH_IN_AMOUNT", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidationFailure() {
        return new ApiError("VALIDATION_FAILED", "Request validation failed");
    }
}
