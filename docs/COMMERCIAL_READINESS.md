# Apotek Naufal Farma — Commercial Readiness Review

Status: architecture/readiness plan. This document does not claim that multi-user authentication or commercial compliance has already been implemented.

## Current implementation observed in repository
- Android app wraps a local HTML/JavaScript UI and native Android database bridge.
- Product data is persisted locally through native database methods.
- Cloud sync currently downloads `cloud/xReport2.csv` from a public raw GitHub URL and imports it into the local database.
- The current cloud-sync script starts automatically when the WebView/app page initializes. The inspected page architecture does not provide a dedicated login/authentication gate.
- The current repository build workflow may produce a debug APK if release signing secrets are not configured. A debug APK is for testing, not commercial distribution.
- CSS contains a Crystal Liquid/glassmorphism design language. The theme has now been adjusted to pearl white/ice/graphite while preserving translucent surfaces, blur, highlights, shadows, rounded cards, and the floating glass navigation.

## Required before a real commercial multi-user launch

### P0 — Identity, authorization, and sessions
1. Add a first-run setup flow that securely creates the first administrator; do not ship a universal default password.
2. Add separate accounts for Admin and User/Kasir, with account activation/deactivation, password/PIN reset, and a clear logout action.
3. Store password verifiers using a modern password hash (Argon2id or bcrypt with appropriate parameters); never store plaintext passwords. For a local-only PIN, use a vetted KDF and Android Keystore-backed protection where feasible.
4. Add session expiry, auto-lock after inactivity, rate limiting/backoff for failed attempts, and re-authentication for destructive or sensitive operations.
5. Enforce role checks at the operation/native bridge layer as well as in the UI. Hiding buttons alone is not authorization.
6. Admin-only actions: user management, database reset/restore, destructive imports, security settings, and access to sensitive purchase-cost/profit reports. Allow explicit permission settings if the business needs exceptions.
7. Add an audit trail for login failures, product/price changes, deletions, stock changes, exports, imports, backup/restore, and administrative actions. Record actor, timestamp, device/session, action, and affected record without logging passwords or secrets.

### P0 — Cloud data and synchronization
1. Do not use a public GitHub CSV as the authoritative commercial database if it contains non-public purchase costs, stock, sales, or business-sensitive information.
2. For multi-device/multi-user operation, use an authenticated backend (for example, a managed auth/database service or a properly secured API). Enforce authorization on the server and tenant/store-level isolation in database policies.
3. Never embed a GitHub write token, service-role key, or backend secret in the APK or JavaScript bundle. Client apps are inspectable.
4. Make sync authenticated and role-aware. Only start sync after a successful session; cancel or suppress it on logout/lock, and prevent duplicate requests.
5. Use explicit conflict rules and stable product IDs. Avoid silently overwriting newer data with stale CSV. Show last successful sync, failure reason, and retry.
6. Keep the existing safety rule: empty, malformed, or unexpectedly small cloud imports must never erase local data. Add checksums/schema versions, row-count thresholds, and a preview/confirmation for destructive imports.
7. Define offline behavior and reconciliation. Local-only data must be marked as pending sync; never imply it has been backed up until server confirmation.

### P0 — Multi-device and multi-branch operation
1. Model the business as an organization with one or more branches. Every user, device, stock location, sale, stock movement, purchase/receiving record, return, and report must have an explicit organization/branch scope where applicable.
2. Separate shared master data (for example, product identity, barcode, generic/name and units) from branch-specific data (stock on hand, purchase cost where it differs, selling-price overrides, stock minimums, and branch transactions). Do not assume that a single global stock quantity is correct across branches.
3. Define access scopes: owner/super-admin across the organization; branch admin limited to assigned branches; cashier/user limited to permitted actions and assigned branch. Server-side policies must enforce this even if a client modifies requests.
4. Register devices and associate each device with a branch. Provide device revocation, session revocation, and an audit trail for device/branch changes. A device must not switch branches silently while an active cashier session is open.
5. Support offline checkout only with durable local transaction IDs and an outbox/queue. On reconnect, sync pending sales and stock movements idempotently; never discard queued transactions because a newer snapshot arrived.
6. Define cross-device conflict rules. Treat sales and stock movements as append-only ledger events; derive stock from accepted movements rather than resolving conflicts by “last write wins” on a single stock number.
7. Add explicit inter-branch transfer workflows: request/dispatch/receive, source and destination branch, quantities, status, actor, timestamps, and audit events. Do not increase destination stock merely because a transfer was requested or dispatched.
8. Reports must be filterable by branch and organization-wide totals. Verify that a branch user cannot query another branch's sales, stock, purchase cost, or customer data by changing a client-side branch ID.
9. Define operational behavior when two devices sell the last units while offline. Show provisional/offline status and reconcile shortages transparently; no architecture can guarantee globally accurate real-time stock while devices are disconnected.
10. Use server-authoritative IDs, timestamps/versioning, schema migrations, and idempotency keys for sync. Test duplicate retries, out-of-order events, clock skew, revoked users/devices, and branch reassignment.

