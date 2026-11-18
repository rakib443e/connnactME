/**
 * ConnectMe Conversations Module
 * Manages 1:1 conversations and user conversation directory index.
 */
var Conversations = (function () {
  function directConvId(userA, userB) {
    var sorted = [String(userA), String(userB)].sort();
    return 'CONV_' + sorted[0] + '_' + sorted[1];
  }

  function addConvRefToUser(userId, conversationId) {
    var uFolder = DriveStorage.getUserFolder(userId, false);
    if (!uFolder) return;
    var index = DriveStorage.readJsonFile(uFolder, 'conversations_index.json', { conversationIds: [] });
    if (index.conversationIds.indexOf(conversationId) === -1) {
      index.conversationIds.push(conversationId);
      DriveStorage.writeJsonFile(uFolder, 'conversations_index.json', index);
    }
  }

  function removeConvRefFromUser(userId, conversationId) {
    var uFolder = DriveStorage.getUserFolder(userId, false);
    if (!uFolder) return;
    var index = DriveStorage.readJsonFile(uFolder, 'conversations_index.json', { conversationIds: [] });
    var filtered = (index.conversationIds || []).filter(function (id) { return id !== conversationId; });
    if (filtered.length !== (index.conversationIds || []).length) {
      index.conversationIds = filtered;
      DriveStorage.writeJsonFile(uFolder, 'conversations_index.json', index);
    }
  }

  function createConversation(payload, authContext) {
    var peerUserId = Utils.assertSafeId(payload.peerUserId, 'peerUserId');
    if (peerUserId === authContext.userId) {
      throw { code: 'INVALID_INPUT', message: 'Cannot create a direct conversation with yourself.' };
    }
    var peerFolder = DriveStorage.getUserFolder(peerUserId, false);
    if (!peerFolder) {
      throw { code: 'USER_NOT_FOUND', message: 'Recipient user does not exist.' };
    }

    var convId = directConvId(authContext.userId, peerUserId);
    var conv = DriveStorage.withLock(function () {
      var convFolder = DriveStorage.getConversationFolder(convId, true);
      var existing = DriveStorage.readJsonFile(convFolder, 'conversation.json', null);
      if (existing) {
        addConvRefToUser(authContext.userId, convId);
        addConvRefToUser(peerUserId, convId);
        return existing;
      }

      var now = Utils.nowIso();
      var created = {
        conversationId: convId,
        type: 'direct',
        participants: [authContext.userId, peerUserId].sort(),
        lastSequence: 0,
        lastMessagePreview: null,
        lastMessageType: null,
        lastMessageAt: now,
        readSequenceByUser: {},
        createdAt: now,
        updatedAt: now
      };
      created.readSequenceByUser[authContext.userId] = 0;
      created.readSequenceByUser[peerUserId] = 0;

      DriveStorage.writeJsonFile(convFolder, 'conversation.json', created);
      DriveStorage.writeJsonFile(convFolder, 'message_index.json', {
        conversationId: convId,
        lastSequence: 0,
        chunks: []
      });

      addConvRefToUser(authContext.userId, convId);
      addConvRefToUser(peerUserId, convId);
      return created;
    });

    return { conversation: enrichConversation(conv, authContext.userId) };
  }

  function enrichConversation(conv, currentUserId) {
    if (conv.type === 'group') {
      var readSeqG = (conv.readSequenceByUser && conv.readSequenceByUser[currentUserId]) || 0;
      var unreadG = Math.max(0, (conv.lastSequence || 0) - readSeqG);
      return {
        conversationId: conv.groupId,
        type: 'group',
        title: conv.name,
        subtitle: conv.description || '',
        photoMediaId: conv.photoMediaId || null,
        peerUser: null,
        groupInfo: conv,
        lastSequence: conv.lastSequence || 0,
        unreadCount: unreadG,
        lastMessagePreview: conv.lastMessagePreview || '',
        lastMessageType: conv.lastMessageType || null,
        lastMessageAt: conv.lastMessageAt || conv.updatedAt,
        updatedAt: conv.updatedAt
      };
    }

    var peerId = (conv.participants || []).filter(function (id) { return id !== currentUserId; })[0];
    var peerUser = null;
    if (peerId) {
      var pFolder = DriveStorage.getUserFolder(peerId, false);
      if (pFolder) {
        var prof = DriveStorage.readJsonFile(pFolder, 'profile.json', null);
        if (prof) peerUser = Users.toPublicProfile(prof);
      }
    }
    var readSeq = (conv.readSequenceByUser && conv.readSequenceByUser[currentUserId]) || 0;
    var unread = Math.max(0, (conv.lastSequence || 0) - readSeq);

    return {
      conversationId: conv.conversationId,
      type: 'direct',
      title: peerUser ? peerUser.displayName : (peerId || 'Conversation'),
      subtitle: peerUser ? ('@' + peerUser.username) : '',
      photoMediaId: peerUser ? peerUser.photoMediaId : null,
      peerUser: peerUser,
      groupInfo: null,
      lastSequence: conv.lastSequence || 0,
      unreadCount: unread,
      lastMessagePreview: conv.lastMessagePreview || '',
      lastMessageType: conv.lastMessageType || null,
      lastMessageAt: conv.lastMessageAt || conv.updatedAt,
      updatedAt: conv.updatedAt
    };
  }

  function getConversations(payload, authContext) {
    var uFolder = DriveStorage.getUserFolder(authContext.userId, false);
    var index = DriveStorage.readJsonFile(uFolder, 'conversations_index.json', { conversationIds: [] });
    var list = [];

    (index.conversationIds || []).forEach(function (cid) {
      try {
        if (cid.indexOf('GRP_') === 0) {
          var gCtx = Security.assertGroupMember(authContext.userId, cid);
          if (gCtx && gCtx.metadata) {
            list.push(enrichConversation(gCtx.metadata, authContext.userId));
          }
        } else {
          var cFolder = DriveStorage.getConversationFolder(cid, false);
          if (cFolder) {
            var conv = DriveStorage.readJsonFile(cFolder, 'conversation.json', null);
            if (conv && conv.participants.indexOf(authContext.userId) !== -1) {
              list.push(enrichConversation(conv, authContext.userId));
            }
          }
        }
      } catch (ignore) {
        // Skip conversations or groups the user was removed from
      }
    });

    list.sort(function (a, b) {
      return String(b.lastMessageAt || '').localeCompare(String(a.lastMessageAt || ''));
    });

    return { conversations: list };
  }

  function getConversationInfo(payload, authContext) {
    var cid = Utils.assertSafeId(payload.conversationId, 'conversationId');
    var access = Security.assertConversationAccess(authContext.userId, cid);
    return { conversation: enrichConversation(access.metadata, authContext.userId) };
  }

  return {
    addConvRefToUser: addConvRefToUser,
    removeConvRefFromUser: removeConvRefFromUser,
    createConversation: createConversation,
    enrichConversation: enrichConversation,
    getConversations: getConversations,
    getConversationInfo: getConversationInfo
  };
})();
