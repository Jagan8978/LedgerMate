# LedgerMate deployment modernization summary

## What was prepared
- Added a separate production Compose deployment with PostgreSQL persistence, private backend/frontend services, and Caddy as the only published ingress.
- Added multi-stage backend/frontend Dockerfiles, an unprivileged Nginx static server with SPA fallback, Caddy reverse-proxy routing, and Docker build-context exclusions for local env/data/build artifacts.
- Added a placeholder-only `.env.production.example`, explicitly allowlisted by `.gitignore`; no actual secrets were created or handled.
- Made JDBC defaults configurable through `DB_HOST`, `DB_PORT`, and `DB_NAME` while retaining the existing `DB_URL` override and local defaults.
- Documented the conditional OCI Always Free + DuckDNS + Let's Encrypt/Caddy option, user-operated setup, new empty schema only, backups, and availability/billing/security caveats.

## Validation
- Backend Maven compilation passed.
- Backend tests executed; one test failed: `LedgerFlowApiTest.similarNamesAreAllowedAndTheDuplicateCheckWarns` expected `duplicate=true`, actual `false`. This behavior is outside deployment scope and was not modified.
- Frontend tests passed: 16/16 across 3 files.
- Frontend TypeScript check and Vite production build passed.
- Editor reports existing YAML schema diagnostics for `time_zone`, custom `app` properties, and dotted logger key; these are not compilation failures and are unrelated to the DB URL change.
- No Docker commands/builds, Compose runtime validation, cloud sign-in/provisioning, DNS registration, live TLS test, or database access/migration were performed. No container-tool configuration was provided, and cloud/account operations were explicitly prohibited.

## Remaining user actions and limitations
- Confirm current OCI eligibility, exact Always Free resource labels, availability, card verification, public networking/IP charges, and current terms yourself before creating resources. A1 capacity may be unavailable; idle instances can be reclaimed. Use only clearly free resources and monitor billing.
- Create and secure the OCI VM yourself, open inbound TCP 80/443, restrict SSH, and install Docker/Compose. A 1 GB Micro VM is not adequate for this multi-container stack; prefer an available Always Free A1 configuration with sufficient CPU/RAM (documented target is 2 OCPUs / 12 GB, subject to capacity).
- Register and configure your own DuckDNS subdomain and supply the hostname through a private `.env.production`; it is a provider subdomain, not a custom domain you own. Generate and store unique database/JWT secrets yourself. Never share or commit that file.
- Start the stack only after DNS and firewall are ready. Caddy requires public reachability on 80/443 to issue and renew TLS. Establish encrypted off-VM backups and test restores.
- This is best-effort free hosting, not a guarantee of ongoing zero cost, permanence, uptime, data durability, or support. App-level login rate limiting/MFA are not present; public exposure remains a security risk.
- The production database is a new empty volume and Flyway creates its schema; local records were not accessed or copied. Create a fresh production user.

## Version control
Committed on branch `modernize/java-20261025162016` as `f1542663516ce4fd4b116eaa6b983e6153ac3b22`.
