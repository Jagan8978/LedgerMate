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

## Optional public deployment (OCI Always Free, best effort)

The repository includes an opt-in production stack in `docker-compose.production.yml`; it does not
change the local PostgreSQL-only `docker-compose.yml`. This option uses one user-provisioned OCI VM,
DuckDNS for a provider-controlled free `duckdns.org` subdomain, Caddy for automatic HTTPS, and a
persistent PostgreSQL volume. The DuckDNS name is **not a custom domain you own**. OCI's current
Always Free terms and eligibility, home-region capacity, account verification, resource limits,
public-IP treatment, and pricing can change. Oracle documents possible reclamation of idle Always
Free compute instances and limited capacity; therefore this is not guaranteed to remain free,
available, or permanent. Sign-up may require a payment card. Check the live official terms and
console labels before creating anything, and do not select paid resources.

Official references (checked October 2026):

* [OCI Always Free resource limits and reclamation](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
* [OCI Free Tier FAQ and signup requirements](https://www.oracle.com/cloud/free/faq/)
* [DuckDNS terms](https://www.duckdns.org/tac.jsp) and [DNS update specification](https://www.duckdns.org/spec.jsp)
* [Caddy automatic HTTPS requirements](https://caddyserver.com/docs/automatic-https)
* [Let's Encrypt documentation](https://letsencrypt.org/docs/)

### User-operated setup

1. Create and secure an OCI Always Free-eligible Linux VM yourself. Choose only resources the OCI
   console explicitly marks Always Free; check that boot volume and networking stay within the
   current free allocations. For this stack, prefer the Ampere A1 allowance (up to 2 OCPUs / 12 GB
   total, subject to availability); the small AMD Micro VM has only 1 GB and is not a suitable target
   for this full Compose stack. Set a budget alert if available. Allow inbound TCP 80 and 443, and
   restrict SSH to your own trusted IP addresses. Do not expose PostgreSQL or Spring's port directly.
2. Register your DuckDNS subdomain and update its IPv4 address to the VM's public IPv4 address.
   Confirm DNS has propagated before starting Caddy. The service and hostname remain subject to
   DuckDNS terms and availability.
3. Install Docker Engine and the Compose plugin on the VM using that operating system's official
   instructions. Clone this repository. Copy `.env.production.example` to `.env.production` on the
   VM; edit it locally there and never send or commit it. Use unique strong values for
   `DB_PASSWORD` and `JWT_SECRET` (Base64 encoding at least 32 random bytes), set
   `PUBLIC_HOSTNAME` to your DuckDNS hostname, and keep `COOKIE_SECURE=true` and
   `DEMO_DATA_ENABLED=false`. Do not reuse local development secrets.
4. From the repository root, start with
   `docker compose --env-file .env.production -f docker-compose.production.yml up -d --build`.
   Caddy requires the hostname to resolve to this VM and ports 80/443 to be reachable to obtain and
   renew a publicly trusted certificate. Visit `https://<your-duckdns-hostname>` and register a new
   user account.
5. Back up PostgreSQL off the VM, encrypt the backups, and test restoring them. The named volume is
   persistence, not a backup. Monitor OCI billing/usage and check the VM periodically. If OCI
   reclaims or stops the VM, the app may go offline; there is no uptime guarantee or free support
   SLA.

**Database safety:** production starts a new, empty PostgreSQL volume and Flyway creates only the
schema. No local database records are copied or migrated. Because your local database has no
meaningful records, create a fresh production account and enter data there. Keep the local database
and its backup separate.

**Public exposure warning:** this app currently has no login rate limiting, account lockout, or
multi-factor authentication. Public internet exposure increases risk. Use unique credentials,
keep the host patched, monitor logs, and consider adding tested rate limiting before relying on the
service for sensitive financial records. A free VM and a single local volume are not a resilient or
backed-up production service.

## More documentation

* [docs/API.md](docs/API.md) — endpoints, rules and sample requests
* [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — structure, design decisions and trade-offs
* [docs/VERIFICATION.md](docs/VERIFICATION.md) — exactly what was and was not run
