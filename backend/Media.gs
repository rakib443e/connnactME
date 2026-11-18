/**
 * ConnectMe Media Module
 * Validated private Google Drive media storage with chunked upload support
 * and strict conversation/group membership verification before serving downloads.
 */
var Media = (function () {
  function categoryToFolderKey(category) {
    switch (category) {
      case 'image': return 'mediaImages';
      case 'video': return 'mediaVideos';
      case 'voice': return 'mediaVoice';
      case 'document': return 'mediaDocuments';
      default: throw { code: 'INVALID_MEDIA_CATEGORY', message: 'Unsupported media category: ' + category };
    }
  }

  function maxBytesForCategory(category) {
    switch (category) {
      case 'image': return CONFIG.UPLOAD_LIMITS.IMAGE_MAX_BYTES;
      case 'video': return CONFIG.UPLOAD_LIMITS.VIDEO_MAX_BYTES;
      case 'voice': return CONFIG.UPLOAD_LIMITS.VOICE_MAX_BYTES;
      case 'document': return CONFIG.UPLOAD_LIMITS.DOCUMENT_MAX_BYTES;
      default: return 0;
    }
  }

  function validateMediaMetadata(category, fileName, mimeType, sizeBytes) {
    var cat = String(category || '').toLowerCase();
    var allowedExts = CONFIG.ALLOWED_MEDIA[cat];
    var allowedMimes = CONFIG.ALLOWED_MIME[cat];
    if (!allowedExts || !allowedMimes) {
      throw { code: 'INVALID_MEDIA_CATEGORY', message: 'Unsupported media category: ' + cat };
    }
    var ext = Utils.getFileExtension(fileName);
    if (allowedExts.indexOf(ext) === -1) {
      throw {
        code: 'UNSUPPORTED_FILE_EXTENSION',
        message: 'File extension .' + ext + ' is not allowed for ' + cat + '. Allowed: ' + allowedExts.join(', ')
      };
    }
    if (mimeType && allowedMimes.indexOf(String(mimeType).toLowerCase()) === -1) {
      throw {
        code: 'UNSUPPORTED_MIME_TYPE',
        message: 'MIME type ' + mimeType + ' is not allowed for ' + cat + '.'
      };
    }
    var maxBytes = maxBytesForCategory(cat);
    if (sizeBytes <= 0 || sizeBytes > maxBytes) {
      throw {
        code: 'FILE_TOO_LARGE',
        message: 'File size exceeds maximum limit of ' + Math.round(maxBytes / (1024 * 1024)) + ' MB for ' + cat + '.'
      };
    }
  }

  function uploadMediaInternal(payload, authContext) {
    Security.checkRateLimit('UPLOAD_' + authContext.userId, CONFIG.RATE_LIMITS.UPLOAD_PER_MINUTE, 60);

    var category = Utils.sanitizeString(payload.category, 24).toLowerCase();
    var fileName = Utils.sanitizeString(payload.fileName, 128);
    var mimeType = Utils.sanitizeString(payload.mimeType, 96).toLowerCase();
    var conversationId = payload.conversationId ? Utils.sanitizeString(payload.conversationId, 80) : null;

    if (!payload.isProfileAvatar && conversationId) {
      Security.assertConversationAccess(authContext.userId, conversationId);
    }

    // Chunked staging support for large files respecting GAS HTTP payload limits
    var chunkIndex = payload.chunkIndex !== undefined ? parseInt(payload.chunkIndex, 10) : 0;
    var totalChunks = payload.totalChunks !== undefined ? parseInt(payload.totalChunks, 10) : 1;
    var uploadSessionId = payload.uploadSessionId ? Utils.assertSafeId(payload.uploadSessionId, 'uploadSessionId') : null;

    if (!payload.base64Data) {
      throw { code: 'INVALID_INPUT', message: 'base64Data is required.' };
    }

    var chunkBytes = Utilities.base64Decode(payload.base64Data);
    var declaredTotalBytes = payload.totalBytes ? parseInt(payload.totalBytes, 10) : chunkBytes.length;
    validateMediaMetadata(category, fileName, mimeType, declaredTotalBytes);

    if (totalChunks > 1) {
      if (!uploadSessionId) {
        uploadSessionId = Utils.generateSecureToken('UPL').substring(0, 20);
      }
      var stagingFolder = DriveStorage.getOrCreateSubfolder(DriveStorage.getFolderByKey('media'), '_staging_' + uploadSessionId);
      stagingFolder.createFile('part_' + Utils.padCounter(chunkIndex, 4) + '.b64', payload.base64Data, MimeType.PLAIN_TEXT);

      if (chunkIndex < totalChunks - 1) {
        return {
          completed: false,
          uploadSessionId: uploadSessionId,
          chunkIndex: chunkIndex,
          totalChunks: totalChunks
        };
      }

      // Assemble all chunks in order on final chunk
      var combinedBytes = [];
      for (var i = 0; i < totalChunks; i++) {
        var partFile = DriveStorage.findFileInFolder(stagingFolder, 'part_' + Utils.padCounter(i, 4) + '.b64');
        if (!partFile) {
          throw { code: 'INCOMPLETE_UPLOAD', message: 'Missing upload chunk ' + i };
        }
        var partB64 = partFile.getBlob().getDataAsString('UTF-8');
        var decoded = Utilities.base64Decode(partB64);
        combinedBytes = combinedBytes.concat(decoded);
      }
      stagingFolder.setTrashed(true);
      chunkBytes = combinedBytes;
      validateMediaMetadata(category, fileName, mimeType, chunkBytes.length);
    }

    return DriveStorage.withLock(function () {
      var mediaId = DriveStorage.nextId('mediaSeq', 'MED_', 7);
      var targetFolder = DriveStorage.getFolderByKey(categoryToFolderKey(category));
      var storedName = mediaId + '_' + fileName.replace(/[^A-Za-z0-9._-]/g, '_');
      var blob = Utilities.newBlob(chunkBytes, mimeType || 'application/octet-stream', storedName);
      var file = targetFolder.createFile(blob);

      // Explicitly ensure private sharing (never public by default)
      try {
        file.setSharing(DriveApp.Access.PRIVATE, DriveApp.Permission.NONE);
      } catch (ignore) {}

      var record = {
        mediaId: mediaId,
        driveFileId: file.getId(),
        category: category,
        fileName: fileName,
        mimeType: mimeType || file.getMimeType(),
        sizeBytes: chunkBytes.length,
        uploaderId: authContext.userId,
        conversationId: conversationId,
        isProfileAvatar: Boolean(payload.isProfileAvatar),
        createdAt: Utils.nowIso()
      };

      var mediaIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.MEDIA_INDEX, { media: {} });
      mediaIndex.media[mediaId] = record;
      DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.MEDIA_INDEX, mediaIndex);

      Security.audit('MEDIA_UPLOADED', authContext.userId, { mediaId: mediaId, category: category, sizeBytes: record.sizeBytes });
      return {
        completed: true,
        media: record
      };
    });
  }

  function uploadMedia(payload, authContext) {
    return uploadMediaInternal(payload, authContext);
  }

  function getMedia(payload, authContext) {
    var mediaId = Utils.assertSafeId(payload.mediaId, 'mediaId');
    var mediaIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.MEDIA_INDEX, { media: {} });
    var record = mediaIndex.media[mediaId];
    if (!record) {
      throw { code: 'MEDIA_NOT_FOUND', message: 'Media record not found.' };
    }

    // Profile avatars are accessible to authenticated ConnectMe users; chat media requires membership
    if (!record.isProfileAvatar && record.conversationId) {
      Security.assertConversationAccess(authContext.userId, record.conversationId);
    }

    var file = DriveApp.getFileById(record.driveFileId);
    if (file.isTrashed()) {
      throw { code: 'MEDIA_DELETED', message: 'Media file is no longer available.' };
    }

    var bytes = file.getBlob().getBytes();
    var base64Data = Utilities.base64Encode(bytes);

    return {
      media: record,
      base64Data: base64Data
    };
  }

  function deleteMedia(payload, authContext) {
    var mediaId = Utils.assertSafeId(payload.mediaId, 'mediaId');
    return DriveStorage.withLock(function () {
      var mediaIndex = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.MEDIA_INDEX, { media: {} });
      var record = mediaIndex.media[mediaId];
      if (!record) {
        throw { code: 'MEDIA_NOT_FOUND', message: 'Media record not found.' };
      }

      var authorized = record.uploaderId === authContext.userId;
      if (!authorized && record.conversationId && record.conversationId.indexOf('GRP_') === 0) {
        try {
          Security.assertGroupAdmin(authContext.userId, record.conversationId);
          authorized = true;
        } catch (ignore) {}
      }

      if (!authorized) {
        Security.audit('UNAUTHORIZED_MEDIA_DELETE', authContext.userId, { mediaId: mediaId });
        throw { code: 'FORBIDDEN', message: 'You do not have permission to delete this media.' };
      }

      try {
        var file = DriveApp.getFileById(record.driveFileId);
        file.setTrashed(true);
      } catch (ignore) {}

      delete mediaIndex.media[mediaId];
      DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.MEDIA_INDEX, mediaIndex);
      Security.audit('MEDIA_DELETED', authContext.userId, { mediaId: mediaId });

      return { deleted: true, mediaId: mediaId };
    });
  }

  return {
    uploadMediaInternal: uploadMediaInternal,
    uploadMedia: uploadMedia,
    getMedia: getMedia,
    deleteMedia: deleteMedia
  };
})();
