package com.example.core.network

import com.example.core.config.AppSettingsStore
import com.example.core.security.SecureTokenStorage
import com.example.core.security.StoredSession
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

interface ConnectMeApi {
  suspend fun healthCheck(): HealthCheckDto

  suspend fun initializeDrive(): Map<String, Any?>

  suspend fun guestRegister(
    deviceId: String,
    displayName: String,
    username: String? = null,
  ): AuthResponseDto

  suspend fun guestLogin(deviceId: String, userId: String? = null): AuthResponseDto

  suspend fun requestPhoneOtp(
    countryCode: String,
    phoneNumber: String,
    upgradeUserId: String? = null,
  ): OtpChallengeResponseDto

  suspend fun verifyPhoneOtp(
    challengeId: String,
    countryCode: String,
    phoneNumber: String,
    otpCode: String,
    displayName: String? = null,
    deviceId: String? = null,
  ): AuthResponseDto

  suspend fun refreshSession(refreshToken: String): AuthResponseDto

  suspend fun logout()

  suspend fun getMyProfile(): UserProfileDto

  suspend fun searchUser(query: String, defaultCountryCode: String = "+1"): List<UserProfileDto>

  suspend fun getUserProfile(userId: String): UserProfileDto

  suspend fun updateProfile(
    displayName: String? = null,
    bio: String? = null,
    privacy: PrivacySettingsDto? = null,
  ): UserProfileDto

  suspend fun updateUsername(username: String): UserProfileDto

  suspend fun uploadProfilePhoto(
    fileName: String,
    mimeType: String,
    base64Data: String,
  ): String

  suspend fun addContact(contactUserId: String, nickname: String? = null): UserProfileDto

  suspend fun removeContact(contactUserId: String)

  suspend fun getContacts(): List<UserProfileDto>

  suspend fun createConversation(peerUserId: String): ConversationDto

  suspend fun getConversations(): List<ConversationDto>

  suspend fun getConversationInfo(conversationId: String): ConversationDto

  suspend fun sendMessage(
    conversationId: String,
    messageType: String,
    content: String,
    idempotencyKey: String,
    mediaId: String? = null,
    mediaMeta: MediaMetaDto? = null,
  ): MessageDto

  suspend fun syncMessages(
    conversationId: String,
    afterSequence: Long,
    limit: Int = 50,
  ): SyncMessagesResponseDto

  suspend fun getMessageHistory(
    conversationId: String,
    beforeSequence: Long? = null,
    limit: Int = 30,
  ): SyncMessagesResponseDto

  suspend fun markMessageRead(conversationId: String, sequenceNumber: Long)

  suspend fun createGroup(
    name: String,
    description: String,
    memberIds: List<String>,
  ): GroupInfoDto

  suspend fun getGroupInfo(groupId: String): GroupInfoDto

  suspend fun addGroupMember(groupId: String, userId: String): GroupInfoDto

  suspend fun removeGroupMember(groupId: String, userId: String): GroupInfoDto

  suspend fun updateGroupInfo(
    groupId: String,
    name: String? = null,
    description: String? = null,
    photoMediaId: String? = null,
  ): GroupInfoDto

  suspend fun uploadMediaChunk(
    category: String,
    fileName: String,
    mimeType: String,
    conversationId: String?,
    base64Data: String,
    totalBytes: Long,
    chunkIndex: Int,
    totalChunks: Int,
    uploadSessionId: String?,
  ): MediaUploadResponseDto

  suspend fun getMedia(mediaId: String): MediaDownloadDto

  suspend fun deleteMedia(mediaId: String)
}

