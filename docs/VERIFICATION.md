# Verification report

Written honestly: this lists what was actually executed before the ZIP was created.

## Environment limitation

The build environment's network policy blocked **Maven Central** (`repo.maven.apache.org` → 403)
and the **npm registry** (DNS failure). Dependencies therefore could not be downloaded, and Docker
had no running daemon. As a result the following could **not** be run:

| Step | Status |
|------|--------|
| `mvn package` / compile of the Spring Boot code | **NOT RUN** |
| `mvn test` (JUnit, Mockito, MockMvc integration, concurrency tests) | **NOT RUN** |
| `npm install`, `tsc`, `npm run build` | **NOT RUN** |
| `npm test` (Vitest component tests) | **NOT RUN** |
| Starting the full application and clicking through the UI | **NOT RUN** |

The test sources are included and expected to pass, but they have never been executed. If any
fails on your machine, the failure message points at the exact assertion; the most likely causes of a
first-run problem are small compile-level slips in code that could not be compiled here.

## What WAS executed

Re-run and re-checked immediately before packaging (PostgreSQL 16.x, OpenJDK 21 compiling with
`--release 17`, Node 22):

1. **Flyway migration `V1__create_schema.sql` on a real PostgreSQL 16 server** - applied with no
   errors; the four tables (`users`, `people`, `transactions`, `audit_events`) exist afterwards.
2. **Database constraints on PostgreSQL 16** - valid user, ledger and credit rows were inserted, then
   eight invalid statements were each rejected **by the expected named constraint**:
   `ck_tx_amount_positive` (zero and negative amount), `ck_tx_type` (unknown type),
   `ck_tx_counterparty_not_blank`, `fk_tx_person_owner` (transaction pointing at another user's
   ledger), `ck_tx_reversal_link` (reversal without a link), `ck_users_email_lower` (upper-case
   email) and `uq_tx_owner_idempotency` (duplicate idempotency key).
3. **Pure Java logic compiled with `javac --release 17`** (`LedgerCalculator`, `CsvExporter`,
   `Money`, `Text`, `TransactionType`) and run through a small throw-away harness: **10 checks
   passed, 0 failed** (specification example 5000 -> 3500 -> 5500 -> 5000, balance 5000.00,
   opening balance, debit sign, exact decimals 0.10 + 0.20 = 0.30, CSV formula neutralisation,
   quoting, null cell and the CSV header/row layout). The JUnit versions of these tests are in
   `backend/src/test` but have **not** been run under Maven.
4. **Frontend money formatting** (`frontend/src/lib/money.ts`) run on Node 22: credit
   `+₹1,000.00`, debit `−₹1,000.00` (true minus sign), Indian digit grouping (`₹12,34,567.50`),
   negative balance `−₹300.00`, tone classification (positive / negative / zero) and amount-input
   validation - **10 checks passed**. The Vitest component tests were **not** run.
5. **Route consistency** - API paths called by the frontend were matched against the controllers.
6. **Static review** of the Spring code against the test expectations (imports, signatures, status
   codes, JSON shapes). This is a read-through, not a compiler. It found and fixed one defect: an
   anonymous `POST` without credentials was answered 403 by the CSRF filter instead of 401;
   `SecurityConfig` now answers 401 when no credentials are present and 403 only when a session
   cookie arrives with a missing/invalid CSRF token.

## Please run first

```powershell
cd backend ; mvn test
cd ../frontend ; npm install ; npm test ; npm run build
```
