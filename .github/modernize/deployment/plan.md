# LedgerMate public deployment plan

## Goal and scope
Prepare opt-in production container artifacts for deploying LedgerMate to a user-provisioned Oracle Cloud Infrastructure (OCI) Always Free eligible VM, using a DuckDNS subdomain and Caddy-managed HTTPS. No cloud resources will be created, no accounts logged into, no secrets requested, and no local `.env` or database data inspected, copied, or migrated. The production stack starts an empty PostgreSQL volume and Flyway creates the schema.

This is a best-effort free hosting option, not a guarantee of zero charges, continuous availability, or permanent free service. OCI eligibility, home-region capacity, policy/terms and pricing must be rechecked by the user before creation. OCI documents Always Free compute and storage limits but notes idle instance reclamation and capacity constraints; account sign-up can require a payment card. DuckDNS provides a free provider-controlled subdomain, not a custom domain owned by the user. Let's Encrypt certificates are free; Caddy needs public DNS pointing to the VM and inbound TCP 80/443.

## Application findings
- Backend: Java 17 / Spring Boot 3.3.5; Maven package produces executable JAR. PostgreSQL schema is Flyway-owned. `SERVER_PORT` is configurable. Current JDBC URL defaults to localhost; adapt it to permit `DB_HOST`, `DB_PORT`, and `DB_NAME` while preserving `DB_URL` override and local defaults.
- Frontend: React + TypeScript + Vite; browser calls relative `/api` paths and uses same-origin credentials plus the readable CSRF cookie. Serve frontend and API on one hostname through Caddy so the HttpOnly `SameSite=Strict` auth cookie and CSRF flow remain same-origin.
- Existing root `docker-compose.yml` is local-development PostgreSQL-only and must not be repurposed or break.
- `.gitignore` already excludes `.env` and `.env.*`; production example template must be explicitly unignored, without adding any real values.

## Planned files
1. `backend/Dockerfile` — multi-stage Maven build and minimal Java 17 runtime, non-root.
2. `frontend/Dockerfile` — Node build stage and unprivileged Nginx static runtime.
3. `frontend/nginx.conf` — SPA fallback for client-side routes.
4. `docker-compose.production.yml` — production-only PostgreSQL, backend, frontend, Caddy services; persistent named PostgreSQL/Caddy data; only Caddy publishes 80/443; no database or backend host ports.
5. `Caddyfile` — same-origin `/api` and API-doc routes to Spring, all other paths to React; automatic HTTPS for configured hostname.
6. `.dockerignore` — exclude local config/data and database artifacts, build outputs, VCS metadata, dependencies and setup scripts from image contexts.
7. `.env.production.example` — placeholder-only template, explicitly tracked despite `.env.*` ignore rule. User creates ignored `.env.production` locally.
8. `.gitignore` — allow only the production example template.
9. `backend/src/main/resources/application.yml` — configurable DB host/port/name fallback, without changing local defaults.
10. `README.md` — deployment prerequisites and stepwise user-run setup; explicit no-migration, backup, security, billing, availability and provider subdomain caveats.

## Execution and validation
- Do not run Docker commands or build images; no container-tool configuration was supplied. Do not log into OCI/DuckDNS or create resources.
- Do not read local `.env` files or database contents and do not copy/migrate local data.
- Keep existing local Compose workflow and local application defaults intact.
- Validate Docker/YAML/Caddy files by static inspection only; run backend unit tests (`mvn test`) and frontend tests/typecheck/build if dependencies already exist and commands do not require Docker or credentials. Record any unrelated existing test failures without changing application behavior.
- Review final diff for accidental secrets, local data, or unrelated changes.

## User-owned steps after artifacts are ready
1. Verify current OCI Always Free eligibility, exact resource labels/quotas/region and billing, card verification requirements, public-IP treatment, and idle reclamation terms; choose only resources clearly marked Always Free. Ensure budget alerts and avoid paid extras.
2. Create and secure the VM/networking yourself; allow inbound TCP 80 and 443 and SSH only from trusted addresses. Confirm Docker/Compose installation for the chosen OS/architecture.
3. Register a DuckDNS provider subdomain and update it to the VM public IPv4 address. This name is not an owned custom domain and can be subject to provider terms/availability.
4. On the VM, clone the repository, create a private `.env.production` from the placeholder template, generate strong unique `DB_PASSWORD` and Base64 `JWT_SECRET` values locally, set `PUBLIC_HOSTNAME` to the owned DuckDNS hostname, and set `COOKIE_SECURE=true`. Never send secrets to the assistant or commit them.
5. Start the production Compose stack manually. Caddy obtains/renews HTTPS automatically once DNS and ports are correct. Create a new account; no local database records are migrated.
6. Establish encrypted off-VM PostgreSQL backups and test restore. A persistent volume is not a backup. Monitor provider usage and availability; free-tier instances may be reclaimed and services/terms can change.

## Verification results
- Backend Maven compilation: passed; `mvn test` executed and reported a failure in `LedgerFlowApiTest.similarNamesAreAllowedAndTheDuplicateCheckWarns` (expected `duplicate=true`, actual false). The unrelated application behavior was not modified.
- Frontend Vitest: passed, 16 tests across 3 files.
- Frontend production build: passed (`tsc --noEmit` and Vite build).
- Docker builds, Compose validation, live TLS/domain, cloud resources and database migration: not run by design; no container tool configuration was available and the user explicitly prohibited cloud/account operations.
- No local `.env` or database content accessed. No production data migration.
