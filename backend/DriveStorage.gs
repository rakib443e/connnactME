/**
 * ConnectMe Central Google Drive Storage Engine
 * Uses supplied ROOT_FOLDER_ID without creating duplicate root folders.
 */
var DriveStorage = (function () {
  var memFolderCache = {};

  function getProps() {
    return PropertiesService.getScriptProperties();
  }

  function getCache() {
    return CacheService.getScriptCache();
  }

  function withLock(fn) {
    var lock = LockService.getScriptLock();
    var acquired = lock.tryLock(CONFIG.LOCK_TIMEOUT_MS);
    if (!acquired) {
      throw { code: 'BUSY_LOCK_TIMEOUT', message: 'Server is busy processing concurrent writes. Please retry.' };
    }
    try {
      return fn();
    } finally {
      lock.releaseLock();
    }
  }

  function validateRootFolderAccess() {
    var rootId = getProps().getProperty(CONFIG.PROP_KEYS.ROOT_FOLDER_ID) || CONFIG.ROOT_FOLDER_ID;
    try {
      var folder = DriveApp.getFolderById(rootId);
      var name = folder.getName();
      if (folder.isTrashed()) {
        return {
          ok: false,
          code: 'ROOT_FOLDER_TRASHED',
          message: 'Supplied Google Drive root folder (' + rootId + ') is in Trash.',
          remediation: 'Restore folder ' + rootId + ' from Google Drive Trash for the deploying account.'
        };
      }
      return {
        ok: true,
        folder: folder,
        id: folder.getId(),
        name: name
      };
    } catch (e) {
      return {
        ok: false,
        code: 'DRIVE_ACCESS_DENIED',
        message: 'Deploying Google account cannot access root Drive folder ID: ' + rootId + '. Details: ' + e.message,
        remediation:
          '1. Ensure you are logged into the Google account that owns or has Editor access to folder ' + rootId + '. ' +
          '2. Deploy the Apps Script Web App with "Execute as: Me" using that authorized account. ' +
          '3. Run initializeDrive() in the Apps Script editor and grant Google Drive OAuth permissions.'
      };
    }
  }

  function getOrCreateSubfolder(parentFolder, name) {
    var iter = parentFolder.getFoldersByName(name);
    if (iter.hasNext()) {
      return iter.next();
    }
    return parentFolder.createFolder(name);
  }

  function initializeDrive() {
    return withLock(function () {
      var rootCheck = validateRootFolderAccess();
      if (!rootCheck.ok) {
        return Utils.jsonError(rootCheck.code, rootCheck.message, { remediation: rootCheck.remediation });
      }

      var rootFolder = rootCheck.folder;
      var props = getProps();

      // Create or reuse top-level subfolders inside ConnectMe_Database
      var systemFolder = getOrCreateSubfolder(rootFolder, CONFIG.FOLDERS.SYSTEM);
      var usersFolder = getOrCreateSubfolder(rootFolder, CONFIG.FOLDERS.USERS);
      var conversationsFolder = getOrCreateSubfolder(rootFolder, CONFIG.FOLDERS.CONVERSATIONS);
      var groupsFolder = getOrCreateSubfolder(rootFolder, CONFIG.FOLDERS.GROUPS);
      var mediaFolder = getOrCreateSubfolder(rootFolder, CONFIG.FOLDERS.MEDIA);
      var securityFolder = getOrCreateSubfolder(rootFolder, CONFIG.FOLDERS.SECURITY);

      // Media subfolders
      var imagesFolder = getOrCreateSubfolder(mediaFolder, CONFIG.FOLDERS.MEDIA_IMAGES);
      var videosFolder = getOrCreateSubfolder(mediaFolder, CONFIG.FOLDERS.MEDIA_VIDEOS);
      var voiceFolder = getOrCreateSubfolder(mediaFolder, CONFIG.FOLDERS.MEDIA_VOICE);
      var docsFolder = getOrCreateSubfolder(mediaFolder, CONFIG.FOLDERS.MEDIA_DOCUMENTS);

      // Security subfolders
      var auditLogsFolder = getOrCreateSubfolder(securityFolder, CONFIG.FOLDERS.AUDIT_LOGS);

      var folderMap = {
        root: rootFolder.getId(),
        system: systemFolder.getId(),
        users: usersFolder.getId(),
        conversations: conversationsFolder.getId(),
        groups: groupsFolder.getId(),
        media: mediaFolder.getId(),
        mediaImages: imagesFolder.getId(),
        mediaVideos: videosFolder.getId(),
        mediaVoice: voiceFolder.getId(),
        mediaDocuments: docsFolder.getId(),
        security: securityFolder.getId(),
        auditLogs: auditLogsFolder.getId()
      };

      props.setProperty(CONFIG.PROP_KEYS.ROOT_FOLDER_ID, rootFolder.getId());
      props.setProperty(CONFIG.PROP_KEYS.FOLDER_MAP, JSON.stringify(folderMap));
      props.setProperty(CONFIG.PROP_KEYS.INITIALIZED_AT, Utils.nowIso());

      if (!props.getProperty(CONFIG.PROP_KEYS.TOKEN_PEPPER)) {
        props.setProperty(CONFIG.PROP_KEYS.TOKEN_PEPPER, Utils.generateSecureToken('PEP'));
      }

      // Ensure core System JSON files exist without overwriting existing data
      ensureJsonFile(systemFolder, CONFIG.SYSTEM_FILES.APP_CONFIG, {
        appName: CONFIG.APP_NAME,
        apiVersion: CONFIG.API_VERSION,
        rootFolderId: rootFolder.getId(),
        polling: {
          activeChatMs: 3000,
          conversationListMs: 5000,
          otherActiveMs: 15000
        },
        uploadLimits: CONFIG.UPLOAD_LIMITS,
        allowedMedia: CONFIG.ALLOWED_MEDIA,
        initializedAt: Utils.nowIso()
      });

      ensureJsonFile(systemFolder, CONFIG.SYSTEM_FILES.USER_INDEX, { users: {} });
      ensureJsonFile(systemFolder, CONFIG.SYSTEM_FILES.USERNAME_INDEX, { usernames: {} });
      ensureJsonFile(systemFolder, CONFIG.SYSTEM_FILES.PHONE_INDEX, { phones: {} });
      ensureJsonFile(systemFolder, CONFIG.SYSTEM_FILES.DEVICE_INDEX, { devices: {} });
      ensureJsonFile(systemFolder, CONFIG.SYSTEM_FILES.SESSIONS, { tokens: {}, refreshTokens: {} });
      ensureJsonFile(systemFolder, CONFIG.SYSTEM_FILES.COUNTERS, { userSeq: 100000, convSeq: 1000, groupSeq: 1000, mediaSeq: 1000 });
      ensureJsonFile(systemFolder, CONFIG.SYSTEM_FILES.OTP_CHALLENGES, { challenges: {} });
      ensureJsonFile(systemFolder, CONFIG.SYSTEM_FILES.MEDIA_INDEX, { media: {} });
      ensureJsonFile(systemFolder, CONFIG.SYSTEM_FILES.IDEMPOTENCY, { keys: {} });

      memFolderCache = folderMap;
      return Utils.jsonOk({
        rootFolderId: rootFolder.getId(),
        rootFolderName: rootCheck.name,
        folders: folderMap,
        initializedAt: props.getProperty(CONFIG.PROP_KEYS.INITIALIZED_AT)
      });
    });
  }

  function getFolderMap() {
    if (memFolderCache && memFolderCache.root) return memFolderCache;
    var raw = getProps().getProperty(CONFIG.PROP_KEYS.FOLDER_MAP);
    if (raw) {
      memFolderCache = JSON.parse(raw);
      return memFolderCache;
    }
    var init = initializeDrive();
    if (!init.success) {
      throw init.error;
    }
    return init.data.folders;
  }

  function getFolderByKey(key) {
    var map = getFolderMap();
    var id = map[key];
    if (!id) {
      throw { code: 'DRIVE_NOT_INITIALIZED', message: 'Drive folder mapping missing for: ' + key };
    }
    return DriveApp.getFolderById(id);
  }

  function findFileInFolder(folder, filename) {
    var files = folder.getFilesByName(filename);
    if (files.hasNext()) {
      return files.next();
    }
    return null;
  }

  function readJsonFile(folder, filename, defaultValue) {
    var cacheKey = 'JSON_' + folder.getId() + '_' + filename;
    var cached = getCache().get(cacheKey);
    if (cached !== null) {
      try {
        return JSON.parse(cached);
      } catch (ignore) {}
    }

    var file = findFileInFolder(folder, filename);
    if (!file) {
      return defaultValue !== undefined ? defaultValue : null;
    }
    var text = file.getBlob().getDataAsString('UTF-8');
    if (!text) return defaultValue !== undefined ? defaultValue : null;
    var parsed = JSON.parse(text);
    if (text.length < 90000) {
      try { getCache().put(cacheKey, text, CONFIG.CACHE_TTL_SEC); } catch (ignore) {}
    }
    return parsed;
  }

  function writeJsonFile(folder, filename, data) {
    var jsonStr = JSON.stringify(data, null, 2);
    var file = findFileInFolder(folder, filename);
    if (file) {
      file.setContent(jsonStr);
    } else {
      file = folder.createFile(filename, jsonStr, MimeType.PLAIN_TEXT);
    }
    var cacheKey = 'JSON_' + folder.getId() + '_' + filename;
    if (jsonStr.length < 90000) {
      try { getCache().put(cacheKey, jsonStr, CONFIG.CACHE_TTL_SEC); } catch (ignore) {}
    } else {
      try { getCache().remove(cacheKey); } catch (ignore) {}
    }
    return file;
  }

  function ensureJsonFile(folder, filename, initialData) {
    var existing = findFileInFolder(folder, filename);
    if (!existing) {
      writeJsonFile(folder, filename, initialData);
    }
  }

  function getSystemJson(filename, defaultValue) {
    return readJsonFile(getFolderByKey('system'), filename, defaultValue);
  }

  function writeSystemJson(filename, data) {
    return writeJsonFile(getFolderByKey('system'), filename, data);
  }

  function nextId(counterKey, prefix, padWidth) {
    var counters = getSystemJson(CONFIG.SYSTEM_FILES.COUNTERS, { userSeq: 100000, convSeq: 1000, groupSeq: 1000, mediaSeq: 1000 });
    counters[counterKey] = (counters[counterKey] || 0) + 1;
    writeSystemJson(CONFIG.SYSTEM_FILES.COUNTERS, counters);
    return prefix + (padWidth ? Utils.padCounter(counters[counterKey], padWidth) : String(counters[counterKey]));
  }

  function getUserFolder(userId, createIfMissing) {
    Utils.assertSafeId(userId, 'userId');
    var usersRoot = getFolderByKey('users');
    var iter = usersRoot.getFoldersByName(userId);
    if (iter.hasNext()) return iter.next();
    if (createIfMissing) return usersRoot.createFolder(userId);
    return null;
  }

  function getConversationFolder(conversationId, createIfMissing) {
    Utils.assertSafeId(conversationId, 'conversationId');
    var convRoot = getFolderByKey('conversations');
    var iter = convRoot.getFoldersByName(conversationId);
    if (iter.hasNext()) {
      var f = iter.next();
      if (createIfMissing) getOrCreateSubfolder(f, 'Messages');
      return f;
    }
    if (createIfMissing) {
      var created = convRoot.createFolder(conversationId);
      getOrCreateSubfolder(created, 'Messages');
      return created;
    }
    return null;
  }

  function getGroupFolder(groupId, createIfMissing) {
    Utils.assertSafeId(groupId, 'groupId');
    var groupsRoot = getFolderByKey('groups');
    var iter = groupsRoot.getFoldersByName(groupId);
    if (iter.hasNext()) {
      var f = iter.next();
      if (createIfMissing) getOrCreateSubfolder(f, 'Messages');
      return f;
    }
    if (createIfMissing) {
      var created = groupsRoot.createFolder(groupId);
      getOrCreateSubfolder(created, 'Messages');
      return created;
    }
    return null;
  }

  return {
    withLock: withLock,
    validateRootFolderAccess: validateRootFolderAccess,
    initializeDrive: initializeDrive,
    getFolderMap: getFolderMap,
    getFolderByKey: getFolderByKey,
    getOrCreateSubfolder: getOrCreateSubfolder,
    findFileInFolder: findFileInFolder,
    readJsonFile: readJsonFile,
    writeJsonFile: writeJsonFile,
    getSystemJson: getSystemJson,
    writeSystemJson: writeSystemJson,
    nextId: nextId,
    getUserFolder: getUserFolder,
    getConversationFolder: getConversationFolder,
    getGroupFolder: getGroupFolder
  };
})();
