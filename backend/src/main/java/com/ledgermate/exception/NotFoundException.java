package com.ledgermate.exception;

/** Resource does not exist OR belongs to another user (deliberately indistinguishable). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
