# ConnectMe Google Apps Script JSON API

All requests are sent via `HTTP POST` to the deployed Apps Script Web App `/exec` URL with `Content-Type: application/json`:
```json
{
  "action": "sendMessage",
  "payload": {
    "accessToken": "AT_...",
    "conversationId": "CONV_CM100001_CM100002",
    "messageType": "text",
    "content": "Hello",
    "idempotencyKey": "idem_..."
  }
}
```
All responses use the uniform envelope:
```json
{
  "success": true,
  "data": {},
  "error": null
}
```

## Actions Summary
- **System**: `healthCheck`, `initializeDrive`, `getAppConfig`
- **Auth**: `guestRegister`, `guestLogin`, `requestPhoneOtp`, `verifyPhoneOtp`, `refreshSession`, `logout`, `getMyProfile`
- **Users**: `searchUser`, `getUserProfile`, `updateProfile`, `updateUsername`, `uploadProfilePhoto`
- **Contacts**: `addContact`, `removeContact`, `getContacts`
- **Conversations**: `createConversation`, `getConversations`, `getConversationInfo`
- **Messages**: `sendMessage`, `syncMessages` (`{ conversationId, afterSequence }`), `getMessageHistory`, `markMessageRead`
- **Groups**: `createGroup`, `getGroupInfo`, `addGroupMember`, `removeGroupMember`, `updateGroupInfo`, `getGroupMessages`
- **Media**: `uploadMedia` (supports `chunkIndex` / `totalChunks` / `uploadSessionId`), `getMedia`, `deleteMedia`
