/**
 * ConnectMe Authentication & Modular SMS Provider Adapter
 */
var SmsProviderAdapter = (function () {
  function getProviderName() {
    return (PropertiesService.getScriptProperties().getProperty(CONFIG.PROP_KEYS.SMS_PROVIDER) || '').toUpperCase();
  }

  function isConfigured() {
    var provider = getProviderName();
    var props = PropertiesService.getScriptProperties();
    if (provider === 'TWILIO') {
      return Boolean(
        props.getProperty(CONFIG.PROP_KEYS.TWILIO_SID) &&
        props.getProperty(CONFIG.PROP_KEYS.TWILIO_TOKEN) &&
        props.getProperty(CONFIG.PROP_KEYS.TWILIO_FROM)
      );
    }
    if (provider === 'WEBHOOK') {
      return Boolean(props.getProperty(CONFIG.PROP_KEYS.SMS_WEBHOOK_URL));
    }
    return false;
  }

  function sendOtpSms(e164Phone, otpCode) {
    var provider = getProviderName();
    var props = PropertiesService.getScriptProperties();

    if (provider === 'TWILIO') {
      var sid = props.getProperty(CONFIG.PROP_KEYS.TWILIO_SID);
      var token = props.getProperty(CONFIG.PROP_KEYS.TWILIO_TOKEN);
      var from = props.getProperty(CONFIG.PROP_KEYS.TWILIO_FROM);
      var url = 'https://api.twilio.com/2010-04-01/Accounts/' + encodeURIComponent(sid) + '/Messages.json';
      var resp = UrlFetchApp.fetch(url, {
        method: 'post',
        headers: {
          Authorization: 'Basic ' + Utilities.base64Encode(sid + ':' + token)
        },
        payload: {
          To: e164Phone,
          From: from,
          Body: 'Your ConnectMe verification code is: ' + otpCode + '. Valid for 5 minutes.'
        },
        muteHttpExceptions: true
      });
      var code = resp.getResponseCode();
      if (code < 200 || code >= 300) {
        throw { code: 'SMS_DELIVERY_FAILED', message: 'SMS provider rejected delivery (HTTP ' + code + ').' };
      }
      return { delivered: true, provider: 'TWILIO' };
    }

    if (provider === 'WEBHOOK') {
      var hookUrl = props.getProperty(CONFIG.PROP_KEYS.SMS_WEBHOOK_URL);
      var hookSecret = props.getProperty(CONFIG.PROP_KEYS.SMS_WEBHOOK_SECRET) || '';
      var hookResp = UrlFetchApp.fetch(hookUrl, {
        method: 'post',
        contentType: 'application/json',
        headers: hookSecret ? { 'X-ConnectMe-Webhook-Secret': hookSecret } : {},
        payload: JSON.stringify({
          to: e164Phone,
          message: 'Your ConnectMe verification code is: ' + otpCode,
          otpCode: otpCode
        }),
        muteHttpExceptions: true
      });
      var hCode = hookResp.getResponseCode();
      if (hCode < 200 || hCode >= 300) {
        throw { code: 'SMS_DELIVERY_FAILED', message: 'SMS webhook returned HTTP ' + hCode + '.' };
      }
      return { delivered: true, provider: 'WEBHOOK' };
    }

    // Never invent credentials or fake SMS delivery
    throw {
      code: 'SMS_PROVIDER_NOT_CONFIGURED',
      message: 'No live SMS provider is configured in Apps Script Script Properties (CM_SMS_PROVIDER). OTP delivery cannot be completed.'
    };
  }

  return {
    getProviderName: getProviderName,
    isConfigured: isConfigured,
    sendOtpSms: sendOtpSms
  };
})();