### P0 — Database integrity and recovery
- Use database migrations with versioned schemas and rollback/recovery plans.
- Add atomic import transactions, validation, duplicate detection, and referential-integrity checks for sales and sale items.
- Keep automatic backups and tested restore procedures; encrypt sensitive backups and avoid writing them to public folders by default.
- Add a recovery path for failed migrations, low storage, app crashes during checkout, and interrupted imports.
- Verify product edits cannot create accidental duplicates when code/barcode/name changes.
- Prevent double submission of sales and give each sale a unique durable transaction ID.

### P1 — Pharmacy and inventory workflows
- Define lot/batch number, expiry date, stock movement ledger, stock adjustments, returns, damaged/expired stock, and low-stock/near-expiry alerts if these are in scope.
- For prescription/regulated products, define workflow and access controls appropriate to the business and applicable Indonesian rules.
- Keep sale history immutable or provide audited corrections/voids instead of silent edits/deletions.
- Validate prices, quantities, discounts, totals, tax fields, and rounding rules. Test thermal receipt and A4/PDF/CSV/XLS exports against real examples.
- Clearly distinguish purchase price, selling price, margin/profit, and stock value permissions.

### P1 — Privacy, legal, and operational readiness
- Publish terms of service, privacy notice, data-retention/deletion policy, support contact, and a clear statement of what data leaves the device.
- Minimize collection of customer personal data. Avoid collecting patient/health information unless there is a defined lawful need and suitable safeguards.
- Review Indonesian requirements that apply to the actual product and deployment, including personal-data protection (UU PDP), electronic-system obligations (PSE, where applicable), pharmacy/medicine distribution rules, tax/invoicing rules, and consumer-protection requirements. Obtain qualified local legal/compliance review; this checklist is not legal advice.
- Define who can access, export, retain, and delete business/customer data; document incident response and breach notification procedures.
- Use HTTPS for backend traffic, secure Android configuration, dependency updates, release signing, and a process to rotate/revoke credentials.

### P1 — Commercial build and release
- Configure a persistent release signing key in GitHub Actions secrets and protect it with restricted access and a secure backup. Never commit the key or passwords.
- Build and test a signed release APK/AAB; debug builds must not be distributed to customers.
- Add versioned release notes, app version display, staged rollout, rollback strategy, and a repeatable QA checklist.
- Add automated tests for authentication/authorization, CRUD, CSV import, cloud sync, offline use, checkout, duplicate submissions, stock movement, PDF/thermal printing, backup/restore, and data migration.
- Test on actual target devices and printers, including small screens, app restarts, low storage, no network, slow network, and interrupted operations.
- Provide a support channel and a documented backup/restore procedure for each customer/store.

## Recommended target architecture
- Android/WebView client: Crystal Liquid UI, local database for resilient offline operations, no embedded privileged credentials.
- Authentication: managed identity or backend-issued sessions; account and role checks on every privileged operation.
- Backend: authenticated API/database with organization/branch isolation, server-side authorization, audit events, idempotent event sync, and versioned migrations.
- Multi-branch model: shared product catalog plus branch-scoped inventory/transactions; organization-wide owner access and branch-limited staff access. Cross-branch stock movement uses a transfer ledger, not direct quantity overwrites.
- Sync: authenticated incremental sync with conflict resolution and explicit success/failure states. GitHub can remain a development/build artifact source, not the production system of record for sensitive business data.
- Admin console: account lifecycle, roles, store settings, backup/restore, audit review, and integration settings.

## Theme change
The end of `tools/ui_theme_v2.css` now defines a Pearl White Crystal Liquid theme:
- white/pearl/ice backgrounds and graphite text;
- glass translucency, blur, reflective highlights, depth shadows, and rounded geometry retained;
- green-heavy header and primary buttons replaced with graphite/steel accents;
- danger states remain visually distinct.
The theme change is source-committed, but visual regression still needs APK build verification and real-device review.

## Acceptance gates before calling this commercial-ready
- [ ] First-run admin setup works without a universal default credential.
- [ ] Login/logout/lock/timeout and password/PIN reset tested.
- [ ] Unauthorized users cannot call privileged native operations directly.
- [ ] Multi-user sessions and role changes are enforced server-side for cloud deployment.
- [ ] Organization/branch isolation is verified with negative authorization tests.
- [ ] Multi-device offline sales, retry/idempotency, conflict handling, and branch transfers are tested.
- [ ] No production secrets in APK, source bundle, or public repository.
- [ ] Sync works with offline/retry/conflict scenarios without data loss.
- [ ] Sales are atomic, auditable, and protected from duplicate submission.
- [ ] Backup and restore tested from a clean installation.
- [ ] Signed release artifact built and installed on target devices.
- [ ] Privacy, legal, and operational review completed for the actual deployment.
- [ ] Crystal Liquid Pearl White theme reviewed on-device in portrait mode and at accessibility text sizes.

## Important status distinction
The white-theme CSS change is implemented in source. The multi-user login, server-side authorization, commercial backend, audit trail, and production release setup are requirements identified by this review, not completed features. They should be implemented and tested as separate steps so that existing product and transaction data are not put at risk.
