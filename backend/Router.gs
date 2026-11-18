/**
 * ConnectMe JSON API Router
 */
var Router = (function () {
  var PUBLIC_ACTIONS = {
    healthCheck: true,
    initializeDrive: true,
    getAppConfig: true,
    guestRegister: true,
    guestLogin: true,
    requestPhoneOtp: true,
    verifyPhoneOtp: true,
    refreshSession: true
  };

  function dispatch(action, payload) {
    try {
      if (!action) {
        return Utils.jsonError('MISSING_ACTION', 'API action is required.');
      }
      var safePayload = payload && typeof payload === 'object' ? payload : {};

      // Global rate limit
      Security.checkRateLimit('GLOBAL_' + action, CONFIG.RATE_LIMITS.DEFAULT_PER_MINUTE, 60);

      var authContext = null;
      if (!PUBLIC_ACTIONS[action]) {
        authContext = Security.authenticate(safePayload);
      } else if (safePayload.accessToken && action === 'verifyPhoneOtp') {
        // Optional auth context when upgrading guest -> phone
        authContext = Security.authenticate(safePayload);
      }

      var data;
      switch (action) {
        // SYSTEM
        case 'healthCheck':
          var rootCheck = DriveStorage.validateRootFolderAccess();
          data = {
            status: rootCheck.ok ? 'healthy' : 'drive_authorization_required',
            appName: CONFIG.APP_NAME,
            apiVersion: CONFIG.API_VERSION,
            rootFolderId: CONFIG.ROOT_FOLDER_ID,
            driveAccess: rootCheck.ok,
            driveMessage: rootCheck.ok ? ('Connected to ' + rootCheck.name) : rootCheck.message,
            remediation: rootCheck.ok ? null : rootCheck.remediation,
            smsConfigured: SmsProviderAdapter.isConfigured(),
            timestamp: Utils.nowIso()
          };
          break;
        case 'initializeDrive':
          return DriveStorage.initializeDrive();
        case 'getAppConfig':
          data = DriveStorage.getSystemJson(CONFIG.SYSTEM_FILES.APP_CONFIG, {
            appName: CONFIG.APP_NAME,
            apiVersion: CONFIG.API_VERSION,
            uploadLimits: CONFIG.UPLOAD_LIMITS,
            allowedMedia: CONFIG.ALLOWED_MEDIA
          });
          break;

        // AUTH
        case 'guestRegister':
          Security.checkRateLimit('AUTH_REG', CONFIG.RATE_LIMITS.AUTH_PER_MINUTE, 60);
          data = Auth.guestRegister(safePayload);
          break;
        case 'guestLogin':
          Security.checkRateLimit('AUTH_LOGIN', CONFIG.RATE_LIMITS.AUTH_PER_MINUTE, 60);
          data = Auth.guestLogin(safePayload);
          break;
        case 'requestPhoneOtp':
          data = Auth.requestPhoneOtp(safePayload);
          break;
        case 'verifyPhoneOtp':
          data = Auth.verifyPhoneOtp(safePayload, authContext);
          break;
        case 'refreshSession':
          data = Auth.refreshSession(safePayload);
          break;
        case 'logout':
          data = Auth.logout(safePayload, authContext);
          break;
        case 'getMyProfile':
          data = Auth.getMyProfile(authContext);
          break;

        // USERS
        case 'searchUser':
          data = Users.searchUser(safePayload, authContext);
          break;
        case 'getUserProfile':
          data = Users.getUserProfile(safePayload, authContext);
          break;
        case 'updateProfile':
          data = Users.updateProfile(safePayload, authContext);
          break;
        case 'updateUsername':
          data = Users.updateUsername(safePayload, authContext);
          break;
        case 'uploadProfilePhoto':
          data = Users.uploadProfilePhoto(safePayload, authContext);
          break;

        // CONTACTS
        case 'addContact':
          data = Contacts.addContact(safePayload, authContext);
          break;
        case 'removeContact':
          data = Contacts.removeContact(safePayload, authContext);
          break;
        case 'getContacts':
          data = Contacts.getContacts(safePayload, authContext);
          break;

        // CONVERSATIONS
        case 'createConversation':
          data = Conversations.createConversation(safePayload, authContext);
          break;
        case 'getConversations':
          data = Conversations.getConversations(safePayload, authContext);
          break;
        case 'getConversationInfo':
          data = Conversations.getConversationInfo(safePayload, authContext);
          break;

        // MESSAGES
        case 'sendMessage':
          data = Messages.sendMessage(safePayload, authContext);
          break;
        case 'syncMessages':
          data = Messages.syncMessages(safePayload, authContext);
          break;
        case 'getMessageHistory':
          data = Messages.getMessageHistory(safePayload, authContext);
          break;
        case 'markMessageRead':
          data = Messages.markMessageRead(safePayload, authContext);
          break;

        // GROUPS
        case 'createGroup':
          data = Groups.createGroup(safePayload, authContext);
          break;
        case 'getGroupInfo':
          data = Groups.getGroupInfo(safePayload, authContext);
          break;
        case 'addGroupMember':
          data = Groups.addGroupMember(safePayload, authContext);
          break;
        case 'removeGroupMember':
          data = Groups.removeGroupMember(safePayload, authContext);
          break;
        case 'updateGroupInfo':
          data = Groups.updateGroupInfo(safePayload, authContext);
          break;
        case 'getGroupMessages':
          data = Groups.getGroupMessages(safePayload, authContext);
          break;

        // MEDIA
        case 'uploadMedia':
          data = Media.uploadMedia(safePayload, authContext);
          break;
        case 'getMedia':
          data = Media.getMedia(safePayload, authContext);
          break;
        case 'deleteMedia':
          data = Media.deleteMedia(safePayload, authContext);
          break;

        default:
          return Utils.jsonError('UNKNOWN_ACTION', 'Unknown API action: ' + action);
      }

      return Utils.jsonOk(data);
    } catch (err) {
      if (err && err.code && err.message) {
        return Utils.jsonError(err.code, err.message, err.details || null);
      }
      console.error('Unhandled router error [' + action + ']: ' + (err && err.stack ? err.stack : err));
      return Utils.jsonError('INTERNAL_ERROR', err && err.message ? err.message : 'Server error.');
    }
  }

  return {
    dispatch: dispatch
  };
})();
