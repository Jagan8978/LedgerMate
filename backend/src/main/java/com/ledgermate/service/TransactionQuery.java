package com.ledgermate.service;

import com.ledgermate.entity.TransactionType;
import java.time.LocalDate;

/**
 * Filters for listing/exporting transactions. Dates are calendar dates in the user's timezone
 * (from inclusive, to inclusive). sortBy: "date" (default) or "amount"; direction: "asc"/"desc".
 */
public record TransactionQuery(
        TransactionType type,
        LocalDate from,
        LocalDate to,
        String counterparty,
        String description,
        String search,
        String sortBy,
        String direction,
        int page,
        int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
}
