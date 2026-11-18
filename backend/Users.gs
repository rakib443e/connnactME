/**
 * ConnectMe Users & Profiles Module
 * Never exposes private phone numbers in public search results.
 */
var Users = (function () {
  function toPublicProfile(p) {
    return {
      userId: p.userId,
      username: p.username,
      displayName: p.displayName,
      bio: p.bio || '',
      photoMediaId: p.photoMediaId || null,
      accountType: p.accountType,
      createdAt: p.createdAt
    };
  }

  function searchUser(payload, authContext) {
    Security.checkRateLimit('SEARCH_' + authContext.userId, CONFIG.RATE_LIMITS.SEARCH_PER_MINUTE, 60);
    var query = Utils.sanitizeString(payload.query, 64);
    if (!query || query.length < 2) {
      throw { code: 'INVALID_INPUT', message: 'Search query must be at least 2 characters.' };
    }

    var resultsMap = {};
    var userIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.USER_INDEX, { users: {} });
    var usernameIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.USERNAME_INDEX, { usernames: {} });
    var phoneIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.PHONE_INDEX, { phones: {} });

    // 1. Exact userId match
    var upperQuery = query.toUpperCase();
    if (userIndex.users[upperQuery]) {
      resultsMap[upperQuery] = true;
    }

    // 2. Exact username match
    var normUser = Utils.normalizeUsername(query);
    if (normUser && usernameIndex.usernames[normUser]) {
      resultsMap[usernameIndex.usernames[normUser]] = true;
    }

    // 3. Phone number hash match (E.164 or stripped digits with default country code if provided)
    var cleanedPhone = query.replace(/[^0-9+]/g, '');
    if (cleanedPhone.length >= 7) {
      if (cleanedPhone.charAt(0) !== '+' && payload.defaultCountryCode) {
        var cc = String(payload.defaultCountryCode).replace(/[^0-9+]/g, '');
        if (cc.charAt(0) !== '+') cc = '+' + cc;
        cleanedPhone = cc + cleanedPhone;
      }
      var phoneHash = Utils.sha256Hex('PHONE:' + cleanedPhone);
      var phoneMatchedId = phoneIndex.phones[phoneHash];
      if (phoneMatchedId) {
        var matchedFolder = DriveStorage.getUserFolder(phoneMatchedId, false);
        if (matchedFolder) {
          var prof = DriveStorage.readJsonFile(matchedFolder, 'profile.json');
          if (!prof.privacy || prof.privacy.allowSearchByPhone !== false) {
            resultsMap[phoneMatchedId] = true;
          }
        }
      }
    }

    // 4. Prefix / substring match on indexed usernames & display names (excluding self)
    var qLower = query.toLowerCase();
    Object.keys(userIndex.users).forEach(function (uid) {
      var u = userIndex.users[uid];
      if ((u.username && u.username.toLowerCase().indexOf(qLower) !== -1) ||
          (u.displayName && u.displayName.toLowerCase().indexOf(qLower) !== -1)) {
        resultsMap[uid] = true;
      }
    });

    var results = Object.keys(resultsMap)
      .filter(function (uid) { return uid !== authContext.userId; })
      .slice(0, 25)
      .map(function (uid) {
        var uFolder = DriveStorage.getUserFolder(uid, false);
        if (!uFolder) return null;
        var p = DriveStorage.readJsonFile(uFolder, 'profile.json', null);
        return p ? toPublicProfile(p) : null;
      })
      .filter(function (item) { return item !== null; });

    return { users: results };
  }

  function getUserProfile(payload) {
    var targetId = Utils.assertSafeId(payload.userId, 'userId');
    var folder = DriveStorage.getUserFolder(targetId, false);
    if (!folder) {
      throw { code: 'USER_NOT_FOUND', message: 'User not found.' };
    }
    var profile = DriveStorage.readJsonFile(folder, 'profile.json');
    return { user: toPublicProfile(profile) };
  }

  function updateProfile(payload, authContext) {
    return DriveStorage.withLock(function () {
      var folder = DriveStorage.getUserFolder(authContext.userId, false);
      if (!folder) {
        throw { code: 'USER_NOT_FOUND', message: 'User profile not found.' };
      }
      var profile = DriveStorage.readJsonFile(folder, 'profile.json');

      if (payload.displayName !== undefined) {
        var dn = Utils.sanitizeString(payload.displayName, 48);
        if (!dn) throw { code: 'INVALID_INPUT', message: 'Display name cannot be empty.' };
        profile.displayName = dn;
      }
      if (payload.bio !== undefined) {
        profile.bio = Utils.sanitizeString(payload.bio, 180);
      }
      if (payload.privacy && typeof payload.privacy === 'object') {
        profile.privacy = profile.privacy || {};
        if (payload.privacy.showOnlineStatus !== undefined) {
          profile.privacy.showOnlineStatus = Boolean(payload.privacy.showOnlineStatus);
        }
        if (payload.privacy.allowSearchByPhone !== undefined) {
          profile.privacy.allowSearchByPhone = Boolean(payload.privacy.allowSearchByPhone);
        }
        if (payload.privacy.allowGroupInvites !== undefined) {
          profile.privacy.allowGroupInvites = Boolean(payload.privacy.allowGroupInvites);
        }
      }
      profile.updatedAt = Utils.nowIso();
      DriveStorage.writeJsonFile(folder, 'profile.json', profile);

      var userIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.USER_INDEX, { users: {} });
      if (userIndex.users[profile.userId]) {
        userIndex.users[profile.userId].displayName = profile.displayName;
        DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.USER_INDEX, userIndex);
      }

      return { user: Auth.sanitizePrivateProfile(profile) };
    });
  }

  function updateUsername(payload, authContext) {
    var desired = Utils.normalizeUsername(payload.username);
    if (!Utils.isValidUsername(desired)) {
      throw { code: 'INVALID_USERNAME', message: 'Username must be 3-24 lowercase letters, digits, or underscores.' };
    }
    return DriveStorage.withLock(function () {
      var usernameIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.USERNAME_INDEX, { usernames: {} });
      var currentOwner = usernameIndex.usernames[desired];
      if (currentOwner && currentOwner !== authContext.userId) {
        throw { code: 'USERNAME_TAKEN', message: 'Username is already taken.' };
      }

      var folder = DriveStorage.getUserFolder(authContext.userId, false);
      var profile = DriveStorage.readJsonFile(folder, 'profile.json');
      var oldUsername = profile.username;
      if (oldUsername && usernameIndex.usernames[oldUsername] === authContext.userId) {
        delete usernameIndex.usernames[oldUsername];
      }
      usernameIndex.usernames[desired] = authContext.userId;
      DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.USERNAME_INDEX, usernameIndex);

      profile.username = desired;
      profile.updatedAt = Utils.nowIso();
      DriveStorage.writeJsonFile(folder, 'profile.json', profile);

      var userIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.USER_INDEX, { users: {} });
      if (userIndex.users[profile.userId]) {
        userIndex.users[profile.userId].username = desired;
        DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.USER_INDEX, userIndex);
      }

      Security.audit('USERNAME_UPDATED', authContext.userId, { newUsername: desired });
      return { user: Auth.sanitizePrivateProfile(profile) };
    });
  }

  function uploadProfilePhoto(payload, authContext) {
    var uploaded = Media.uploadMediaInternal({
      category: 'image',
      fileName: payload.fileName || ('avatar_' + authContext.userId + '.jpg'),
      mimeType: payload.mimeType || 'image/jpeg',
      base64Data: payload.base64Data,
      conversationId: 'PROFILE_' + authContext.userId,
      isProfileAvatar: true
    }, authContext);

    DriveStorage.withLock(function () {
      var folder = DriveStorage.getUserFolder(authContext.userId, false);
      var profile = DriveStorage.readJsonFile(folder, 'profile.json');
      profile.photoMediaId = uploaded.media.mediaId;
      profile.updatedAt = Utils.nowIso();
      DriveStorage.writeJsonFile(folder, 'profile.json', profile);

      var userIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.USER_INDEX, { users: {} });
      if (userIndex.users[profile.userId]) {
        userIndex.users[profile.userId].photoMediaId = uploaded.media.mediaId;
        DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.USER_INDEX, userIndex);
      }
    });

    return {
      photoMediaId: uploaded.media.mediaId,
      media: uploaded.media
    };
  }

  return {
    toPublicProfile: toPublicProfile,
    searchUser: searchUser,
    getUserProfile: getUserProfile,
    updateProfile: updateProfile,
    updateUsername: updateUsername,
    uploadProfilePhoto: uploadProfilePhoto
  };
})();
