# Walkthrough: Cloudflare Workers + D1 Readiness & Security Verification

## Summary of Architecture & Compatibility Changes
- Implemented the modular Cloudflare Workers backend in `cloudflare-worker/src/` backed by Cloudflare D1 (`DB`) and `cloudflare-worker/migrations/0001_initial_d1_schema.sql` (26 tables, 4 views, initial canonical seeds).
- Hardened authentication and input validation across both the Cloudflare Worker (`cloudflare-worker/src/security.js`) and the preserved Node.js/Express backend (`server/server.js`, `server/rewards-service/server.js`, `server/public/index.html`, `server/public/users.html`).
- Configured Android networking (`app/build.gradle.kts`, `ApiConfig.kt`, `PreferencesManager.kt`, `NetworkClientProvider.kt`, `BackendRepository.kt`) to target `https://api-test.kalametgame.ir` while preserving `https://api.kalametgame.ir`, preserving local `MockWebServer` support, attaching auth headers on all authenticated requests, and removing client-side offline score/reward fallbacks.

# Security Summary

## Issues Identified and Remediated
1. **Hardcoded Fallback Admin Key & Query-String Admin Key Leakage**:
   - **Remediation**: Removed hardcoded fallback admin secrets from Express middleware and admin HTML files, rejected query-parameter admin credentials, and enforced constant-time comparison against configured server secrets (`ADMIN_KEY`) via headers only (`x-admin-key` / `Authorization: Bearer`).
2. **Unauthenticated `x-user-id` Impersonation & Token Overwrite Risk**:
   - **Remediation**: Enforced strict token validation (`x-auth-token` / `x-token` / `Authorization: Bearer`) in `requireUserAuth` across both Cloudflare Worker and Express, verified header/token user ID alignment (`403` on mismatch), verified path ownership on `/api/rewards/claims/user/:userId`, and attached user bearer/token headers to all authenticated Android requests in `BackendRepository.kt`.
3. **Client-Side Fake Score Awarding & Purchase Signature Bypass**:
   - **Remediation**: Removed client-side offline `+1` point fallback in `BackendRepository.completeAdAndAwardPoint` and static reward fallbacks; enforced Myket `SHA1withRSA` verification and replay prevention (`409`) across `/api/purchases/verify` and `/api/store/verify-purchase`.
4. **Password Hashing & Race-Condition Protections**:
   - **Remediation**: Implemented PBKDF2-SHA256 password hashing with transparent legacy upgrade in Worker, D1-backed rate limiting, conditional atomic updates for reward claims and double-refund prevention, binary magic-byte upload validation, and scheme filtering on banner target URLs.

## PoC Verification
- **Executed Worker + D1 SQLite Security Suite** (`node cloudflare-worker/tests/worker_d1.test.js`):
  - Verified `/api/health` and `/api/health/d1` report `200 OK` and all 26 D1 tables present.
  - Confirmed `?adminKey=...` query-parameter admin requests and missing/invalid keys return `401 Unauthorized`.
  - Confirmed unauthenticated `x-user-id`-only requests return `401 Unauthorized` while valid token sessions succeed.
  - Confirmed duplicate ad completion / `rewardNonce` replay returns `409 Conflict` without double-crediting points.
  - Confirmed tampered Myket RSA signatures return `401` and replayed purchase tokens return `409`.
  - Confirmed cross-user support ticket access returns `404` and repeated reward claim rejection does not double-refund points.
- **Executed Android HTTP & Config Tests** (`ApiConfigTest`):
  - Confirmed OkHttp interceptor enforces HTTPS on non-local domains (throwing `SSLHandshakeException` on cleartext HTTP) while keeping local `MockWebServer` tests functional and routing legacy Render hosts safely.
