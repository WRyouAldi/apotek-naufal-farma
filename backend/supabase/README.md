# Supabase backend foundation (not yet connected to the APK)

This folder defines the initial PostgreSQL schema for **one organization: Apotek Naufal Farma**, multiple branches, and many Android devices.

## Important status

- `migrations/0001_multi_branch_core.sql` defines the proposed core schema and row-level security (RLS).
- `migrations/0002_transactional_operations.sql` adds server-side atomic checkout, stock receipt/adjustment, and transfer request/dispatch/receive functions. Direct client writes to transaction and inventory-ledger tables are revoked so clients must use these functions.
- It has **not** been applied to a hosted database and has not been executed against a live PostgreSQL instance in this repository workflow.
- The Android app is **not yet connected** to Supabase. Existing app data remains in the current local SQLite database.
- No branch names or initial stock balances are invented or seeded.
- Do not call the system multi-branch-ready until backend authorization, transactional stock/sales APIs, and APK integration have been implemented and tested.

## Before applying this migration

1. Create a dedicated Supabase project for Apotek Naufal Farma and restrict project/dashboard access to trusted administrators.
2. Review the SQL with a PostgreSQL/Supabase environment. Apply it first to a disposable staging project, not directly to production.
3. Create the actual branch records with unique codes and confirm the initial Admin Pusat identity.
4. Create the first Supabase Auth user through the trusted dashboard or a private server-side admin process. Add its row to `public.profiles` with the seeded organization UUID, `role='owner'`, and `branch_id=NULL`. Never add a default password or service-role key to the repository/APK.
5. Create branch users only after branch assignments and permissions are approved.
6. Review both SQL migrations in a disposable staging project and run transactional RPC tests. Migration 0002 includes checkout, stock receipt/adjustment, and transfer request/dispatch/receive. Sale voids, returns/refunds, customer handling, full offline outbox replay, and end-to-end client sync still need implementation and tests.
7. Test RLS using separate owner, branch-admin, cashier, and inventory accounts. Verify a branch user cannot read or change another branch by changing a branch ID in a request.
8. Configure backups, restore tests, monitoring, and incident response before production use.

## Data migration safety

The existing APK has a local `products.stok` field. That field does not identify which physical branch owns the quantity. Therefore, the migration intentionally does not copy the old stock number into every branch (or silently assign it to a guessed branch).

Before importing current data:
- take and verify a backup of the existing app database and product CSV;
- confirm which branch the current stock belongs to;
- map existing product codes/barcodes to central product IDs;
- import each branch's counted stock as an opening movement;
- reconcile opening quantities and purchase prices against a physical count;
- retain the original backup until reconciliation is signed off.

## Intended authorization model

- `owner`: view and manage the whole organization.
- `branch_admin`: manage permitted operations for their assigned branch and shared product catalog, but not manage owner accounts.
- `cashier`: sales-oriented access; no direct purchase-cost snapshot access and no stock adjustment/transfer administration.
- `inventory`: inventory and transfer workflows in the permitted branch scope.

These migrations are a foundation, not a production-ready complete POS. The Android app is not integrated with the RPCs yet. Sale voids/refunds, return accounting, stock reconciliation, full offline outbox replay, and end-to-end sync still need implementation and tests. Do not use live transactions until staging security and concurrency tests pass. The app must never contain a Supabase service-role key.
