# LedgerMate — Personal Money Audit

LedgerMate keeps a separate credit/debit ledger for every person you deal with, always shows the
true balance, and lets you audit every rupee that moved.

* **Credit** = money received → shown as **+₹1,000.00** in **green**
* **Debit** = money paid → shown as **−₹1,000.00** in **red**
* **Balance = Opening Balance + Total Credits − Total Debits** (calculated on the server only)
* Positive balances are green, negative balances red, zero is neutral

| Layer    | Technology                                                    |
|----------|---------------------------------------------------------------|
| Frontend | React 18, Vite, TypeScript, Tailwind CSS, React Router        |
| Backend  | Java 17, Spring Boot 3.3, Spring Security (JWT), Maven        |
| Database | PostgreSQL 16 with Flyway migrations                          |
| Tests    | JUnit 5, Mockito, Spring MockMvc (backend), Vitest (frontend) |

Everything is free and open source. No paid services or cloud accounts are needed.

> **Honest status:** see [docs/VERIFICATION.md](docs/VERIFICATION.md). The project was written in
> an environment that could not download Maven or npm packages, so `mvn test`, `npm run build` and
> the Vitest suite were **not run** before delivery. Please run them first (steps 7 and 9 below);
> that is the quickest way to confirm everything works on your machine.

---

## 1. What you need to install (Windows)

| Tool | Version | Download |
|------|---------|----------|
| JDK (Temurin) | 17 or newer | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org/download.cgi (or `winget install Apache.Maven`) |
| Node.js LTS | 20 or newer | https://nodejs.org |
| PostgreSQL | 16 (14+ works) | https://www.postgresql.org/download/windows/ — **or** Docker Desktop (see step 3, option B) |
| VS Code | latest | https://code.visualstudio.com |

Recommended VS Code extensions (VS Code will offer them automatically): *Extension Pack for Java*,
*Spring Boot Extension Pack*, *ESLint*, *Tailwind CSS IntelliSense*.

Check the tools in a **new** terminal (VS Code → Terminal → New Terminal):

```powershell
java -version
mvn -version
node -v
npm -v
```

## 2. Open the project

1. Unzip `LedgerMate_Complete_Project.zip`.
2. VS Code → **File → Open Folder…** → choose the `LedgerMate` folder.

## 3. Set up PostgreSQL

### Option A — PostgreSQL installed on Windows

Open a terminal and run (replace the password; keep it for step 4):

```powershell
psql -U postgres -v db_password='ChooseAStrongPasswordHere' -f scripts/setup-database.sql
```

If `psql` is not recognised, use the *SQL Shell (psql)* from the Start menu, or add
`C:\Program Files\PostgreSQL\16\bin` to your PATH. The script creates the user `ledgermate` and the
database `ledgermate`. The tables are created automatically by Flyway when the backend starts.

### Option B — Docker Desktop (no PostgreSQL install)

After creating your `.env` file (step 4):

```powershell
docker compose up -d
```

## 4. Create your `.env` file

1. Copy `.env.example` to `.env` (same folder):

   ```powershell
   Copy-Item .env.example .env
   ```
2. Generate a signing secret (works in Windows PowerShell and PowerShell 7):

   ```powershell
   $b = New-Object byte[] 48; (New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($b); [Convert]::ToBase64String($b)
   ```
   (Alternative: `node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"`)
3. Edit `.env`:
   * `DB_PASSWORD` → the password from step 3
   * `JWT_SECRET` → the text generated above
   * For the demo data: set `DEMO_DATA_ENABLED=true` and `DEMO_PASSWORD` to a 10+ character password.

`.env` is git-ignored and must never be shared. The backend refuses to start when `JWT_SECRET` is
missing, not Base64, or shorter than 32 bytes.

## 5. Run the backend

```powershell
cd backend
mvn spring-boot:run
```

Wait for `Started LedgerMateApplication`. The API is at http://localhost:8080 and the interactive
API explorer at http://localhost:8080/swagger-ui.html.

