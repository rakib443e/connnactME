# ConnectMe Troubleshooting

1. **`DRIVE_ACCESS_DENIED` on `healthCheck` or `initializeDrive`**
   - Confirm the deploying Google account owns or has Editor permission on folder `1YFrpi1h1L747bTrfexoutVX5zUAT0kKj`.
   - Confirm the Web App deployment is set to **Execute as: Me**.

2. **`INVALID_BACKEND_RESPONSE` (HTML login page returned instead of JSON)**
   - Ensure Web App **Who has access** is set to **Anyone** (not "Only myself") and that the configured URL ends in `/exec` (not `/dev`).

3. **`SMS_PROVIDER_NOT_CONFIGURED` during Phone Login / Upgrade**
   - Configure `CM_SMS_PROVIDER` (`TWILIO` or `WEBHOOK`) and corresponding credentials in Apps Script **Project Settings -> Script Properties**. Guest accounts remain fully functional without SMS credentials.

4. **Messages stay `QUEUED`**
   - Check device connectivity (`OfflineBanner`) or tap a `FAILED` bubble to retry delivery using its preserved idempotency key.
