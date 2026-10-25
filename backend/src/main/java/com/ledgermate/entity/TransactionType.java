package com.ledgermate.entity;

/** Direction of money relative to the ledger. The stored amount is always positive. */
public enum TransactionType {
    /** Money received into the ledger: increases the balance. */
    CREDIT,
    /** Money paid out of the ledger: decreases the balance. */
    DEBIT;

    public TransactionType opposite() {
        return this == CREDIT ? DEBIT : CREDIT;
    }
}
