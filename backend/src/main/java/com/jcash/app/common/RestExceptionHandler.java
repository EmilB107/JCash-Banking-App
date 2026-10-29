package com.jcash.app.common;

import com.jcash.app.accounts.AccountNotFoundException;
import com.jcash.app.accounts.InvalidCashInAmountException;
import com.jcash.app.transfers.IdempotencyKeyConflictException;
import com.jcash.app.transfers.IdempotencyKeyInProgressException;
import com.jcash.app.transfers.InsufficientFundsException;
import com.jcash.app.transfers.InvalidIdempotencyKeyException;
import com.jcash.app.transfers.InvalidTransferAmountException;
import com.jcash.app.transfers.RecipientNotFoundException;
import com.jcash.app.transfers.SelfTransferNotAllowedException;
import com.jcash.app.users.UserAlreadyExistsException;
import com.jcash.app.users.UserNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler({
            UserNotFoundException.class,
            AccountNotFoundException.class,
            RecipientNotFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(RuntimeException exception) {
        return new ApiError("RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidCashInAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidCashInAmount(InvalidCashInAmountException exception) {
        return new ApiError("INVALID_CASH_IN_AMOUNT", exception.getMessage());
    }

    @ExceptionHandler(InvalidTransferAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidTransferAmount(InvalidTransferAmountException exception) {
        return new ApiError("INVALID_TRANSFER_AMOUNT", exception.getMessage());
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidIdempotencyKey(InvalidIdempotencyKeyException exception) {
        return new ApiError("INVALID_IDEMPOTENCY_KEY", exception.getMessage());
    }

    @ExceptionHandler(SelfTransferNotAllowedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleSelfTransfer(SelfTransferNotAllowedException exception) {
        return new ApiError("SELF_TRANSFER_NOT_ALLOWED", exception.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInsufficientFunds(InsufficientFundsException exception) {
        return new ApiError("INSUFFICIENT_FUNDS", exception.getMessage());
    }

    @ExceptionHandler({
            IdempotencyKeyConflictException.class,
            IdempotencyKeyInProgressException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleIdempotencyConflict(RuntimeException exception) {
        return new ApiError("IDEMPOTENCY_KEY_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler({
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleOptimisticLockConflict(RuntimeException exception) {
        return new ApiError("OPTIMISTIC_LOCK_CONFLICT", "Concurrent update detected. Please retry the transfer");
    }

    @ExceptionHandler({
            PessimisticLockingFailureException.class,
            PessimisticLockException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleLockConflict(RuntimeException exception) {
        return new ApiError("LOCK_CONFLICT", "Account is busy. Please retry the transfer");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidationFailure() {
        return new ApiError("VALIDATION_FAILED", "Request validation failed");
    }
}
