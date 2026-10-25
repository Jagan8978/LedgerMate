package com.ledgermate.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PersonDtos {

    private PersonDtos() {}

    public record PersonRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 120, message = "Name must be at most 120 characters")
            String name,
            @Size(max = 30, message = "Phone/reference must be at most 30 characters")
            String phone,
            @Size(max = 255, message = "Description must be at most 255 characters")
            String description,
            @Digits(integer = 15, fraction = 2,
                    message = "Opening balance may have at most 15 whole digits and 2 decimal places")
            BigDecimal openingBalance,
            @Size(max = 2000, message = "Notes must be at most 2000 characters")
            String notes) {}

    /** Person plus ledger totals, all calculated on the server. Money values are plain strings. */
    public record PersonResponse(
            UUID id, String name, String phone, String description, String notes,
            String openingBalance, String totalCredits, String totalDebits, String currentBalance,
            long transactionCount, Instant lastTransactionAt,
            boolean archived, Instant archivedAt, Instant createdAt, Instant updatedAt) {}

    public record LedgerSummaryResponse(
            UUID personId, String personName, String openingBalance, String totalCredits,
            String totalDebits, String currentBalance, long transactionCount,
            Instant lastTransactionAt) {}

    /** Aggregate across the user's ACTIVE ledgers (not money belonging to one person). */
    public record OverviewResponse(long activeLedgers, String totalCredits, String totalDebits,
                                   String netBalance) {}

    public record DuplicateCheckResponse(boolean duplicate, long matches) {}
}