class OkHttpAppsScriptClient(
  private val settingsStore: AppSettingsStore,
  private val tokenStorage: SecureTokenStorage,
  private val httpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(25, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build(),
) : ConnectMeApi {

  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private suspend fun requireEndpointUrl(): String {
    val url = settingsStore.getBackendUrl()
    if (url.isBlank() || !url.startsWith("https://")) {
      throw ApiException(
        code = "BACKEND_NOT_CONFIGURED",
        message =
          "Apps Script Web App HTTPS URL is not configured. Open Backend Settings and paste your deployed Web App URL.",
      )
    }
    return url
  }

  private suspend fun <T> postAction(
    action: String,
    payload: JSONObject = JSONObject(),
    requiresAuth: Boolean = true,
    allowRetryOnExpired: Boolean = true,
    parseData: (String) -> T,
  ): T {
    val url = requireEndpointUrl()
    val bodyJson = JSONObject()
    bodyJson.put("action", action)

    val finalPayload = JSONObject(payload.toString())
    if (requiresAuth) {
      val session =
        tokenStorage.getSession()
          ?: throw ApiException("UNAUTHORIZED", "Not signed in. Please register or log in.")
      finalPayload.put("accessToken", session.accessToken)
    }
    bodyJson.put("payload", finalPayload)

    val rawResponse = executePost(url, bodyJson.toString())
    val envelope = JSONObject(rawResponse)
    val success = envelope.optBoolean("success", false)

    if (!success) {
      val errObj = envelope.optJSONObject("error")
      val code = errObj?.optString("code", "API_ERROR") ?: "API_ERROR"
      val message = errObj?.optString("message", "Request failed") ?: "Request failed"
      val remediation = errObj?.optJSONObject("details")?.optString("remediation")

      if (requiresAuth && allowRetryOnExpired && code == "SESSION_EXPIRED") {
        val current = tokenStorage.getSession()
        if (current != null) {
          val refreshed = refreshSession(current.refreshToken)
          tokenStorage.saveSession(
            StoredSession(
              accessToken = refreshed.session.accessToken,
              refreshToken = refreshed.session.refreshToken,
              expiresAtMs = refreshed.session.expiresAtMs,
              userId = refreshed.user.userId,
            )
          )
          return postAction(action, payload, requiresAuth = true, allowRetryOnExpired = false, parseData)
        }
      }
      throw ApiException(code, message, remediation)
    }

    val dataJson =
      when (val d = envelope.opt("data")) {
        null, JSONObject.NULL -> "{}"
        else -> d.toString()
      }
    return parseData(dataJson)
  }

  private suspend fun executePost(url: String, jsonBody: String): String =
    withContext(Dispatchers.IO) {
      val request =
        Request.Builder()
          .url(url)
          .post(jsonBody.toRequestBody(jsonMediaType))
          .header("Accept", "application/json")
          .build()
      try {
        httpClient.newCall(request).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) {
            throw ApiException("HTTP_${resp.code}", "Backend returned HTTP ${resp.code}.")
          }
          if (!body.trimStart().startsWith("{")) {
            throw ApiException(
              "INVALID_BACKEND_RESPONSE",
              "Backend did not return JSON. Verify the Web App deployment access is set to 'Anyone' and URL ends with /exec.",
            )
          }
          body
        }
      } catch (e: ApiException) {
        throw e
      } catch (e: IOException) {
        throw ApiException("NETWORK_ERROR", e.message ?: "Network connection error.")
      }
    }

  private inline fun <reified T> fromJson(json: String): T {
    val adapter = moshi.adapter(T::class.java)
    return adapter.fromJson(json)
      ?: throw ApiException("PARSE_ERROR", "Failed to parse ${T::class.java.simpleName}.")
  }

  override suspend fun healthCheck(): HealthCheckDto =
    postAction("healthCheck", requiresAuth = false) { fromJson(it) }

  override suspend fun initializeDrive(): Map<String, Any?> =
    postAction("initializeDrive", requiresAuth = false) { json ->
      val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
      moshi.adapter<Map<String, Any?>>(type).fromJson(json).orEmpty()
    }

  override suspend fun guestRegister(
    deviceId: String,
    displayName: String,
    username: String?,
  ): AuthResponseDto {
    val payload =
      JSONObject().apply {
        put("deviceId", deviceId)
        put("displayName", displayName)
        if (!username.isNullOrBlank()) put("username", username)
      }
    return postAction("guestRegister", payload, requiresAuth = false) { fromJson(it) }
  }

  override suspend fun guestLogin(deviceId: String, userId: String?): AuthResponseDto {
    val payload =
      JSONObject().apply {
        put("deviceId", deviceId)
        if (!userId.isNullOrBlank()) put("userId", userId)
      }
    return postAction("guestLogin", payload, requiresAuth = false) { fromJson(it) }
  }

  override suspend fun requestPhoneOtp(
    countryCode: String,
    phoneNumber: String,
    upgradeUserId: String?,
  ): OtpChallengeResponseDto {
    val payload =
      JSONObject().apply {
        put("countryCode", countryCode)
        put("phoneNumber", phoneNumber)
        if (!upgradeUserId.isNullOrBlank()) put("upgradeUserId", upgradeUserId)
      }
    return postAction("requestPhoneOtp", payload, requiresAuth = false) { fromJson(it) }
  }

  override suspend fun verifyPhoneOtp(
    challengeId: String,
    countryCode: String,
    phoneNumber: String,
    otpCode: String,
    displayName: String?,
    deviceId: String?,
  ): AuthResponseDto {
    val payload =
      JSONObject().apply {
        put("challengeId", challengeId)
        put("countryCode", countryCode)
        put("phoneNumber", phoneNumber)
        put("otpCode", otpCode)
        if (!displayName.isNullOrBlank()) put("displayName", displayName)
        if (!deviceId.isNullOrBlank()) put("deviceId", deviceId)
        tokenStorage.getSession()?.accessToken?.let { put("accessToken", it) }
      }
    return postAction("verifyPhoneOtp", payload, requiresAuth = false) { fromJson(it) }
  }

  override suspend fun refreshSession(refreshToken: String): AuthResponseDto {
    val payload = JSONObject().put("refreshToken", refreshToken)
    return postAction("refreshSession", payload, requiresAuth = false, allowRetryOnExpired = false) {
      fromJson(it)
    }
  }

  override suspend fun logout() {
    postAction("logout") { Unit }
  }

  override suspend fun getMyProfile(): UserProfileDto =
    postAction("getMyProfile") { json ->
      val userJson = JSONObject(json).getJSONObject("user").toString()
      fromJson(userJson)
    }

  override suspend fun searchUser(query: String, defaultCountryCode: String): List<UserProfileDto> {
    val payload =
      JSONObject().apply {
        put("query", query)
        put("defaultCountryCode", defaultCountryCode)
      }
    return postAction("searchUser", payload) { json ->
      val arr = JSONObject(json).optJSONArray("users") ?: JSONArray()
      val listType = Types.newParameterizedType(List::class.java, UserProfileDto::class.java)
      moshi.adapter<List<UserProfileDto>>(listType).fromJson(arr.toString()).orEmpty()
    }
  }

  override suspend fun getUserProfile(userId: String): UserProfileDto {
    val payload = JSONObject().put("userId", userId)
    return postAction("getUserProfile", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("user").toString())
    }
  }

  override suspend fun updateProfile(
    displayName: String?,
    bio: String?,
    privacy: PrivacySettingsDto?,
  ): UserProfileDto {
    val payload =
      JSONObject().apply {
        if (displayName != null) put("displayName", displayName)
        if (bio != null) put("bio", bio)
        if (privacy != null) {
          put(
            "privacy",
            JSONObject().apply {
              put("showOnlineStatus", privacy.showOnlineStatus)
              put("allowSearchByPhone", privacy.allowSearchByPhone)
              put("allowGroupInvites", privacy.allowGroupInvites)
            },
          )
        }
      }
    return postAction("updateProfile", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("user").toString())
    }
  }

  override suspend fun updateUsername(username: String): UserProfileDto {
    val payload = JSONObject().put("username", username)
    return postAction("updateUsername", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("user").toString())
    }
  }

  override suspend fun uploadProfilePhoto(
    fileName: String,
    mimeType: String,
    base64Data: String,
  ): String {
    val payload =
      JSONObject().apply {
        put("fileName", fileName)
        put("mimeType", mimeType)
        put("base64Data", base64Data)
      }
    return postAction("uploadProfilePhoto", payload) { json ->
      JSONObject(json).getString("photoMediaId")
    }
  }

  override suspend fun addContact(contactUserId: String, nickname: String?): UserProfileDto {
    val payload =
      JSONObject().apply {
        put("contactUserId", contactUserId)
        if (!nickname.isNullOrBlank()) put("nickname", nickname)
      }
    return postAction("addContact", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("contact").toString())
    }
  }

  override suspend fun removeContact(contactUserId: String) {
    val payload = JSONObject().put("contactUserId", contactUserId)
    postAction("removeContact", payload) { Unit }
  }

  override suspend fun getContacts(): List<UserProfileDto> =
    postAction("getContacts") { json ->
      val arr = JSONObject(json).optJSONArray("contacts") ?: JSONArray()
      val listType = Types.newParameterizedType(List::class.java, UserProfileDto::class.java)
      moshi.adapter<List<UserProfileDto>>(listType).fromJson(arr.toString()).orEmpty()
    }

  override suspend fun createConversation(peerUserId: String): ConversationDto {
    val payload = JSONObject().put("peerUserId", peerUserId)
    return postAction("createConversation", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("conversation").toString())
    }
  }

  override suspend fun getConversations(): List<ConversationDto> =
    postAction("getConversations") { json ->
      val arr = JSONObject(json).optJSONArray("conversations") ?: JSONArray()
      val listType = Types.newParameterizedType(List::class.java, ConversationDto::class.java)
      moshi.adapter<List<ConversationDto>>(listType).fromJson(arr.toString()).orEmpty()
    }

  override suspend fun getConversationInfo(conversationId: String): ConversationDto {
    val payload = JSONObject().put("conversationId", conversationId)
    return postAction("getConversationInfo", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("conversation").toString())
    }
  }

  override suspend fun sendMessage(
    conversationId: String,
    messageType: String,
    content: String,
    idempotencyKey: String,
    mediaId: String?,
    mediaMeta: MediaMetaDto?,
  ): MessageDto {
    val payload =
      JSONObject().apply {
        put("conversationId", conversationId)
        put("messageType", messageType)
        put("content", content)
        put("idempotencyKey", idempotencyKey)
        if (mediaId != null) put("mediaId", mediaId)
        if (mediaMeta != null) {
          put(
            "mediaMeta",
            JSONObject().apply {
              put("fileName", mediaMeta.fileName)
              put("mimeType", mediaMeta.mimeType)
              put("sizeBytes", mediaMeta.sizeBytes)
              put("durationMs", mediaMeta.durationMs)
            },
          )
        }
      }
    return postAction("sendMessage", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("message").toString())
    }
  }

  override suspend fun syncMessages(
    conversationId: String,
    afterSequence: Long,
    limit: Int,
  ): SyncMessagesResponseDto {
    val payload =
      JSONObject().apply {
        put("conversationId", conversationId)
        put("afterSequence", afterSequence)
        put("limit", limit)
      }
    return postAction("syncMessages", payload) { fromJson(it) }
  }

  override suspend fun getMessageHistory(
    conversationId: String,
    beforeSequence: Long?,
    limit: Int,
  ): SyncMessagesResponseDto {
    val payload =
      JSONObject().apply {
        put("conversationId", conversationId)
        if (beforeSequence != null) put("beforeSequence", beforeSequence)
        put("limit", limit)
      }
    return postAction("getMessageHistory", payload) { fromJson(it) }
  }

  override suspend fun markMessageRead(conversationId: String, sequenceNumber: Long) {
    val payload =
      JSONObject().apply {
        put("conversationId", conversationId)
        put("sequenceNumber", sequenceNumber)
      }
    postAction("markMessageRead", payload) { Unit }
  }

  override suspend fun createGroup(
    name: String,
    description: String,
    memberIds: List<String>,
  ): GroupInfoDto {
    val payload =
      JSONObject().apply {
        put("name", name)
        put("description", description)
        put("memberIds", JSONArray(memberIds))
      }
    return postAction("createGroup", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("group").toString())
    }
  }

  override suspend fun getGroupInfo(groupId: String): GroupInfoDto {
    val payload = JSONObject().put("groupId", groupId)
    return postAction("getGroupInfo", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("group").toString())
    }
  }

  override suspend fun addGroupMember(groupId: String, userId: String): GroupInfoDto {
    val payload =
      JSONObject().apply {
        put("groupId", groupId)
        put("userId", userId)
      }
    return postAction("addGroupMember", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("group").toString())
    }
  }

  override suspend fun removeGroupMember(groupId: String, userId: String): GroupInfoDto {
    val payload =
      JSONObject().apply {
        put("groupId", groupId)
        put("userId", userId)
      }
    return postAction("removeGroupMember", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("group").toString())
    }
  }

  override suspend fun updateGroupInfo(
    groupId: String,
    name: String?,
    description: String?,
    photoMediaId: String?,
  ): GroupInfoDto {
    val payload =
      JSONObject().apply {
        put("groupId", groupId)
        if (name != null) put("name", name)
        if (description != null) put("description", description)
        if (photoMediaId != null) put("photoMediaId", photoMediaId)
      }
    return postAction("updateGroupInfo", payload) { json ->
      fromJson(JSONObject(json).getJSONObject("group").toString())
    }
  }

  override suspend fun uploadMediaChunk(
    category: String,
    fileName: String,
    mimeType: String,
    conversationId: String?,
    base64Data: String,
    totalBytes: Long,
    chunkIndex: Int,
    totalChunks: Int,
    uploadSessionId: String?,
  ): MediaUploadResponseDto {
    val payload =
      JSONObject().apply {
        put("category", category)
        put("fileName", fileName)
        put("mimeType", mimeType)
        if (conversationId != null) put("conversationId", conversationId)
        put("base64Data", base64Data)
        put("totalBytes", totalBytes)
        put("chunkIndex", chunkIndex)
        put("totalChunks", totalChunks)
        if (uploadSessionId != null) put("uploadSessionId", uploadSessionId)
      }
    return postAction("uploadMedia", payload) { fromJson(it) }
  }

  override suspend fun getMedia(mediaId: String): MediaDownloadDto {
    val payload = JSONObject().put("mediaId", mediaId)
    return postAction("getMedia", payload) { fromJson(it) }
  }

  override suspend fun deleteMedia(mediaId: String) {
    val payload = JSONObject().put("mediaId", mediaId)
    postAction("deleteMedia", payload) { Unit }
  }
}