var Auth = (function () {
  function createUserRecord(opts) {
    var userId = DriveStorage.nextId('userSeq', 'CM', 6);
    var now = Utils.nowIso();
    var username = opts.username || ('user_' + userId.toLowerCase());
    var displayName = opts.displayName || ('ConnectMe ' + userId);

    var profile = {
      userId: userId,
      username: username,
      displayName: displayName,
      bio: '',
      photoMediaId: null,
      accountType: opts.accountType || 'guest', // 'guest' | 'phone'
      phoneVerified: Boolean(opts.phoneVerified),
      phoneE164Hash: opts.phoneE164Hash || null,
      phoneMasked: opts.phoneMasked || null,
      deviceIdHash: opts.deviceIdHash || null,
      privacy: {
        showOnlineStatus: true,
        allowSearchByPhone: true,
        allowGroupInvites: true
      },
      createdAt: now,
      updatedAt: now
    };

    var userFolder = DriveStorage.getUserFolder(userId, true);
    DriveStorage.writeJsonFile(userFolder, 'profile.json', profile);
    DriveStorage.writeJsonFile(userFolder, 'contacts.json', { contacts: [] });

    // Update central indexes
    var userIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.USER_INDEX, { users: {} });
    userIndex.users[userId] = {
      userId: userId,
      username: username,
      displayName: displayName,
      photoMediaId: null,
      accountType: profile.accountType,
      createdAt: now
    };
    DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.USER_INDEX, userIndex);

    var usernameIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.USERNAME_INDEX, { usernames: {} });
    usernameIndex.usernames[username] = userId;
    DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.USERNAME_INDEX, usernameIndex);

    if (opts.deviceIdHash) {
      var deviceIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.DEVICE_INDEX, { devices: {} });
      deviceIndex.devices[opts.deviceIdHash] = userId;
      DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.DEVICE_INDEX, deviceIndex);
    }

    if (opts.phoneE164Hash) {
      var phoneIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.PHONE_INDEX, { phones: {} });
      phoneIndex.phones[opts.phoneE164Hash] = userId;
      DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.PHONE_INDEX, phoneIndex);
    }

    return profile;
  }

  function guestRegister(payload) {
    var deviceId = Utils.sanitizeString(payload.deviceId, 128);
    if (!deviceId || deviceId.length < 6) {
      throw { code: 'INVALID_INPUT', message: 'A valid deviceId is required.' };
    }
    var displayName = Utils.sanitizeString(payload.displayName, 48);
    if (!displayName) {
      throw { code: 'INVALID_INPUT', message: 'Display name is required.' };
    }

    var desiredUsername = Utils.normalizeUsername(payload.username);
    var deviceIdHash = Utils.sha256Hex('DEV:' + deviceId);

    var profile = DriveStorage.withLock(function () {
      var deviceIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.DEVICE_INDEX, { devices: {} });
      if (deviceIndex.devices[deviceIdHash] && !payload.allowNewOnSameDevice) {
        var existingId = deviceIndex.devices[deviceIdHash];
        var folder = DriveStorage.getUserFolder(existingId, false);
        if (folder) {
          return DriveStorage.readJsonFile(folder, 'profile.json');
        }
      }

      if (desiredUsername) {
        if (!Utils.isValidUsername(desiredUsername)) {
          throw { code: 'INVALID_USERNAME', message: 'Username must be 3-24 lowercase letters, numbers, or underscores.' };
        }
        var usernameIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.USERNAME_INDEX, { usernames: {} });
        if (usernameIndex.usernames[desiredUsername]) {
          throw { code: 'USERNAME_TAKEN', message: 'That username is already taken.' };
        }
      }

      return createUserRecord({
        displayName: displayName,
        username: desiredUsername || null,
        accountType: 'guest',
        phoneVerified: false,
        deviceIdHash: deviceIdHash
      });
    });

    var session = Security.createSession(profile.userId, deviceId);
    Security.audit('GUEST_REGISTER', profile.userId, { accountType: profile.accountType });

    return {
      user: sanitizePrivateProfile(profile),
      session: session
    };
  }

  function guestLogin(payload) {
    var deviceId = Utils.sanitizeString(payload.deviceId, 128);
    if (!deviceId) {
      throw { code: 'INVALID_INPUT', message: 'deviceId is required.' };
    }
    var deviceIdHash = Utils.sha256Hex('DEV:' + deviceId);
    var deviceIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.DEVICE_INDEX, { devices: {} });
    var userId = payload.userId ? Utils.assertSafeId(payload.userId, 'userId') : deviceIndex.devices[deviceIdHash];

    if (!userId) {
      throw { code: 'GUEST_NOT_FOUND', message: 'No guest profile associated with this device.' };
    }
    var userFolder = DriveStorage.getUserFolder(userId, false);
    if (!userFolder) {
      throw { code: 'USER_NOT_FOUND', message: 'User profile not found.' };
    }
    var profile = DriveStorage.readJsonFile(userFolder, 'profile.json');
    if (profile.deviceIdHash && profile.deviceIdHash !== deviceIdHash) {
      Security.audit('GUEST_LOGIN_DEVICE_MISMATCH', userId, {});
      throw { code: 'FORBIDDEN', message: 'Device identity does not match guest account.' };
    }

    var session = Security.createSession(profile.userId, deviceId);
    Security.audit('GUEST_LOGIN', profile.userId, {});
    return {
      user: sanitizePrivateProfile(profile),
      session: session
    };
  }

  function requestPhoneOtp(payload) {
    var e164 = Utils.normalizePhone(payload.countryCode, payload.phoneNumber);
    if (!e164) {
      throw { code: 'INVALID_PHONE', message: 'Please enter a valid country code and phone number.' };
    }
    Security.checkRateLimit('OTP_' + e164, CONFIG.RATE_LIMITS.OTP_PER_10_MIN, 600);

    var otpCode = Utils.generateOtpCode();
    var challengeId = Utils.generateSecureToken('OTP').substring(0, 24);
    var otpHash = Utils.sha256Hex(challengeId + ':' + otpCode);
    var phoneHash = Utils.sha256Hex('PHONE:' + e164);
    var expiresAtMs = Utils.nowMs() + CONFIG.OTP_TTL_MS;

    // Attempt real SMS delivery BEFORE storing active challenge or claiming success
    var delivery = SmsProviderAdapter.sendOtpSms(e164, otpCode);

    DriveStorage.withLock(function () {
      var store = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.OTP_CHALLENGES, { challenges: {} });
      var now = Utils.nowMs();
      Object.keys(store.challenges).forEach(function (k) {
        if (store.challenges[k].expiresAtMs < now) delete store.challenges[k];
      });
      store.challenges[challengeId] = {
        phoneE164Hash: phoneHash,
        phoneMasked: Utils.maskPhone(e164),
        otpHash: otpHash,
        attempts: 0,
        expiresAtMs: expiresAtMs,
        upgradeFromUserId: payload.upgradeUserId || null
      };
      DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.OTP_CHALLENGES, store);
    });

    Security.audit('OTP_REQUESTED', payload.upgradeUserId || 'PHONE_AUTH', { provider: delivery.provider });
    return {
      challengeId: challengeId,
      phoneMasked: Utils.maskPhone(e164),
      expiresAt: new Date(expiresAtMs).toISOString(),
      provider: delivery.provider
    };
  }

  function verifyPhoneOtp(payload, authContext) {
    var challengeId = Utils.sanitizeString(payload.challengeId, 64);
    var otpCode = Utils.sanitizeString(payload.otpCode, 12);
    var e164 = Utils.normalizePhone(payload.countryCode, payload.phoneNumber);
    if (!challengeId || !otpCode || !e164) {
      throw { code: 'INVALID_INPUT', message: 'challengeId, phoneNumber, and otpCode are required.' };
    }

    var phoneHash = Utils.sha256Hex('PHONE:' + e164);
    var profile = DriveStorage.withLock(function () {
      var store = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.OTP_CHALLENGES, { challenges: {} });
      var challenge = store.challenges[challengeId];
      if (!challenge) {
        throw { code: 'OTP_NOT_FOUND', message: 'Verification challenge not found or already used.' };
      }
      if (challenge.expiresAtMs < Utils.nowMs()) {
        delete store.challenges[challengeId];
        DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.OTP_CHALLENGES, store);
        throw { code: 'OTP_EXPIRED', message: 'Verification code has expired. Please request a new code.' };
      }
      if (challenge.phoneE164Hash !== phoneHash) {
        throw { code: 'OTP_PHONE_MISMATCH', message: 'Phone number does not match this challenge.' };
      }
      challenge.attempts += 1;
      if (challenge.attempts > CONFIG.OTP_MAX_ATTEMPTS) {
        delete store.challenges[challengeId];
        DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.OTP_CHALLENGES, store);
        throw { code: 'OTP_MAX_ATTEMPTS', message: 'Too many incorrect verification attempts.' };
      }

      var expectedHash = Utils.sha256Hex(challengeId + ':' + otpCode);
      if (expectedHash !== challenge.otpHash) {
        DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.OTP_CHALLENGES, store);
        throw { code: 'OTP_INVALID', message: 'Incorrect verification code.' };
      }

      delete store.challenges[challengeId];
      DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.OTP_CHALLENGES, store);

      var phoneIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.PHONE_INDEX, { phones: {} });
      var existingPhoneOwner = phoneIndex.phones[phoneHash];

      // Guest-to-phone upgrade if authenticated as guest
      if (authContext && authContext.userId) {
        if (existingPhoneOwner && existingPhoneOwner !== authContext.userId) {
          throw { code: 'PHONE_ALREADY_REGISTERED', message: 'This phone number is already linked to another ConnectMe account.' };
        }
        var uFolder = DriveStorage.getUserFolder(authContext.userId, false);
        var uProf = DriveStorage.readJsonFile(uFolder, 'profile.json');
        uProf.accountType = 'phone';
        uProf.phoneVerified = true;
        uProf.phoneE164Hash = phoneHash;
        uProf.phoneMasked = Utils.maskPhone(e164);
        uProf.updatedAt = Utils.nowIso();
        DriveStorage.writeJsonFile(uFolder, 'profile.json', uProf);

        phoneIndex.phones[phoneHash] = uProf.userId;
        DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.PHONE_INDEX, phoneIndex);

        var userIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.USER_INDEX, { users: {} });
        if (userIndex.users[uProf.userId]) {
          userIndex.users[uProf.userId].accountType = 'phone';
          DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.USER_INDEX, userIndex);
        }
        return uProf;
      }

      if (existingPhoneOwner) {
        var existingFolder = DriveStorage.getUserFolder(existingPhoneOwner, false);
        return DriveStorage.readJsonFile(existingFolder, 'profile.json');
      }

      return createUserRecord({
        displayName: Utils.sanitizeString(payload.displayName, 48) || 'ConnectMe User',
        accountType: 'phone',
        phoneVerified: true,
        phoneE164Hash: phoneHash,
        phoneMasked: Utils.maskPhone(e164),
        deviceIdHash: payload.deviceId ? Utils.sha256Hex('DEV:' + payload.deviceId) : null
      });
    });

    var session = Security.createSession(profile.userId, payload.deviceId || '');
    Security.audit('PHONE_OTP_VERIFIED', profile.userId, { upgraded: Boolean(authContext) });
    return {
      user: sanitizePrivateProfile(profile),
      session: session
    };
  }

  function refreshSession(payload) {
    var rotated = Security.rotateSession(payload.refreshToken);
    var folder = DriveStorage.getUserFolder(rotated.userId, false);
    var profile = folder ? DriveStorage.readJsonFile(folder, 'profile.json') : null;
    return {
      session: rotated,
      user: profile ? sanitizePrivateProfile(profile) : null
    };
  }

  function logout(payload, authContext) {
    Security.revokeSession(payload.accessToken || payload.token);
    Security.audit('LOGOUT', authContext.userId, {});
    return { loggedOut: true };
  }

  function getMyProfile(authContext) {
    var folder = DriveStorage.getUserFolder(authContext.userId, false);
    if (!folder) {
      throw { code: 'USER_NOT_FOUND', message: 'Authenticated user profile not found.' };
    }
    var profile = DriveStorage.readJsonFile(folder, 'profile.json');
    return { user: sanitizePrivateProfile(profile) };
  }

  function sanitizePrivateProfile(p) {
    return {
      userId: p.userId,
      username: p.username,
      displayName: p.displayName,
      bio: p.bio || '',
      photoMediaId: p.photoMediaId || null,
      accountType: p.accountType,
      phoneVerified: Boolean(p.phoneVerified),
      phoneMasked: p.phoneMasked || null,
      privacy: p.privacy || { showOnlineStatus: true, allowSearchByPhone: true, allowGroupInvites: true },
      createdAt: p.createdAt,
      updatedAt: p.updatedAt
    };
  }

  return {
    guestRegister: guestRegister,
    guestLogin: guestLogin,
    requestPhoneOtp: requestPhoneOtp,
    verifyPhoneOtp: verifyPhoneOtp,
    refreshSession: refreshSession,
    logout: logout,
    getMyProfile: getMyProfile,
    sanitizePrivateProfile: sanitizePrivateProfile
  };
})();
