# Google Drive Setup (`ConnectMe_Database`)

- **Supplied Root Folder ID:** `1YFrpi1h1L747bTrfexoutVX5zUAT0kKj`
- **Expected Name:** `ConnectMe_Database`
- **Access Requirement:** The Google account that deploys and runs the Apps Script Web App **must own or have Editor access** to folder `1YFrpi1h1L747bTrfexoutVX5zUAT0kKj`. Do **not** share this folder publicly.

## Folder Hierarchy Created by `initializeDrive()`
```text
ConnectMe_Database/ (1YFrpi1h1L747bTrfexoutVX5zUAT0kKj)
├── System/
│   ├── app_config.json
│   ├── user_index.json
│   ├── username_index.json
│   ├── phone_index.json
│   ├── device_index.json
│   ├── sessions_index.json
│   ├── counters.json
│   ├── otp_challenges.json
│   ├── media_index.json
│   └── idempotency_index.json
├── Users/
│   └── CM100001/ (profile.json, contacts.json, conversations_index.json)
├── Conversations/
│   └── CONV_ID/ (conversation.json, message_index.json, Messages/)
├── Groups/
│   └── GROUP_ID/ (group_info.json, members.json, message_index.json, Messages/)
├── Media/
│   ├── Images/
│   ├── Videos/
│   ├── Voice/
│   └── Documents/
└── Security/
    └── audit_logs/
```

## Initialization Steps
1. Open the Apps Script project containing all files from `backend/`.
2. Select `initializeDrive` in the top function selector and click **Run**.
3. Complete the Google OAuth consent screen for `DriveApp` and `UrlFetchApp`.
4. Verify in Execution Log that `success: true` and `rootFolderId: "1YFrpi1h1L747bTrfexoutVX5zUAT0kKj"` are returned.
5. Generated subfolder IDs are saved automatically in `PropertiesService.getScriptProperties()` (`CM_FOLDER_MAP_JSON`).
