package com.ledgermate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only audit record. Never contains passwords, tokens or other secrets. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(nullable = false, updatable = false, length = 40)
    private String action;

    @Column(name = "previous_values", updatable = false, length = 4000)
    private String previousValues;

    @Column(name = "new_values", updatable = false, length = 4000)
    private String newValues;

    @Column(updatable = false, length = 500)
    private String reason;

    @Column(nullable = false, updatable = false, length = 254)
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {}

    public AuditEvent(UUID ownerId, String entityType, UUID entityId, String action,
                      String previousValues, String newValues, String reason, String actor) {
        this.ownerId = ownerId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.previousValues = previousValues;
        this.newValues = newValues;
        this.reason = reason;
        this.actor = actor;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getAction() { return action; }
    public String getPreviousValues() { return previousValues; }
    public String getNewValues() { return newValues; }
    public String getReason() { return reason; }
    public String getActor() { return actor; }
    public Instant getCreatedAt() { return createdAt; }
}
