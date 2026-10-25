# Architecture

## Layout

```
LedgerMate/
├── backend/                      Spring Boot 3 / Java 17 / Maven
│   └── src/main/java/com/ledgermate/
│       ├── controller/           HTTP only: parse, validate, delegate
│       ├── service/              business rules and transactions (+ LedgerCalculator: pure maths)
│       ├── repository/           Spring Data JPA, ownership-scoped queries
│       ├── entity/               JPA entities (users, people, transactions, audit events)
│       ├── dto/                  request/response records with Bean Validation
│       ├── security/             JWT, cookie, CSRF, CORS, filter chain
│       ├── report/               CsvExporter (pure)
│       ├── exception/            typed errors + one GlobalExceptionHandler
│       └── config/               typed properties, optional demo data
│   └── src/main/resources/db/migration/V1__create_schema.sql
├── frontend/                     React + Vite + TypeScript + Tailwind
│   └── src/{pages,components,lib}
├── scripts/setup-database.sql    one-off DB/user creation
├── docker-compose.yml            optional PostgreSQL only
└── docs/
```

Request flow: `React → /api (Vite proxy) → Controller → Service (@Transactional) → Repository → PostgreSQL`.
The accounting rules live in `LedgerCalculator` and `CsvExporter`, which have no framework
dependencies, so they are unit-tested in isolation (a light take on hexagonal layering: the domain
rules do not know about HTTP or JPA).

## Key decisions and trade-offs

| Decision | Why | Trade-off |
|----------|-----|-----------|
| **Balances are derived, never stored** | A stored balance can drift from the entries. Totals come from `SUM` queries; running balances from the full ordered ledger. | Reading a ledger loads all its rows (fine for personal use; add a snapshot table if a single ledger reaches ~100k rows). |
| **`NUMERIC(19,2)` + `BigDecimal`** | Exact money maths; `0.1 + 0.2 = 0.30`. Amounts travel as strings, so JavaScript never rounds them. | Slightly more verbose code. |
| **Amount stored positive + type** | Avoids sign mistakes; `CHECK (amount > 0)` enforces it in the database too. | Signed value computed on read. |
| **Immutable posted entries** | `type/amount/date/ledger/owner` columns are `updatable=false`; fixes happen through reversal or correction, linked and audited. | Users post two entries instead of editing one. |
| **Correction in one DB transaction** | Reversal + replacement + audit rows commit or roll back together (tested). | — |
| **Row lock per ledger (`SELECT … FOR UPDATE`)** | Serialises writers on the same ledger, so idempotency checks and writes cannot interleave. | Writers on one ledger queue; independent ledgers do not block each other. |
| **Idempotency-Key header** | A double click or retry cannot post twice; unique `(owner_id, idempotency_key)` is the backstop. | Clients must send the key (the web form does). |
| **Composite FK `(person_id, owner_id)`** | The database itself refuses a transaction that points at another user's ledger. | Extra unique constraint on `people`. |
| **404 instead of 403 for foreign records** | Does not reveal whether another user's id exists. | — |
| **JWT in HttpOnly SameSite=Strict cookie + CSRF token** | Scripts (and XSS) cannot read the session token. | Needs same-origin (Vite proxy in dev, reverse proxy in production). |
| **Bearer header also accepted** | Easy API testing; immune to CSRF by construction. | Token only exposed when explicitly enabled. |
| **Time zone per user** | "A day" in filters, reports and CSV means the user's calendar day. Instants are stored in UTC. | Default `Asia/Kolkata`; changeable in Settings. |
| **Flyway owns the schema** | `ddl-auto=none`; reproducible, reviewable changes. | Schema changes require a new migration file. |
| **H2 (PostgreSQL mode) for tests** | Tests run anywhere without a database. | Not identical to PostgreSQL; the migration is also written to run on both. |

## Security summary

BCrypt (cost 12) password hashes; constant-time-ish login (dummy hash for unknown emails) with one
generic error; JWT signature, issuer and expiry validated, secret required from the environment and
length-checked at start-up; CSRF double-submit + SameSite=Strict; CORS limited to configured origins;
every repository query is scoped by `owner_id`; Bean Validation on all inputs; generic 500 messages
with no stack traces or SQL; CSV formula-injection neutralisation; audit trail without secrets;
no secrets in the repository (`.env.example` holds placeholders only).

Not included (documented limitations): login rate limiting / account lockout, email verification,
password reset, multi-factor authentication. Add a reverse-proxy rate limit or Bucket4j before
exposing the app to the public internet.

## Database

`users` · `people` (ledger, opening balance, archive flag) · `transactions` (append-only entries with
`entry_kind` NORMAL / REVERSAL / CORRECTION and links to the entry they offset) · `audit_events`
(who did what, previous/new values, reason). Indexes cover owner, `(person_id, transaction_at)` and
`(person_id, transaction_type)`.
