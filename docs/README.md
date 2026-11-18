# ConnectMe — Android Messaging Application

ConnectMe is an Android messaging platform built with **Kotlin + Jetpack Compose**, **Google Apps Script (Web App)**, and **Central Google Drive** JSON & binary storage (`ConnectMe_Database`, Folder ID `1YFrpi1h1L747bTrfexoutVX5zUAT0kKj`).

> **Environment Note:** This workspace provides the native Android (Kotlin/Gradle/Compose) build toolchain rather than the Flutter/Dart SDK, so the Android client is implemented natively in Kotlin + Jetpack Compose under `app/` (`applicationId = "com.connectme.messaging"`).

## Key Features
- **Guest & Phone Accounts**: Device-associated Guest registration (`CM100001`), Phone OTP verification with modular SMS provider adapter (`Twilio` / `Webhook`), and Guest-to-Phone upgrade.
- **Privacy-Preserving Search**: Search by username, unique `CM...` ID, or phone hash without exposing private phone numbers.
- **1:1 & Group Messaging**: Sequence-allocated messages, client idempotency keys, offline Room SQLite queue, automatic reconnection flush, and backward pagination.
- **Polling Synchronization**: 3s active chat, 5s conversation list, 15s other screens, exponential backoff, background pause.
- **Rich Media**: Validated chunked uploads for Images (`<= 10 MB`), Voice (`<= 20 MB`), Documents/PDF (`<= 20 MB`), and Videos (`<= 50 MB`), plus in-app image zoom, video player, voice recorder/player, and PDF page renderer.

## Documentation Index
- [Architecture](ARCHITECTURE.md)
- [API Documentation](API_DOCUMENTATION.md)
- [Google Drive Setup](GOOGLE_DRIVE_SETUP.md)
- [Backend Deployment](BACKEND_DEPLOYMENT.md)
- [Android Build](ANDROID_BUILD.md)
- [Security](SECURITY.md)
- [Troubleshooting](TROUBLESHOOTING.md)
