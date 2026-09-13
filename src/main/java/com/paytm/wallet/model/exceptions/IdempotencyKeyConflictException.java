package com.paytm.wallet.model.exceptions;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String message) { super(message); }
}
