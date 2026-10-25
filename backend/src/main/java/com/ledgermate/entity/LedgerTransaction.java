package com.ledgermate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A posted credit or debit. The amount is stored positive; {@link #getTransactionType()} decides
 * the direction. Type, amount, ledger and owner are immutable once saved (updatable = false) so
 * a financial entry can only be changed by posting a reversal or correction.
 */
@Entity
@Table(name = "transactions")
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, updatable = false, length = 10)
    private TransactionType transactionType;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_at", nullable = false, updatable = false)
    private Instant transactionAt;

    @Column(name = "counterparty_name", nullable = false, length = 120)
    private String counterpartyName;

    @Column(length = 500)
    private String description;

    @Column(name = "reference_number", length = 60)
    private String referenceNumber;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_kind", nullable = false, updatable = false, length = 12)
    private EntryKind entryKind = EntryKind.NORMAL;

    @Column(name = "reversal_of_transaction_id", updatable = false)
    private UUID reversalOfTransactionId;

    @Column(name = "correction_reference_id", updatable = false)
    private UUID correctionReferenceId;

    @Column(length = 500, updatable = false)
    private String reason;

    @Column(name = "idempotency_key", length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerTransaction() {}

    public LedgerTransaction(UUID personId, UUID ownerId, UUID createdBy, TransactionType type,
                             BigDecimal amount, Instant transactionAt, String counterpartyName) {
        this.personId = personId;
        this.ownerId = ownerId;
        this.createdBy = createdBy;
        this.transactionType = type;
        this.amount = amount;
        this.transactionAt = transactionAt;
        this.counterpartyName = counterpartyName;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPersonId() { return personId; }
    public UUID getOwnerId() { return ownerId; }
    public TransactionType getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public Instant getTransactionAt() { return transactionAt; }
    public String getCounterpartyName() { return counterpartyName; }
    public void setCounterpartyName(String counterpartyName) { this.counterpartyName = counterpartyName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public EntryKind getEntryKind() { return entryKind; }
    public void setEntryKind(EntryKind entryKind) { this.entryKind = entryKind; }
    public UUID getReversalOfTransactionId() { return reversalOfTransactionId; }
    public void setReversalOfTransactionId(UUID id) { this.reversalOfTransactionId = id; }
    public UUID getCorrectionReferenceId() { return correctionReferenceId; }
    public void setCorrectionReferenceId(UUID id) { this.correctionReferenceId = id; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String key) { this.idempotencyKey = key; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
