# Backend Deployment Guide (Google Apps Script)

1. Go to [script.google.com](https://script.google.com) signed in as the Google account that owns/edits Drive folder `1YFrpi1h1L747bTrfexoutVX5zUAT0kKj`.
2. Create a new Apps Script project named **ConnectMe Backend**.
3. Copy all 13 `.gs` modules from `/backend/` (`Code.gs`, `Config.gs`, `Router.gs`, `Auth.gs`, `Users.gs`, `Contacts.gs`, `Conversations.gs`, `Messages.gs`, `Groups.gs`, `Media.gs`, `DriveStorage.gs`, `Security.gs`, `Utils.gs`) and `appsscript.json` (enable **Show "appsscript.json" manifest file in editor** in Project Settings).
4. Optional SMS OTP configuration in **Project Settings -> Script Properties**:
   - For Twilio: `CM_SMS_PROVIDER=TWILIO`, `CM_TWILIO_ACCOUNT_SID`, `CM_TWILIO_AUTH_TOKEN`, `CM_TWILIO_FROM_NUMBER`
   - For custom HTTPS webhook: `CM_SMS_PROVIDER=WEBHOOK`, `CM_SMS_WEBHOOK_URL`, `CM_SMS_WEBHOOK_SECRET`
5. Run `initializeDrive()` once in the editor and approve permissions.
6. Click **Deploy -> New deployment -> Web app**:
   - **Execute as:** `Me` (the authorized Drive owner)
   - **Who has access:** `Anyone` (required so the Android mobile client can reach the HTTPS `/exec` endpoint without browser Google cookies; all user actions are gated by application-level salted session tokens!)
7. Copy the deployed `https://script.google.com/macros/s/.../exec` URL.
8. Verify healthCheck:
   ```bash
   curl -L "https://script.google.com/macros/s/<DEPLOYMENT_ID>/exec?action=healthCheck"
   ```
9. Paste the Web App URL into ConnectMe's **Apps Script & Drive Backend** setup dialog (or set `DEFAULT_APPS_SCRIPT_URL` in `AppConfig.kt`).
