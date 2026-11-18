/**
 * ConnectMe Google Apps Script Web App Entry Points
 * Deploy as Web App -> Execute as: Me -> Who has access: Anyone
 */

function doGet(e) {
  var params = (e && e.parameter) ? e.parameter : {};
  var action = params.action || 'healthCheck';
  if (action !== 'healthCheck' && action !== 'getAppConfig') {
    return Utils.toTextOutput(
      Utils.jsonError('METHOD_NOT_ALLOWED', 'Use HTTP POST with JSON body for action: ' + action)
    );
  }
  return Utils.toTextOutput(Router.dispatch(action, params));
}

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return Utils.toTextOutput(Utils.jsonError('EMPTY_BODY', 'Request body must be valid JSON.'));
    }
    var body = JSON.parse(e.postData.contents);
    var action = body.action;
    var payload = body.payload || body;
    return Utils.toTextOutput(Router.dispatch(action, payload));
  } catch (parseErr) {
    return Utils.toTextOutput(
      Utils.jsonError('INVALID_JSON', 'Malformed JSON request body: ' + parseErr.message)
    );
  }
}

/**
 * Top-level function runnable directly from the Apps Script Editor.
 * Validates folder ID 1YFrpi1h1L747bTrfexoutVX5zUAT0kKj and initializes all required subfolders.
 */
function initializeDrive() {
  var result = DriveStorage.initializeDrive();
  Logger.log(JSON.stringify(result, null, 2));
  return result;
}

/**
 * Top-level diagnostic helper runnable from the Apps Script Editor.
 */
function runHealthCheck() {
  var result = Router.dispatch('healthCheck', {});
  Logger.log(JSON.stringify(result, null, 2));
  return result;
}
