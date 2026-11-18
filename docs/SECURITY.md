# ConnectMe Security Architecture

## Authentication & Token Hygiene
- Server issues high-entropy random `accessToken` (24h TTL) and `refreshToken` (30d TTL).
- Backend stores **only salted SHA-256 hashes** (`CM_TOKEN_PEPPER`) in `System/sessions_index.json`—never plaintext tokens.
- Android stores tokens in `SecureTokenStorage` encrypted with **Android Keystore AES-256-GCM**.
- All protected endpoints resolve `authContext.userId` exclusively from the validated token; client-supplied sender IDs are ignored.

## Authorization & Privacy
- **1:1 Conversations**: Verified participant membership on every send, sync, history, read-receipt, and media download call.
- **Groups**: Admin-only member addition, info updates, and non-self removals. Removed members are immediately revoked from `members.json` and `conversations_index.json` and denied future sync/media calls (`FORBIDDEN`).
- **Phone Search Privacy**: `searchUser` hashes candidate phone queries (`PHONE:<E.164>`) against `phone_index.json` and strips private phone fields from search results.
- **Media Access**: Files created in Drive have `DriveApp.Access.PRIVATE` and are served only through authenticated `getMedia` checks.

## Important Architectural Disclosure
- Because Google Apps Script and the central Drive folder owner process and store JSON & media files, **this architecture is NOT end-to-end encrypted (E2EE)**.
- Setting Web App access to `Anyone` exposes the HTTP endpoint publicly, so rate limiting (`CacheService`), strict token verification, input validation (`assertSafeId`), and `Security/audit_logs/` auditing are enforced on every request.
