package com.ledgermate.exception;

/** A domain rule was violated (for example: a reversal cannot itself be reversed). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
