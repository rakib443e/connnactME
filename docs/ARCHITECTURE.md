# ConnectMe Architecture

## High-Level System Topology
1. **Android Client (`app/`, `com.connectme.messaging`)**
   - **UI**: Jetpack Compose + Material Design 3 (`ConnectMeTheme`, adaptive `NavigationBar` / `NavigationRail`).
   - **State**: `ConnectMeViewModel` (`StateFlow` / `collectAsStateWithLifecycle`).
   - **Local Cache & Offline Queue**: Room SQLite (`ConnectMeDatabase`) storing `conversations_cache`, `messages_cache` (with `QUEUED`, `SENDING`, `FAILED`, `SERVER_CONFIRMED` states), `sync_cursors`, `contacts_cache`, and `media_cache`.
   - **Token Storage**: `SecureTokenStorage` using Android Keystore `AES/GCM/NoPadding`.
   - **Polling Engine**: `PollingManager` with surface-based intervals (3s / 5s / 15s), single-flight mutex, exponential backoff, and background lifecycle pause.

2. **Trusted Backend (`backend/*.gs`)**
   - Modular Google Apps Script Web App executing as the authorized Drive owner.
   - Enforces authentication, rate limits, `LockService` atomic sequence allocation, idempotency deduplication, and membership checks.

3. **Central Storage (`ConnectMe_Database`, ID `1YFrpi1h1L747bTrfexoutVX5zUAT0kKj`)**
   - JSON files for users, indexes, conversations, message chunks (100 messages/chunk), groups, and audit logs.
   - Private Drive files under `Media/{Images,Videos,Voice,Documents}/`.
