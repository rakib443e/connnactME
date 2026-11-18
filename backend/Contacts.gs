/**
 * ConnectMe Contacts Module
 */
var Contacts = (function () {
  function addContact(payload, authContext) {
    var contactUserId = Utils.assertSafeId(payload.contactUserId, 'contactUserId');
    if (contactUserId === authContext.userId) {
      throw { code: 'INVALID_INPUT', message: 'You cannot add yourself as a contact.' };
    }
    var targetFolder = DriveStorage.getUserFolder(contactUserId, false);
    if (!targetFolder) {
      throw { code: 'USER_NOT_FOUND', message: 'Target user does not exist.' };
    }
    var targetProfile = DriveStorage.readJsonFile(targetFolder, 'profile.json');

    return DriveStorage.withLock(function () {
      var myFolder = DriveStorage.getUserFolder(authContext.userId, false);
      var doc = DriveStorage.readJsonFile(myFolder, 'contacts.json', { contacts: [] });
      var existing = (doc.contacts || []).filter(function (c) { return c.userId === contactUserId; })[0];
      if (!existing) {
        doc.contacts.push({
          userId: contactUserId,
          nickname: Utils.sanitizeString(payload.nickname, 48) || targetProfile.displayName,
          addedAt: Utils.nowIso()
        });
        DriveStorage.writeJsonFile(myFolder, 'contacts.json', doc);
      }
      return {
        contact: Users.toPublicProfile(targetProfile),
        totalContacts: doc.contacts.length
      };
    });
  }

  function removeContact(payload, authContext) {
    var contactUserId = Utils.assertSafeId(payload.contactUserId, 'contactUserId');
    return DriveStorage.withLock(function () {
      var myFolder = DriveStorage.getUserFolder(authContext.userId, false);
      var doc = DriveStorage.readJsonFile(myFolder, 'contacts.json', { contacts: [] });
      var before = (doc.contacts || []).length;
      doc.contacts = (doc.contacts || []).filter(function (c) { return c.userId !== contactUserId; });
      if (doc.contacts.length !== before) {
        DriveStorage.writeJsonFile(myFolder, 'contacts.json', doc);
      }
      return { removed: doc.contacts.length !== before, contactUserId: contactUserId };
    });
  }

  function getContacts(payload, authContext) {
    var myFolder = DriveStorage.getUserFolder(authContext.userId, false);
    var doc = DriveStorage.readJsonFile(myFolder, 'contacts.json', { contacts: [] });
    var contacts = (doc.contacts || []).map(function (entry) {
      var uFolder = DriveStorage.getUserFolder(entry.userId, false);
      if (!uFolder) return null;
      var prof = DriveStorage.readJsonFile(uFolder, 'profile.json', null);
      if (!prof) return null;
      var pub = Users.toPublicProfile(prof);
      pub.nickname = entry.nickname || pub.displayName;
      pub.addedAt = entry.addedAt;
      return pub;
    }).filter(function (x) { return x !== null; });

    return { contacts: contacts };
  }

  return {
    addContact: addContact,
    removeContact: removeContact,
    getContacts: getContacts
  };
})();
