package org.karolismed.redisapp.exception;

public class FailedTransactionException extends RuntimeException {
    public FailedTransactionException() {
        super("Failed to complete operation due to a failed transaction.");
    }

    public FailedTransactionException(String message) {
        super(message);
    }
}
