-- LedgerMate schema. Written to run unchanged on PostgreSQL (production) and H2 in
-- PostgreSQL mode (automated tests). All money columns are NUMERIC(19,2).

CREATE TABLE users (
    id            UUID                     NOT NULL,
    email         VARCHAR(254)             NOT NULL,
    display_name  VARCHAR(100),
    password_hash VARCHAR(100)             NOT NULL,
    timezone      VARCHAR(64)              NOT NULL DEFAULT 'Asia/Kolkata',
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email))
);

CREATE TABLE people (
    id              UUID                     NOT NULL,
    owner_id        UUID                     NOT NULL,
    name            VARCHAR(120)             NOT NULL,
    phone           VARCHAR(30),
    description     VARCHAR(255),
    opening_balance NUMERIC(19, 2)           NOT NULL DEFAULT 0,
    notes           VARCHAR(2000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_people PRIMARY KEY (id),
    CONSTRAINT fk_people_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    -- lets transactions reference (person, owner) together so a transaction can never
    -- point at a ledger that belongs to a different user
    CONSTRAINT uq_people_id_owner UNIQUE (id, owner_id),
    CONSTRAINT ck_people_name_not_blank CHECK (length(trim(name)) > 0)
);

CREATE INDEX idx_people_owner ON people (owner_id);
CREATE INDEX idx_people_owner_name ON people (owner_id, name);

CREATE TABLE transactions (
    id                         UUID                     NOT NULL,
    person_id                  UUID                     NOT NULL,
    owner_id                   UUID                     NOT NULL,
    transaction_type           VARCHAR(10)              NOT NULL,
    amount                     NUMERIC(19, 2)           NOT NULL,
    transaction_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    counterparty_name          VARCHAR(120)             NOT NULL,
    description                VARCHAR(500),
    reference_number           VARCHAR(60),
    notes                      VARCHAR(2000),
    entry_kind                 VARCHAR(12)              NOT NULL DEFAULT 'NORMAL',
    reversal_of_transaction_id UUID,
    correction_reference_id    UUID,
    reason                     VARCHAR(500),
    idempotency_key            VARCHAR(64),
    created_by                 UUID                     NOT NULL,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_tx_person_owner FOREIGN KEY (person_id, owner_id) REFERENCES people (id, owner_id),
    CONSTRAINT fk_tx_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_tx_reversal_of FOREIGN KEY (reversal_of_transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_tx_correction_ref FOREIGN KEY (correction_reference_id) REFERENCES transactions (id),
    CONSTRAINT ck_tx_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_tx_type CHECK (transaction_type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_tx_entry_kind CHECK (entry_kind IN ('NORMAL', 'REVERSAL', 'CORRECTION')),
    CONSTRAINT ck_tx_counterparty_not_blank CHECK (length(trim(counterparty_name)) > 0),
    CONSTRAINT ck_tx_reversal_link CHECK (
        (entry_kind = 'REVERSAL' AND reversal_of_transaction_id IS NOT NULL)
        OR (entry_kind <> 'REVERSAL' AND reversal_of_transaction_id IS NULL)),
    CONSTRAINT ck_tx_correction_link CHECK (
        (entry_kind = 'CORRECTION' AND correction_reference_id IS NOT NULL)
        OR (entry_kind <> 'CORRECTION' AND correction_reference_id IS NULL)),
    -- a transaction can be reversed at most once, and corrected at most once
    CONSTRAINT uq_tx_reversal_of UNIQUE (reversal_of_transaction_id),
    CONSTRAINT uq_tx_correction_ref UNIQUE (correction_reference_id),
    -- idempotency keys are unique per user (NULL keys are allowed any number of times)
    CONSTRAINT uq_tx_owner_idempotency UNIQUE (owner_id, idempotency_key)
);

CREATE INDEX idx_tx_person_at ON transactions (person_id, transaction_at);
CREATE INDEX idx_tx_owner ON transactions (owner_id);
CREATE INDEX idx_tx_person_type ON transactions (person_id, transaction_type);

CREATE TABLE audit_events (
    id              UUID                     NOT NULL,
    owner_id        UUID                     NOT NULL,
    entity_type     VARCHAR(40)              NOT NULL,
    entity_id       UUID                     NOT NULL,
    action          VARCHAR(40)              NOT NULL,
    previous_values VARCHAR(4000),
    new_values      VARCHAR(4000),
    reason          VARCHAR(500),
    actor           VARCHAR(254)             NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_audit_events PRIMARY KEY (id),
    CONSTRAINT fk_audit_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_audit_owner_entity ON audit_events (owner_id, entity_type, entity_id);
CREATE INDEX idx_audit_owner_created ON audit_events (owner_id, created_at);
