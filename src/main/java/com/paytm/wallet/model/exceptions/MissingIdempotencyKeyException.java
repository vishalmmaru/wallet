package com.paytm.wallet.model.exceptions;

public class MissingIdempotencyKeyException extends RuntimeException {
    public MissingIdempotencyKeyException(String message) {
        super(message);
    }
}
