/**
 * ConnectMe Groups Module
 * Server-enforced group creation, membership management, admin permissions, and revocation.
 */
var Groups = (function () {
  function createGroup(payload, authContext) {
    var name = Utils.sanitizeString(payload.name, 64);
    if (!name) {
      throw { code: 'INVALID_INPUT', message: 'Group name is required.' };
    }
    var description = Utils.sanitizeString(payload.description, 240);
    var initialMemberIds = Array.isArray(payload.memberIds) ? payload.memberIds : [];

    var uniqueIds = [authContext.userId];
    initialMemberIds.forEach(function (uid) {
      if (uid && uid !== authContext.userId) {
        Utils.assertSafeId(uid, 'memberId');
        if (uniqueIds.indexOf(uid) === -1 && DriveStorage.getUserFolder(uid, false)) {
          uniqueIds.push(uid);
        }
      }
    });

    var result = DriveStorage.withLock(function () {
      var groupId = DriveStorage.nextId('groupSeq', 'GRP_', 6);
      var groupFolder = DriveStorage.getGroupFolder(groupId, true);
      var now = Utils.nowIso();

      var members = uniqueIds.map(function (uid) {
        return {
          userId: uid,
          role: uid === authContext.userId ? 'admin' : 'member',
          status: 'active',
          joinedAt: now,
          addedBy: authContext.userId
        };
      });

      var groupInfo = {
        groupId: groupId,
        type: 'group',
        name: name,
        description: description,
        photoMediaId: null,
        createdBy: authContext.userId,
        adminIds: [authContext.userId],
        memberCount: members.length,
        lastSequence: 0,
        lastMessagePreview: 'Group "' + name + '" created',
        lastMessageType: 'system',
        lastMessageAt: now,
        readSequenceByUser: {},
        createdAt: now,
        updatedAt: now
      };
      uniqueIds.forEach(function (uid) { groupInfo.readSequenceByUser[uid] = 0; });

      DriveStorage.writeJsonFile(groupFolder, 'group_info.json', groupInfo);
      DriveStorage.writeJsonFile(groupFolder, 'members.json', { groupId: groupId, members: members });
      DriveStorage.writeJsonFile(groupFolder, 'message_index.json', { conversationId: groupId, lastSequence: 0, chunks: [] });

      uniqueIds.forEach(function (uid) {
        Conversations.addConvRefToUser(uid, groupId);
      });

      Security.audit('GROUP_CREATED', authContext.userId, { groupId: groupId, memberCount: members.length });
      return { groupInfo: groupInfo, members: members };
    });

    return {
      group: hydrateGroup(result.groupInfo, result.members)
    };
  }

  function hydrateGroup(groupInfo, members) {
    var hydratedMembers = (members || [])
      .filter(function (m) { return m.status === 'active'; })
      .map(function (m) {
        var uFolder = DriveStorage.getUserFolder(m.userId, false);
        var prof = uFolder ? DriveStorage.readJsonFile(uFolder, 'profile.json', null) : null;
        return {
          userId: m.userId,
          role: m.role,
          joinedAt: m.joinedAt,
          profile: prof ? Users.toPublicProfile(prof) : null
        };
      });

    return {
      groupId: groupInfo.groupId,
      name: groupInfo.name,
      description: groupInfo.description || '',
      photoMediaId: groupInfo.photoMediaId || null,
      createdBy: groupInfo.createdBy,
      adminIds: groupInfo.adminIds || [],
      memberCount: hydratedMembers.length,
      lastSequence: groupInfo.lastSequence || 0,
      createdAt: groupInfo.createdAt,
      updatedAt: groupInfo.updatedAt,
      members: hydratedMembers
    };
  }

  function getGroupInfo(payload, authContext) {
    var groupId = Utils.assertSafeId(payload.groupId, 'groupId');
    var ctx = Security.assertGroupMember(authContext.userId, groupId);
    return {
      group: hydrateGroup(ctx.metadata, ctx.membersDoc.members)
    };
  }

  function addGroupMember(payload, authContext) {
    var groupId = Utils.assertSafeId(payload.groupId, 'groupId');
    var targetUserId = Utils.assertSafeId(payload.userId, 'userId');
    if (!DriveStorage.getUserFolder(targetUserId, false)) {
      throw { code: 'USER_NOT_FOUND', message: 'User to add does not exist.' };
    }

    return DriveStorage.withLock(function () {
      var ctx = Security.assertGroupAdmin(authContext.userId, groupId);
      var members = ctx.membersDoc.members || [];
      var existing = members.filter(function (m) { return m.userId === targetUserId; })[0];
      var now = Utils.nowIso();

      if (existing) {
        if (existing.status === 'active') {
          throw { code: 'ALREADY_MEMBER', message: 'User is already an active member of this group.' };
        }
        existing.status = 'active';
        existing.role = 'member';
        existing.joinedAt = now;
        existing.addedBy = authContext.userId;
        delete existing.removedAt;
      } else {
        members.push({
          userId: targetUserId,
          role: 'member',
          status: 'active',
          joinedAt: now,
          addedBy: authContext.userId
        });
      }

      DriveStorage.writeJsonFile(ctx.folder, 'members.json', { groupId: groupId, members: members });

      var activeCount = members.filter(function (m) { return m.status === 'active'; }).length;
      ctx.metadata.memberCount = activeCount;
      ctx.metadata.updatedAt = now;
      ctx.metadata.readSequenceByUser = ctx.metadata.readSequenceByUser || {};
      ctx.metadata.readSequenceByUser[targetUserId] = ctx.metadata.lastSequence || 0;
      DriveStorage.writeJsonFile(ctx.folder, 'group_info.json', ctx.metadata);

      Conversations.addConvRefToUser(targetUserId, groupId);
      Security.audit('GROUP_MEMBER_ADDED', authContext.userId, { groupId: groupId, targetUserId: targetUserId });

      return { group: hydrateGroup(ctx.metadata, members) };
    });
  }

  function removeGroupMember(payload, authContext) {
    var groupId = Utils.assertSafeId(payload.groupId, 'groupId');
    var targetUserId = Utils.assertSafeId(payload.userId, 'userId');

    return DriveStorage.withLock(function () {
      // Admin can remove anyone; non-admin can only remove self (leave group)
      var ctx = Security.assertGroupMember(authContext.userId, groupId);
      if (targetUserId !== authContext.userId && ctx.member.role !== 'admin') {
        Security.audit('UNAUTHORIZED_REMOVE_MEMBER', authContext.userId, { groupId: groupId, targetUserId: targetUserId });
        throw { code: 'FORBIDDEN_ADMIN_REQUIRED', message: 'Only a group administrator can remove other members.' };
      }

      var members = ctx.membersDoc.members || [];
      var target = members.filter(function (m) { return m.userId === targetUserId && m.status === 'active'; })[0];
      if (!target) {
        throw { code: 'MEMBER_NOT_FOUND', message: 'Target user is not an active member of this group.' };
      }

      var activeAdmins = members.filter(function (m) { return m.status === 'active' && m.role === 'admin' && m.userId !== targetUserId; });
      if (target.role === 'admin' && activeAdmins.length === 0) {
        var nextActive = members.filter(function (m) { return m.status === 'active' && m.userId !== targetUserId; })[0];
        if (nextActive) {
          nextActive.role = 'admin';
        }
      }

      target.status = 'removed';
      target.removedAt = Utils.nowIso();
      target.removedBy = authContext.userId;

      DriveStorage.writeJsonFile(ctx.folder, 'members.json', { groupId: groupId, members: members });

      ctx.metadata.adminIds = members.filter(function (m) { return m.status === 'active' && m.role === 'admin'; }).map(function (m) { return m.userId; });
      ctx.metadata.memberCount = members.filter(function (m) { return m.status === 'active'; }).length;
      ctx.metadata.updatedAt = Utils.nowIso();
      DriveStorage.writeJsonFile(ctx.folder, 'group_info.json', ctx.metadata);

      Conversations.removeConvRefFromUser(targetUserId, groupId);
      Security.audit('GROUP_MEMBER_REMOVED', authContext.userId, { groupId: groupId, targetUserId: targetUserId });

      return { group: hydrateGroup(ctx.metadata, members), removedUserId: targetUserId };
    });
  }

  function updateGroupInfo(payload, authContext) {
    var groupId = Utils.assertSafeId(payload.groupId, 'groupId');
    return DriveStorage.withLock(function () {
      var ctx = Security.assertGroupAdmin(authContext.userId, groupId);
      if (payload.name !== undefined) {
        var nm = Utils.sanitizeString(payload.name, 64);
        if (!nm) throw { code: 'INVALID_INPUT', message: 'Group name cannot be empty.' };
        ctx.metadata.name = nm;
      }
      if (payload.description !== undefined) {
        ctx.metadata.description = Utils.sanitizeString(payload.description, 240);
      }
      if (payload.photoMediaId !== undefined) {
        ctx.metadata.photoMediaId = payload.photoMediaId ? Utils.assertSafeId(payload.photoMediaId, 'photoMediaId') : null;
      }
      ctx.metadata.updatedAt = Utils.nowIso();
      DriveStorage.writeJsonFile(ctx.folder, 'group_info.json', ctx.metadata);
      Security.audit('GROUP_UPDATED', authContext.userId, { groupId: groupId });
      return { group: hydrateGroup(ctx.metadata, ctx.membersDoc.members) };
    });
  }

  function getGroupMessages(payload, authContext) {
    var groupId = Utils.assertSafeId(payload.groupId, 'groupId');
    Security.assertGroupMember(authContext.userId, groupId);
    if (payload.afterSequence !== undefined) {
      return Messages.syncMessages({
        conversationId: groupId,
        afterSequence: payload.afterSequence,
        limit: payload.limit
      }, authContext);
    }
    return Messages.getMessageHistory({
      conversationId: groupId,
      beforeSequence: payload.beforeSequence,
      limit: payload.limit
    }, authContext);
  }

  return {
    createGroup: createGroup,
    getGroupInfo: getGroupInfo,
    addGroupMember: addGroupMember,
    removeGroupMember: removeGroupMember,
    updateGroupInfo: updateGroupInfo,
    getGroupMessages: getGroupMessages
  };
})();