## 6. Run the frontend (second terminal)

```powershell
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. Create an account (or sign in as `demo@ledgermate.local` with your
`DEMO_PASSWORD` if you enabled demo data).

## 7. Run the backend tests

```powershell
cd backend
mvn test
```

The tests use an in-memory H2 database in PostgreSQL mode, so no database setup is needed. They run
the real application (security, validation, Flyway migration, JPA). Coverage includes: the
specification's worked example (balance 5,000.00), running balances, opening balance, validation,
filters/search/paging, reversal and correction, atomic rollback, idempotency, concurrent writes,
user isolation, JWT tampering/expiry, CSRF, CORS, CSV export and CSV formula-injection protection.

## 8. Build the backend JAR (optional)

```powershell
cd backend
mvn clean package
```

## 9. Frontend tests and production build

```powershell
cd frontend
npm test
npm run build
```

`npm run build` type-checks (`tsc --noEmit`) and writes the static site to `frontend/dist`.

---

## Using the app

1. **Dashboard** — every person with their balance. Press **+** to create a ledger (name, optional
   phone/reference, description, opening balance, notes). A warning appears if the name already
   exists; similar names are allowed.
2. **Ledger page** — large balance at the top, **Credit** and **Debit** buttons side by side,
   totals, and the transaction history with search, type/date/amount filters, sorting, paging and a
   running balance on every row.
3. **Credit / Debit form** — amount (must be > 0, max 2 decimals), date and time, who the money
   came from / went to, description, optional reference and notes.
4. **Corrections** — financial entries are never deleted or edited. Use **Reverse** (posts an equal
   opposite entry) or **Correct** (reverses the wrong entry and posts the right one in a single
   database transaction). Descriptions and notes can be edited; every change is audited.
5. **Reports** — period summary (opening, credits, debits, closing), monthly breakdown, statement,
   **Export CSV**, print view.
6. **Archive** a ledger to hide it; its history is kept and it can be restored.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `JWT_SECRET is too short` / `must be a Base64 string` | Regenerate the secret as in step 4 and paste it into `.env` without quotes. |
| `Could not resolve placeholder 'DB_PASSWORD'` | `.env` is missing or not in the project root. Copy `.env.example` to `.env`. |
| `password authentication failed for user "ledgermate"` | `DB_PASSWORD` differs from the password given to `setup-database.sql` (or the Docker volume was created with an older password: `docker compose down -v`). |
| `Connection refused` on port 5432 | PostgreSQL is not running. Start the service (`services.msc` → postgresql-x64-16) or `docker compose up -d`. |
| `Port 8080 is already in use` | Set `SERVER_PORT=8081` in `.env` and start the frontend with `$env:VITE_BACKEND_URL="http://localhost:8081"; npm run dev`. |
| `release version 17 not supported` | Maven is using an older JDK. Set `JAVA_HOME` to JDK 17+ and open a new terminal. |
| Login works but every action returns 403 | The browser must call the API through the Vite dev server (http://localhost:5173), not by opening port 8080 directly; this keeps the CSRF cookie working. |
| `npm install` fails behind a proxy | Configure `npm config set proxy` / `https-proxy`, or try another network. |
| Flyway `Validate failed` after editing a migration | Never edit an applied migration; add a new `V2__...sql`. In development you may drop and recreate the database. |

## Production notes

* Serve the built frontend and the API from the **same origin** behind HTTPS (for example a reverse
  proxy that forwards `/api` to the backend) and set `COOKIE_SECURE=true`.
* Keep `.env` out of version control; use your platform's secret store.
* Back up the PostgreSQL database regularly (`pg_dump`).

## More documentation

* [docs/API.md](docs/API.md) — endpoints, rules and sample requests
* [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — structure, design decisions and trade-offs
* [docs/VERIFICATION.md](docs/VERIFICATION.md) — exactly what was and was not run
