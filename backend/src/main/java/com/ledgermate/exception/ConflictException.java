package com.ledgermate.exception;

/** The request is valid but conflicts with the current state (already reversed, archived...). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
