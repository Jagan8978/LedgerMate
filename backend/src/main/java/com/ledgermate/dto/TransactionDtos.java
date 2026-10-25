package com.ledgermate.dto;

import com.ledgermate.entity.EntryKind;
import com.ledgermate.entity.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class TransactionDtos {

    private TransactionDtos() {}

    public record TransactionRequest(
            @NotNull(message = "Transaction type is required (CREDIT or DEBIT)")
            TransactionType type,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.00", inclusive = false, message = "Amount must be greater than zero")
            @Digits(integer = 15, fraction = 2,
                    message = "Amount may have at most 15 whole digits and 2 decimal places")
            BigDecimal amount,
            @NotNull(message = "Transaction date is required")
            LocalDate transactionDate,
            /** Optional. Defaults to the current time of day when the date is today. */
            LocalTime transactionTime,
            @NotBlank(message = "Counterparty name is required")
            @Size(max = 120, message = "Counterparty name must be at most 120 characters")
            String counterpartyName,
            @Size(max = 500, message = "Description must be at most 500 characters")
            String description,
            @Size(max = 60, message = "Reference number must be at most 60 characters")
            String referenceNumber,
            @Size(max = 2000, message = "Notes must be at most 2000 characters")
            String notes) {}

    /** PATCH semantics: null = leave unchanged, "" = clear (counterparty cannot be cleared). */
    public record DescriptiveUpdateRequest(
            @Size(max = 500, message = "Description must be at most 500 characters") String description,
            @Size(max = 2000, message = "Notes must be at most 2000 characters") String notes,
            @Size(max = 120, message = "Counterparty name must be at most 120 characters") String counterpartyName,
            @Size(max = 60, message = "Reference number must be at most 60 characters") String referenceNumber,
            @Size(max = 500, message = "Reason must be at most 500 characters") String reason) {}

    public record ReverseRequest(
            @NotBlank(message = "A reason is required")
            @Size(max = 500, message = "Reason must be at most 500 characters")
            String reason) {}

    public record CorrectionRequest(
            @NotBlank(message = "A reason is required")
            @Size(max = 500, message = "Reason must be at most 500 characters")
            String reason,
            @NotNull(message = "The corrected transaction is required")
            @Valid
            TransactionRequest replacement) {}

    public record TransactionResponse(
            UUID id, UUID personId, TransactionType type, String amount, String signedAmount,
            Instant transactionAt, String counterpartyName, String description,
            String referenceNumber, String notes, String runningBalance, EntryKind entryKind,
            UUID reversalOfTransactionId, UUID correctionReferenceId,
            UUID reversedByTransactionId, UUID correctedByTransactionId, String reason,
            Instant createdAt, Instant updatedAt) {}

    public record CorrectionResponse(TransactionResponse reversal, TransactionResponse correction) {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}
}
