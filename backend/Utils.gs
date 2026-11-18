/**
 * ConnectMe Backend Utilities
 */
var Utils = (function () {
  function nowIso() {
    return new Date().toISOString();
  }

  function nowMs() {
    return Date.now();
  }

  function jsonOk(data) {
    return {
      success: true,
      data: data === undefined ? {} : data,
      error: null
    };
  }

  function jsonError(code, message, details) {
    return {
      success: false,
      data: null,
      error: {
        code: String(code || 'INTERNAL_ERROR'),
        message: String(message || 'An unexpected error occurred.'),
        details: details || null
      }
    };
  }

  function toTextOutput(obj) {
    return ContentService
      .createTextOutput(JSON.stringify(obj))
      .setMimeType(ContentService.MimeType.JSON);
  }

  function sha256Hex(input) {
    var digest = Utilities.computeDigest(
      Utilities.DigestAlgorithm.SHA_256,
      String(input),
      Utilities.Charset.UTF_8
    );
    return digest.map(function (b) {
      var v = (b < 0 ? b + 256 : b).toString(16);
      return v.length === 1 ? '0' + v : v;
    }).join('');
  }

  function generateSecureToken(prefix) {
    var raw = Utilities.getUuid() + ':' + Utilities.getUuid() + ':' + Date.now() + ':' + Math.random();
    return (prefix ? prefix + '_' : '') + sha256Hex(raw) + sha256Hex(Utilities.getUuid());
  }

  function generateOtpCode() {
    var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, Utilities.getUuid() + Date.now());
    var num = Math.abs(((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16) | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff));
    return ('000000' + (num % 1000000)).slice(-6);
  }

  function sanitizeString(val, maxLen) {
    if (val === null || val === undefined) return '';
    var s = String(val).trim();
    if (maxLen && s.length > maxLen) {
      s = s.substring(0, maxLen);
    }
    return s;
  }

  function normalizeUsername(username) {
    return sanitizeString(username, 32).toLowerCase().replace(/[^a-z0-9_]/g, '');
  }

  function isValidUsername(username) {
    return /^[a-z0-9_]{3,24}$/.test(String(username || ''));
  }

  function normalizePhone(countryCode, phoneNumber) {
    var cc = String(countryCode || '').replace(/[^0-9+]/g, '');
    if (cc && cc.charAt(0) !== '+') cc = '+' + cc;
    var num = String(phoneNumber || '').replace(/[^0-9]/g, '');
    if (!cc || num.length < 6 || num.length > 15) {
      return null;
    }
    return cc + num;
  }

  function maskPhone(e164) {
    if (!e164 || e164.length < 6) return '***';
    return e164.substring(0, 3) + '****' + e164.slice(-3);
  }

  function padCounter(num, width) {
    var s = String(num);
    while (s.length < width) s = '0' + s;
    return s;
  }

  function getFileExtension(filename) {
    if (!filename) return '';
    var parts = String(filename).toLowerCase().split('.');
    return parts.length > 1 ? parts[parts.length - 1] : '';
  }

  function assertSafeId(id, label) {
    if (!id || !/^[A-Za-z0-9_-]{3,80}$/.test(String(id))) {
      throw { code: 'INVALID_INPUT', message: 'Invalid ' + (label || 'identifier') + '.' };
    }
    return String(id);
  }

  return {
    nowIso: nowIso,
    nowMs: nowMs,
    jsonOk: jsonOk,
    jsonError: jsonError,
    toTextOutput: toTextOutput,
    sha256Hex: sha256Hex,
    generateSecureToken: generateSecureToken,
    generateOtpCode: generateOtpCode,
    sanitizeString: sanitizeString,
    normalizeUsername: normalizeUsername,
    isValidUsername: isValidUsername,
    normalizePhone: normalizePhone,
    maskPhone: maskPhone,
    padCounter: padCounter,
    getFileExtension: getFileExtension,
    assertSafeId: assertSafeId
  };
})();
