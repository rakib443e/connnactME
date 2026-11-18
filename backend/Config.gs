/**
 * ConnectMe Backend Configuration
 * Central Google Drive Root Folder ID is fixed per project specification.
 */
var CONFIG = {
  APP_NAME: 'ConnectMe',
  API_VERSION: '1.0.0',
  ROOT_FOLDER_ID: '1YFrpi1h1L747bTrfexoutVX5zUAT0kKj',
  ROOT_FOLDER_EXPECTED_NAME: 'ConnectMe_Database',

  // Subfolder hierarchy inside ConnectMe_Database
  FOLDERS: {
    SYSTEM: 'System',
    USERS: 'Users',
    CONVERSATIONS: 'Conversations',
    GROUPS: 'Groups',
    MEDIA: 'Media',
    MEDIA_IMAGES: 'Images',
    MEDIA_VIDEOS: 'Videos',
    MEDIA_VOICE: 'Voice',
    MEDIA_DOCUMENTS: 'Documents',
    SECURITY: 'Security',
    AUDIT_LOGS: 'audit_logs'
  },

  // Script Property keys
  PROP_KEYS: {
    ROOT_FOLDER_ID: 'CM_ROOT_FOLDER_ID',
    FOLDER_MAP: 'CM_FOLDER_MAP_JSON',
    INITIALIZED_AT: 'CM_INITIALIZED_AT',
    TOKEN_PEPPER: 'CM_TOKEN_PEPPER',
    SMS_PROVIDER: 'CM_SMS_PROVIDER', // e.g., TWILIO, WEBHOOK, or empty
    TWILIO_SID: 'CM_TWILIO_ACCOUNT_SID',
    TWILIO_TOKEN: 'CM_TWILIO_AUTH_TOKEN',
    TWILIO_FROM: 'CM_TWILIO_FROM_NUMBER',
    SMS_WEBHOOK_URL: 'CM_SMS_WEBHOOK_URL',
    SMS_WEBHOOK_SECRET: 'CM_SMS_WEBHOOK_SECRET'
  },

  // System files inside System/
  SYSTEM_FILES: {
    APP_CONFIG: 'app_config.json',
    USER_INDEX: 'user_index.json',
    USERNAME_INDEX: 'username_index.json',
    PHONE_INDEX: 'phone_index.json',
    DEVICE_INDEX: 'device_index.json',
    SESSIONS: 'sessions_index.json',
    COUNTERS: 'counters.json',
    OTP_CHALLENGES: 'otp_challenges.json',
    MEDIA_INDEX: 'media_index.json',
    IDEMPOTENCY: 'idempotency_index.json'
  },

  // Security & Session settings
  SESSION_TTL_MS: 24 * 60 * 60 * 1000,          // 24 hours
  REFRESH_TOKEN_TTL_MS: 30 * 24 * 60 * 60 * 1000, // 30 days
  OTP_TTL_MS: 5 * 60 * 1000,                     // 5 minutes
  OTP_MAX_ATTEMPTS: 5,

  // Rate limits (requests per window)
  RATE_LIMITS: {
    DEFAULT_PER_MINUTE: 120,
    AUTH_PER_MINUTE: 20,
    OTP_PER_10_MIN: 5,
    SEARCH_PER_MINUTE: 40,
    UPLOAD_PER_MINUTE: 30
  },

  // Upload limits in bytes (starting configurable limits)
  UPLOAD_LIMITS: {
    IMAGE_MAX_BYTES: 10 * 1024 * 1024,    // 10 MB
    VOICE_MAX_BYTES: 20 * 1024 * 1024,    // 20 MB
    VIDEO_MAX_BYTES: 50 * 1024 * 1024,    // 50 MB
    DOCUMENT_MAX_BYTES: 20 * 1024 * 1024, // 20 MB
    GAS_CHUNK_MAX_BYTES: 4 * 1024 * 1024  // 4 MB base64-safe chunk per GAS HTTP call
  },

  ALLOWED_MEDIA: {
    image: ['jpg', 'jpeg', 'png', 'webp'],
    video: ['mp4', 'mov'],
    voice: ['m4a', 'aac', 'mp3', 'wav'],
    document: ['pdf']
  },

  ALLOWED_MIME: {
    image: ['image/jpeg', 'image/png', 'image/webp'],
    video: ['video/mp4', 'video/quicktime'],
    voice: ['audio/mp4', 'audio/aac', 'audio/mpeg', 'audio/wav', 'audio/x-wav', 'audio/m4a'],
    document: ['application/pdf']
  },

  LOCK_TIMEOUT_MS: 25000,
  CACHE_TTL_SEC: 120
};
