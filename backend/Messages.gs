/**
 * ConnectMe Messages Module
 * Sequence-based storage in 100-message chunks per conversation/group,
 * idempotency protection, incremental polling, pagination, and read receipts.
 */
var Messages = (function () {
  var CHUNK_SIZE = 100;

  function chunkFilename(sequenceNumber) {
    var chunkIndex = Math.floor((sequenceNumber - 1) / CHUNK_SIZE);
    var startSeq = chunkIndex * CHUNK_SIZE + 1;
    var endSeq = startSeq + CHUNK_SIZE - 1;
    return 'messages_' + Utils.padCounter(startSeq, 6) + '_' + Utils.padCounter(endSeq, 6) + '.json';
  }

  function sendMessage(payload, authContext) {
    var conversationId = Utils.assertSafeId(payload.conversationId, 'conversationId');
    var messageType = Utils.sanitizeString(payload.messageType || 'text', 24).toLowerCase();
    var allowedTypes = ['text', 'image', 'video', 'voice', 'document', 'system'];
    if (allowedTypes.indexOf(messageType) === -1) {
      throw { code: 'INVALID_INPUT', message: 'Unsupported messageType: ' + messageType };
    }

    var idempotencyKey = Utils.sanitizeString(payload.idempotencyKey, 96);
    if (!idempotencyKey || idempotencyKey.length < 8) {
      throw { code: 'INVALID_INPUT', message: 'Client-generated idempotencyKey is required.' };
    }

    var content = Utils.sanitizeString(payload.content, 4000);
    var mediaId = payload.mediaId ? Utils.assertSafeId(payload.mediaId, 'mediaId') : null;
    if (messageType === 'text' && !content) {
      throw { code: 'INVALID_INPUT', message: 'Text message content cannot be empty.' };
    }
    if ((messageType === 'image' || messageType === 'video' || messageType === 'voice' || messageType === 'document') && !mediaId) {
      throw { code: 'INVALID_INPUT', message: 'mediaId is required for media messages.' };
    }

    return DriveStorage.withLock(function () {
      var access = Security.assertConversationAccess(authContext.userId, conversationId);

      // Duplicate prevention via idempotency index
      var idemKeyFull = authContext.userId + ':' + conversationId + ':' + idempotencyKey;
      var idemStore = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.IDEMPOTENCY, { keys: {} });
      if (idemStore.keys[idemKeyFull]) {
        return {
          message: idemStore.keys[idemKeyFull],
          deduplicated: true
        };
      }

      var metaFile = access.type === 'group' ? 'group_info.json' : 'conversation.json';
      var meta = DriveStorage.readJsonFile(access.folder, metaFile);

      var receiverId = null;
      if (access.type === 'direct') {
        receiverId = (meta.participants || []).filter(function (id) { return id !== authContext.userId; })[0] || '';
      } else {
        receiverId = conversationId; // group ID for group messages
      }

      var nextSeq = (meta.lastSequence || 0) + 1;
      var now = Utils.nowIso();
      var messageId = 'MSG_' + conversationId + '_' + Utils.padCounter(nextSeq, 6);

      var messageObj = {
        messageId: messageId,
        conversationId: conversationId,
        senderId: authContext.userId, // Strictly server-derived from authenticated session
        receiverId: receiverId,
        messageType: messageType,
        content: content,
        mediaId: mediaId,
        mediaMeta: payload.mediaMeta || null,
        sequenceNumber: nextSeq,
        idempotencyKey: idempotencyKey,
        timestamp: now,
        deliveryStatus: 'server_confirmed',
        readBy: [authContext.userId]
      };

      var messagesFolder = DriveStorage.getOrCreateSubfolder(access.folder, 'Messages');
      var cFile = chunkFilename(nextSeq);
      var chunkDoc = DriveStorage.readJsonFile(messagesFolder, cFile, { messages: [] });
      chunkDoc.messages.push(messageObj);
      DriveStorage.writeJsonFile(messagesFolder, cFile, chunkDoc);

      var msgIndex = DriveStorage.readJsonFile(access.folder, 'message_index.json', {
        conversationId: conversationId,
        lastSequence: 0,
        chunks: []
      });
      msgIndex.lastSequence = nextSeq;
      if (msgIndex.chunks.indexOf(cFile) === -1) {
        msgIndex.chunks.push(cFile);
      }
      DriveStorage.writeJsonFile(access.folder, 'message_index.json', msgIndex);

      meta.lastSequence = nextSeq;
      meta.lastMessageType = messageType;
      meta.lastMessagePreview = messageType === 'text' || messageType === 'system'
        ? content.substring(0, 120)
        : ('[' + messageType.toUpperCase() + '] ' + (content || (payload.mediaMeta && payload.mediaMeta.fileName) || 'Attachment'));
      meta.lastMessageAt = now;
      meta.updatedAt = now;
      meta.readSequenceByUser = meta.readSequenceByUser || {};
      meta.readSequenceByUser[authContext.userId] = nextSeq;
      DriveStorage.writeJsonFile(access.folder, metaFile, meta);

      // Save idempotency record (keep bounded to latest 500 keys)
      var keysList = Object.keys(idemStore.keys);
      if (keysList.length > 500) {
        keysList.slice(0, keysList.length - 450).forEach(function (k) { delete idemStore.keys[k]; });
      }
      idemStore.keys[idemKeyFull] = messageObj;
      DriveStorage.writeSystemJson(CONFIG.SYSTEM_FILES.IDEMPOTENCY, idemStore);

      return {
        message: messageObj,
        deduplicated: false
      };
    });
  }

  function syncMessages(payload, authContext) {
    var conversationId = Utils.assertSafeId(payload.conversationId, 'conversationId');
    var afterSequence = Math.max(0, parseInt(payload.afterSequence || 0, 10) || 0);
    var limit = Math.min(100, Math.max(1, parseInt(payload.limit || 50, 10) || 50));

    var access = Security.assertConversationAccess(authContext.userId, conversationId);
    var metaFile = access.type === 'group' ? 'group_info.json' : 'conversation.json';
    var meta = DriveStorage.readJsonFile(access.folder, metaFile);
    var currentLastSeq = meta.lastSequence || 0;

    // Enforce membership visibility boundary for groups if member joined later? Active members see group history unless removed.
    if (currentLastSeq <= afterSequence) {
      return {
        conversationId: conversationId,
        messages: [],
        lastSequence: currentLastSeq,
        hasMore: false,
        readSequenceByUser: meta.readSequenceByUser || {}
      };
    }

    var messagesFolder = DriveStorage.getOrCreateSubfolder(access.folder, 'Messages');
    var collected = [];
    var targetStart = afterSequence + 1;
    var targetEnd = Math.min(currentLastSeq, afterSequence + limit);

    var startChunk = Math.floor((targetStart - 1) / CHUNK_SIZE);
    var endChunk = Math.floor((targetEnd - 1) / CHUNK_SIZE);

    for (var c = startChunk; c <= endChunk; c++) {
      var seqRepresentative = c * CHUNK_SIZE + 1;
      var cFile = chunkFilename(seqRepresentative);
      var chunkDoc = DriveStorage.readJsonFile(messagesFolder, cFile, { messages: [] });
      (chunkDoc.messages || []).forEach(function (m) {
        if (m.sequenceNumber > afterSequence && m.sequenceNumber <= targetEnd) {
          collected.push(m);
        }
      });
    }

    collected.sort(function (a, b) { return a.sequenceNumber - b.sequenceNumber; });
    var newLastSeq = collected.length > 0 ? collected[collected.length - 1].sequenceNumber : afterSequence;

    return {
      conversationId: conversationId,
      messages: collected,
      lastSequence: newLastSeq,
      latestServerSequence: currentLastSeq,
      hasMore: targetEnd < currentLastSeq,
      readSequenceByUser: meta.readSequenceByUser || {}
    };
  }

  function getMessageHistory(payload, authContext) {
    var conversationId = Utils.assertSafeId(payload.conversationId, 'conversationId');
    var limit = Math.min(100, Math.max(1, parseInt(payload.limit || 30, 10) || 30));

    var access = Security.assertConversationAccess(authContext.userId, conversationId);
    var metaFile = access.type === 'group' ? 'group_info.json' : 'conversation.json';
    var meta = DriveStorage.readJsonFile(access.folder, metaFile);
    var totalSeq = meta.lastSequence || 0;

    var beforeSequence = payload.beforeSequence ? parseInt(payload.beforeSequence, 10) : (totalSeq + 1);
    if (totalSeq === 0 || beforeSequence <= 1) {
      return {
        conversationId: conversationId,
        messages: [],
        lastSequence: totalSeq,
        hasMore: false
      };
    }

    var maxSeq = Math.min(totalSeq, beforeSequence - 1);
    var minSeq = Math.max(1, maxSeq - limit + 1);

    var messagesFolder = DriveStorage.getOrCreateSubfolder(access.folder, 'Messages');
    var collected = [];
    var startChunk = Math.floor((minSeq - 1) / CHUNK_SIZE);
    var endChunk = Math.floor((maxSeq - 1) / CHUNK_SIZE);

    for (var c = startChunk; c <= endChunk; c++) {
      var cFile = chunkFilename(c * CHUNK_SIZE + 1);
      var chunkDoc = DriveStorage.readJsonFile(messagesFolder, cFile, { messages: [] });
      (chunkDoc.messages || []).forEach(function (m) {
        if (m.sequenceNumber >= minSeq && m.sequenceNumber <= maxSeq) {
          collected.push(m);
        }
      });
    }

    collected.sort(function (a, b) { return a.sequenceNumber - b.sequenceNumber; });

    return {
      conversationId: conversationId,
      messages: collected,
      lastSequence: totalSeq,
      hasMore: minSeq > 1
    };
  }

  function markMessageRead(payload, authContext) {
    var conversationId = Utils.assertSafeId(payload.conversationId, 'conversationId');
    var sequenceNumber = parseInt(payload.sequenceNumber || 0, 10) || 0;

    return DriveStorage.withLock(function () {
      var access = Security.assertConversationAccess(authContext.userId, conversationId);
      var metaFile = access.type === 'group' ? 'group_info.json' : 'conversation.json';
      var meta = DriveStorage.readJsonFile(access.folder, metaFile);
      var cappedSeq = Math.min(meta.lastSequence || 0, sequenceNumber);

      meta.readSequenceByUser = meta.readSequenceByUser || {};
      var prev = meta.readSequenceByUser[authContext.userId] || 0;
      if (cappedSeq > prev) {
        meta.readSequenceByUser[authContext.userId] = cappedSeq;
        meta.updatedAt = Utils.nowIso();
        DriveStorage.writeJsonFile(access.folder, metaFile, meta);
      }

      return {
        conversationId: conversationId,
        readSequence: meta.readSequenceByUser[authContext.userId] || 0,
        readSequenceByUser: meta.readSequenceByUser
      };
    });
  }

  return {
    sendMessage: sendMessage,
    syncMessages: syncMessages,
    getMessageHistory: getMessageHistory,
    markMessageRead: markMessageRead
  };
})();
