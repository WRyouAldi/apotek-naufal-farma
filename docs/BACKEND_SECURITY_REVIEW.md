# Backend and data-exposure review

Date: 2026-10-09  
Status: pre-deployment review; no hosted database has been changed.

## Findings that block a commercial release

### 1. The current cloud CSV is public
The Android app fetches `cloud/xReport2.csv` from the public GitHub raw-content endpoint. The file contains current stock quantities and purchase prices (and sales prices). Anyone who knows or discovers the URL can read it without signing in. This is not a secure multi-user backend and should not be treated as one.

**Required action before production:** stop publishing operational stock/cost data in a public repository. Move the data behind authenticated Supabase APIs/RLS, or use a private server-side source. Then replace the public CSV sync path only after the authenticated client integration is implemented and tested. Do not merely hide the URL in the APK; that does not make it private.

### 2. Supabase migrations and Edge Functions have not been executed
The SQL and TypeScript in `backend/supabase/` are source foundations only. They have not been validated against a live PostgreSQL/Supabase staging project. No claim of working multi-branch authorization, transactional stock movement, or staff invitations should be made until staging tests pass.

### 3. Edge Function CORS is currently permissive
Both admin Edge Functions currently return `Access-Control-Allow-Origin: *`. Authorization still requires a valid bearer token and server-side role checks, but the browser origin policy should be restricted to the actual trusted web origin(s) if a browser-based client is used. Native Android clients do not rely on CORS as an authentication boundary.

### 4. Transaction and inventory rules need staging tests
Before production, test at least:
- concurrent checkouts for the same final units;
- retrying checkout/stock movement with the same idempotency key;
- malformed item JSON, duplicate product IDs, invalid quantities and discounts;
- transfer dispatch/receive retries and cross-branch access attempts;
- role separation for owner, branch admin, cashier and inventory;
- whether the deployed Supabase grants match the intended RPC-only write path.

### 5. Opening stock cannot be inferred safely
The legacy local `products.stok` column has no branch identifier. Do not copy it to every branch. Confirm the source branch, map product codes/barcodes, count physical stock per branch, and reconcile before importing opening balances.

## Safe next steps
1. Create a dedicated Supabase staging project.
2. Apply and test migrations there only after SQL review.
3. Create the initial owner account/profile and real branch records.
4. Integrate the Android app with Supabase Auth and authenticated APIs; never put `service_role` in the APK or client-side source.
5. Replace public CSV sync and test account/branch isolation.
6. Only then consider a production migration and release-signed APK.

A successful Android build proves compilation only. It does not prove backend connectivity, authorization, data confidentiality, or on-device correctness.
