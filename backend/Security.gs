/**
 * ConnectMe Security Module
 * Session token hashing, validation, rate limiting, authorization, and audit logs.
 */
var Security = (function () {
  function getTokenPepper() {
    var props = PropertiesService.getScriptProperties();
    var pepper = props.getProperty(CONFIG.PROP_KEYS.TOKEN_PEPPER);
    if (!pepper) {
      pepper = Utils.generateSecureToken('PEP');
      props.setProperty(CONFIG.PROP_KEYS.TOKEN_PEPPER, pepper);
    }
    return pepper;
  }

  function hashToken(rawToken) {
    return Utils.sha256Hex(getTokenPepper() + ':' + String(rawToken || ''));
  }

  function checkRateLimit(bucketKey, limitPerWindow, windowSeconds) {
    var cache = CacheService.getScriptCache();
    var key = 'RL_' + Utils.sha256Hex(bucketKey);
    var current = Number(cache.get(key) || '0');
    if (current >= limitPerWindow) {
      throw {
        code: 'RATE_LIMITED',
        message: 'Too many requests. Please wait a moment and try again.'
      };
    }
    cache.put(key, String(current + 1), windowSeconds || 60);
  }

  function createSession(userId, deviceId) {
    return DriveStorage.withLock(function () {
      var sessions = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.SESSIONS, { tokens: {}, refreshTokens: {} });
      var now = Utils.nowMs();
      var accessToken = Utils.generateSecureToken('AT');
      var refreshToken = Utils.generateSecureToken('RT');
      var accessHash = hashToken(accessToken);
      var refreshHash = hashToken(refreshToken);

      // Prune expired entries opportunistically
      Object.keys(sessions.tokens).forEach(function (h) {
        if (sessions.tokens[h].expiresAtMs < now) delete sessions.tokens[h];
      });
      Object.keys(sessions.refreshTokens).forEach(function (h) {
        if (sessions.refreshTokens[h].expiresAtMs < now) delete sessions.refreshTokens[h];
      });

      var expiresAtMs = now + CONFIG.SESSION_TTL_MS;
      var refreshExpiresAtMs = now + CONFIG.REFRESH_TOKEN_TTL_MS;

      sessions.tokens[accessHash] = {
        userId: userId,
        deviceId: deviceId || '',
        issuedAtMs: now,
        expiresAtMs: expiresAtMs,
        refreshHash: refreshHash
      };

      sessions.refreshTokens[refreshHash] = {
        userId: userId,
        deviceId: deviceId || '',
        issuedAtMs: now,
        expiresAtMs: refreshExpiresAtMs,
        accessHash: accessHash
      };

      DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.SESSIONS, sessions);

      return {
        accessToken: accessToken,
        refreshToken: refreshToken,
        expiresAt: new Date(expiresAtMs).toISOString(),
        expiresAtMs: expiresAtMs,
        refreshExpiresAt: new Date(refreshExpiresAtMs).toISOString()
      };
    });
  }

  function authenticate(payload) {
    var token = payload && (payload.accessToken || payload.token);
    if (!token) {
      throw { code: 'UNAUTHORIZED', message: 'Missing authentication token.' };
    }
    var tokenHash = hashToken(token);
    var sessions = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.SESSIONS, { tokens: {}, refreshTokens: {} });
    var entry = sessions.tokens[tokenHash];
    if (!entry) {
      throw { code: 'UNAUTHORIZED', message: 'Invalid session token.' };
    }
    if (entry.expiresAtMs < Utils.nowMs()) {
      throw { code: 'SESSION_EXPIRED', message: 'Session has expired. Please refresh your session.' };
    }
    return {
      userId: entry.userId,
      deviceId: entry.deviceId,
      tokenHash: tokenHash,
      expiresAtMs: entry.expiresAtMs
    };
  }

  function revokeSession(accessToken) {
    if (!accessToken) return;
    DriveStorage.withLock(function () {
      var sessions = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.SESSIONS, { tokens: {}, refreshTokens: {} });
      var tokenHash = hashToken(accessToken);
      var entry = sessions.tokens[tokenHash];
      if (entry) {
        if (entry.refreshHash) delete sessions.refreshTokens[entry.refreshHash];
        delete sessions.tokens[tokenHash];
        DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.SESSIONS, sessions);
      }
    });
  }

  function rotateSession(refreshToken) {
    if (!refreshToken) {
      throw { code: 'INVALID_REFRESH_TOKEN', message: 'Refresh token is required.' };
    }
    return DriveStorage.withLock(function () {
      var sessions = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.SESSIONS, { tokens: {}, refreshTokens: {} });
      var rHash = hashToken(refreshToken);
      var rEntry = sessions.refreshTokens[rHash];
      if (!rEntry || rEntry.expiresAtMs < Utils.nowMs()) {
        throw { code: 'REFRESH_EXPIRED', message: 'Refresh token is invalid or expired. Please sign in again.' };
      }

      if (rEntry.accessHash) delete sessions.tokens[rEntry.accessHash];
      delete sessions.refreshTokens[rHash];

      var now = Utils.nowMs();
      var newAccess = Utils.generateSecureToken('AT');
      var newRefresh = Utils.generateSecureToken('RT');
      var newAHash = hashToken(newAccess);
      var newRHash = hashToken(newRefresh);
      var expiresAtMs = now + CONFIG.SESSION_TTL_MS;
      var refreshExpiresAtMs = now + CONFIG.REFRESH_TOKEN_TTL_MS;

      sessions.tokens[newAHash] = {
        userId: rEntry.userId,
        deviceId: rEntry.deviceId,
        issuedAtMs: now,
        expiresAtMs: expiresAtMs,
        refreshHash: newRHash
      };
      sessions.refreshTokens[newRHash] = {
        userId: rEntry.userId,
        deviceId: rEntry.deviceId,
        issuedAtMs: now,
        expiresAtMs: refreshExpiresAtMs,
        accessHash: newAHash
      };
      DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.SESSIONS, sessions);

      return {
        userId: rEntry.userId,
        accessToken: newAccess,
        refreshToken: newRefresh,
        expiresAt: new Date(expiresAtMs).toISOString(),
        expiresAtMs: expiresAtMs
      };
    });
  }

  function assertConversationAccess(userId, conversationId) {
    Utils.assertSafeId(conversationId, 'conversationId');
    // Check 1:1 conversation or group conversation
    if (conversationId.indexOf('GRP_') === 0) {
      return assertGroupMember(userId, conversationId);
    }
    var convFolder = DriveStorage.getConversationFolder(conversationId, false);
    if (!convFolder) {
      throw { code: 'CONVERSATION_NOT_FOUND', message: 'Conversation does not exist.' };
    }
    var conv = DriveStorage.readJsonFile(convFolder, 'conversation.json', null);
    if (!conv || !Array.isArray(conv.participants) || conv.participants.indexOf(userId) === -1) {
      audit('UNAUTHORIZED_CONVERSATION_ACCESS', userId, { conversationId: conversationId });
      throw { code: 'FORBIDDEN', message: 'You are not a participant of this conversation.' };
    }
    return { type: 'direct', folder: convFolder, metadata: conv };
  }

  function assertGroupMember(userId, groupId) {
    Utils.assertSafeId(groupId, 'groupId');
    var groupFolder = DriveStorage.getGroupFolder(groupId, false);
    if (!groupFolder) {
      throw { code: 'GROUP_NOT_FOUND', message: 'Group does not exist.' };
    }
    var membersDoc = DriveStorage.readJsonFile(groupFolder, 'members.json', { members: [] });
    var member = (membersDoc.members || []).filter(function (m) {
      return m.userId === userId && m.status === 'active';
    })[0];

    if (!member) {
      audit('UNAUTHORIZED_GROUP_ACCESS', userId, { groupId: groupId });
      throw { code: 'FORBIDDEN', message: 'You are not an active member of this group.' };
    }
    var groupInfo = DriveStorage.readJsonFile(groupFolder, 'group_info.json', null);
    return { type: 'group', folder: groupFolder, metadata: groupInfo, membersDoc: membersDoc, member: member };
  }

  function assertGroupAdmin(userId, groupId) {
    var ctx = assertGroupMember(userId, groupId);
    if (ctx.member.role !== 'admin') {
      audit('UNAUTHORIZED_ADMIN_ACTION', userId, { groupId: groupId });
      throw { code: 'FORBIDDEN_ADMIN_REQUIRED', message: 'Only a group administrator can perform this action.' };
    }
    return ctx;
  }

  function audit(eventType, actorUserId, details) {
    try {
      var auditFolder = DriveStorage.getFolderByKey('auditLogs');
      var dateKey = Utils.nowIso().substring(0, 10);
      var filename = 'audit_' + dateKey + '.json';
      var logDoc = DriveStorage.readJsonFile(auditFolder, filename, { date: dateKey, events: [] });
      logDoc.events.push({
        timestamp: Utils.nowIso(),
        eventType: eventType,
        actorUserId: actorUserId || 'ANONYMOUS',
        details: details || {}
      });
      if (logDoc.events.length > 500) {
        logDoc.events = logDoc.events.slice(-500);
      }
      DriveStorage.writeJsonFile(auditFolder, filename, logDoc);
    } catch (ignore) {
      // Audit failures must not crash primary execution once logged in Stackdriver
      console.error('Audit log error: ' + ignore);
    }
  }

  return {
    hashToken: hashToken,
    checkRateLimit: checkRateLimit,
    createSession: createSession,
    authenticate: authenticate,
    revokeSession: revokeSession,
    rotateSession: rotateSession,
    assertConversationAccess: assertConversationAccess,
    assertGroupMember: assertGroupMember,
    assertGroupAdmin: assertGroupAdmin,
    audit: audit
  };
})();
