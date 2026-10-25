# LedgerMate API

Base URL: `http://localhost:8080`. Interactive explorer: `/swagger-ui.html`. All bodies are JSON.
Money values in responses are exact decimal **strings** (`"5000.00"`), never floating point numbers.

## Authentication

The web app uses an **HttpOnly, SameSite=Strict** cookie (`LM_AUTH`) plus a CSRF double-submit
cookie (`XSRF-TOKEN` → header `X-XSRF-TOKEN`) on state-changing requests.
API clients may instead send `Authorization: Bearer <token>`; such requests are not subject to CSRF.
The token is only returned in the response body when `EXPOSE_TOKEN_IN_RESPONSE=true` (off by
default; the automated tests turn it on).

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/auth/register` | `{email, password (10–72 chars), displayName?}` → 201, sets cookie |
| POST | `/api/auth/login` | `{email, password}` → 200, sets cookie. Same 401 message for unknown email / wrong password |
| POST | `/api/auth/logout` | clears the cookie → 204 |
| GET | `/api/auth/me` | current user |
| PATCH | `/api/auth/me` | `{displayName?, timezone?}` (IANA zone, e.g. `Asia/Kolkata`) |

## People (ledgers)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/people?status=active\|archived\|all` | list with server-calculated totals |
| POST | `/api/people` | create `{name, phone?, description?, openingBalance?, notes?}` → 201 |
| GET | `/api/people/overview` | totals across active ledgers |
| GET | `/api/people/duplicate-check?name=` | `{duplicate, matches}` (a warning only) |
| GET / PUT | `/api/people/{id}` | read / update |
| PATCH | `/api/people/{id}/archive` · `/restore` | hide / unhide (history kept) |
| GET | `/api/people/{id}/summary` | opening, credits, debits, current balance, count |

## Transactions

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/people/{id}/transactions` | filters: `type, from, to, counterparty, description, search, sortBy(date\|amount), direction(asc\|desc), page, size(≤100)` |
| POST | `/api/people/{id}/transactions` | post an entry; optional header `Idempotency-Key` |
| GET | `/api/transactions/{id}` | one entry |
| PATCH | `/api/transactions/{id}/description` | edit description / notes / counterparty / reference only |
| POST | `/api/transactions/{id}/reverse` | `{reason}` → posts the equal opposite entry |
| POST | `/api/transactions/{id}/corrections` | `{reason, replacement:{…}}` → reversal + replacement, one DB transaction |

Create body: `{"type":"CREDIT|DEBIT","amount":"1000.00","transactionDate":"2026-10-22","transactionTime":"10:00","counterpartyName":"Ravi","description":"…","referenceNumber":"…","notes":"…"}`

Rules: amount > 0 with at most 2 decimals; counterparty required; dates are calendar dates in the
user's timezone and cannot be in the future; amount/type/date of a posted entry are immutable.
Each response row includes `signedAmount` (`"-1500.00"` for debits) and `runningBalance`, which is
computed over the **whole** ledger even when a filter is applied.

## Reports

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/people/{id}/reports/summary?from&to` | period opening, credits, debits, closing, monthly breakdown |
| GET | `/api/people/{id}/reports/transactions?…` | statement rows (same filters as above) + summary |
| GET | `/api/people/{id}/reports/export?…` | CSV download (UTF-8 with BOM; formula-injection safe) |

## Errors

```json
{ "timestamp": "…", "status": 400, "error": "Bad Request",
  "message": "Validation failed. Please check the highlighted fields.",
  "path": "/api/people/…/transactions",
  "fieldErrors": [ { "field": "amount", "message": "Amount must be greater than zero" } ] }
```

400 validation/malformed · 401 not authenticated · 403 CSRF · 404 not found **or not yours** ·
409 conflict (already reversed, archived, duplicate email) · 422 business rule · 500 generic message
(details only in server logs).

## Sample requests (PowerShell, bearer-token mode)

Start the backend with `EXPOSE_TOKEN_IN_RESPONSE=true` for these examples.

```powershell
$base = "http://localhost:8080"
$r = Invoke-RestMethod "$base/api/auth/register" -Method Post -ContentType "application/json" `
     -Body '{"email":"me@example.com","password":"CorrectHorse-9-Battery"}'
$h = @{ Authorization = "Bearer $($r.accessToken)" }

$p = Invoke-RestMethod "$base/api/people" -Method Post -Headers $h -ContentType "application/json" `
     -Body '{"name":"Person A","openingBalance":"0"}'

Invoke-RestMethod "$base/api/people/$($p.id)/transactions" -Method Post -Headers $h `
     -ContentType "application/json" `
     -Body '{"type":"CREDIT","amount":"5000","transactionDate":"2026-10-22","transactionTime":"10:00","counterpartyName":"Ravi","description":"Money received from Ravi"}'

Invoke-RestMethod "$base/api/people/$($p.id)/summary" -Headers $h
Invoke-WebRequest "$base/api/people/$($p.id)/reports/export" -Headers $h -OutFile ledger.csv
```

curl equivalent:

```bash
curl -s -X POST localhost:8080/api/people/$PID/transactions \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"DEBIT","amount":"1500","transactionDate":"2026-10-22","counterpartyName":"Suresh"}'
```
