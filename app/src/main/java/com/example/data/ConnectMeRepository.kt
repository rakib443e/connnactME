package com.example.data

import android.net.Uri
import android.util.Base64
import com.example.core.config.AppSettingsStore
import com.example.core.media.MediaHelper
import com.example.core.media.MediaValidationResult
import com.example.core.network.ApiException
import com.example.core.network.ConnectMeApi
import com.example.core.network.ConversationDto
import com.example.core.network.GroupInfoDto
import com.example.core.network.HealthCheckDto
import com.example.core.network.MediaMetaDto
import com.example.core.network.MessageDto
import com.example.core.network.NetworkMonitor
import com.example.core.network.OtpChallengeResponseDto
import com.example.core.network.PrivacySettingsDto
import com.example.core.network.UserProfileDto
import com.example.core.security.SecureTokenStorage
import com.example.core.security.StoredSession
import com.example.core.storage.CachedContactEntity
import com.example.core.storage.CachedConversationEntity
import com.example.core.storage.CachedMediaEntity
import com.example.core.storage.CachedMessageEntity
import com.example.core.storage.ConnectMeDatabase
import com.example.core.storage.SyncCursorEntity
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ConnectMeRepository(
  private val api: ConnectMeApi,
  private val db: ConnectMeDatabase,
  private val tokenStorage: SecureTokenStorage,
  private val settingsStore: AppSettingsStore,
  private val networkMonitor: NetworkMonitor,
  private val mediaHelper: MediaHelper,
) {
  private val _currentUser = MutableStateFlow<UserProfileDto?>(null)
  val currentUser: StateFlow<UserProfileDto?> = _currentUser.asStateFlow()

  private val _uploadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
  val uploadProgress: StateFlow<Map<String, Float>> = _uploadProgress.asStateFlow()

  private val queueFlushMutex = Mutex()

  val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

  fun hasStoredSession(): Boolean = tokenStorage.getSession() != null

  fun getStoredUserId(): String? = tokenStorage.getSession()?.userId

  fun observeConversations(): Flow<List<CachedConversationEntity>> =
    db.conversationDao().observeConversations()

  fun observeMessages(conversationId: String): Flow<List<CachedMessageEntity>> =
    db.messageDao().observeMessages(conversationId)

  fun observeConversationMedia(conversationId: String): Flow<List<CachedMessageEntity>> =
    db.messageDao().observeConversationMedia(conversationId)

  fun observeContacts(): Flow<List<CachedContactEntity>> = db.contactDao().observeContacts()

  suspend fun checkBackendHealth(): Result<HealthCheckDto> = runCatching { api.healthCheck() }

  suspend fun initializeDrive(): Result<Map<String, Any?>> = runCatching { api.initializeDrive() }

  suspend fun restoreSession(): Result<UserProfileDto?> = runCatching {
    val stored = tokenStorage.getSession() ?: return@runCatching null
    if (!networkMonitor.isOnline.value) {
      // Offline mode with cached session
      val fallback =
        UserProfileDto(
          userId = stored.userId,
          username = stored.userId.lowercase(),
          displayName = "ConnectMe User",
        )
      _currentUser.value = fallback
      return@runCatching fallback
    }
    try {
      val profile = api.getMyProfile()
      _currentUser.value = profile
      profile
    } catch (e: ApiException) {
      if (e.code == "SESSION_EXPIRED" || e.code == "UNAUTHORIZED") {
        val refreshed = api.refreshSession(stored.refreshToken)
        tokenStorage.saveSession(
          StoredSession(
            accessToken = refreshed.session.accessToken,
            refreshToken = refreshed.session.refreshToken,
            expiresAtMs = refreshed.session.expiresAtMs,
            userId = refreshed.user.userId,
          )
        )
        _currentUser.value = refreshed.user
        refreshed.user
      } else {
        throw e
      }
    }
  }

  suspend fun guestRegister(displayName: String, username: String? = null): Result<UserProfileDto> =
    runCatching {
      val deviceId = tokenStorage.getOrCreateDeviceId()
      val auth = api.guestRegister(deviceId = deviceId, displayName = displayName, username = username)
      tokenStorage.saveSession(
        StoredSession(
          accessToken = auth.session.accessToken,
          refreshToken = auth.session.refreshToken,
          expiresAtMs = auth.session.expiresAtMs,
          userId = auth.user.userId,
        )
      )
      _currentUser.value = auth.user
      auth.user
    }

  suspend fun guestLogin(): Result<UserProfileDto> = runCatching {
    val deviceId = tokenStorage.getOrCreateDeviceId()
    val auth = api.guestLogin(deviceId = deviceId, userId = tokenStorage.getSession()?.userId)
    tokenStorage.saveSession(
      StoredSession(
        accessToken = auth.session.accessToken,
        refreshToken = auth.session.refreshToken,
        expiresAtMs = auth.session.expiresAtMs,
        userId = auth.user.userId,
      )
    )
    _currentUser.value = auth.user
    auth.user
  }

  suspend fun requestPhoneOtp(
    countryCode: String,
    phoneNumber: String,
    isUpgrade: Boolean = false,
  ): Result<OtpChallengeResponseDto> = runCatching {
    api.requestPhoneOtp(
      countryCode = countryCode,
      phoneNumber = phoneNumber,
      upgradeUserId = if (isUpgrade) _currentUser.value?.userId else null,
    )
  }

  suspend fun verifyPhoneOtp(
    challengeId: String,
    countryCode: String,
    phoneNumber: String,
    otpCode: String,
    displayName: String? = null,
  ): Result<UserProfileDto> = runCatching {
    val deviceId = tokenStorage.getOrCreateDeviceId()
    val auth =
      api.verifyPhoneOtp(
        challengeId = challengeId,
        countryCode = countryCode,
        phoneNumber = phoneNumber,
        otpCode = otpCode,
        displayName = displayName,
        deviceId = deviceId,
      )
    tokenStorage.saveSession(
      StoredSession(
        accessToken = auth.session.accessToken,
        refreshToken = auth.session.refreshToken,
        expiresAtMs = auth.session.expiresAtMs,
        userId = auth.user.userId,
      )
    )
    _currentUser.value = auth.user
    auth.user
  }

  suspend fun logout(): Result<Unit> = runCatching {
    runCatching { api.logout() }
    tokenStorage.clearSession()
    _currentUser.value = null
    withContext(Dispatchers.IO) {
      db.conversationDao().clearAll()
      db.messageDao().clearAll()
      db.syncCursorDao().clearAll()
      db.contactDao().clearAll()
      db.mediaCacheDao().clearAll()
    }
  }

  suspend fun refreshProfile(): Result<UserProfileDto> = runCatching {
    val profile = api.getMyProfile()
    _currentUser.value = profile
    profile
  }

  suspend fun updateProfile(
    displayName: String? = null,
    bio: String? = null,
    privacy: PrivacySettingsDto? = null,
  ): Result<UserProfileDto> = runCatching {
    val updated = api.updateProfile(displayName, bio, privacy)
    _currentUser.value = updated
    updated
  }

  suspend fun updateUsername(username: String): Result<UserProfileDto> = runCatching {
    val updated = api.updateUsername(username)
    _currentUser.value = updated
    updated
  }

  suspend fun uploadProfilePhoto(uri: Uri): Result<UserProfileDto> = runCatching {
    when (val res = mediaHelper.validateAndStageUri(uri, expectedCategory = "image")) {
      is MediaValidationResult.Invalid -> throw ApiException("INVALID_MEDIA", res.reason)
      is MediaValidationResult.Valid -> {
        val bytes = res.media.localFile.readBytes()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        api.uploadProfilePhoto(res.media.fileName, res.media.mimeType, b64)
        val refreshed = api.getMyProfile()
        _currentUser.value = refreshed
        refreshed
      }
    }
  }

  suspend fun searchUsers(query: String): Result<List<UserProfileDto>> = runCatching {
    api.searchUser(query)
  }

  suspend fun getUserProfile(userId: String): Result<UserProfileDto> = runCatching {
    api.getUserProfile(userId)
  }

  suspend fun syncContacts(): Result<List<UserProfileDto>> = runCatching {
    val contacts = api.getContacts()
    db.contactDao().upsertAll(contacts.map { it.toEntity() })
    contacts
  }

  suspend fun addContact(userId: String): Result<UserProfileDto> = runCatching {
    val added = api.addContact(userId)
    db.contactDao().upsertAll(listOf(added.toEntity()))
    added
  }

  suspend fun removeContact(userId: String): Result<Unit> = runCatching {
    api.removeContact(userId)
    db.contactDao().deleteById(userId)
  }

  suspend fun startDirectConversation(peerUserId: String): Result<ConversationDto> = runCatching {
    val conv = api.createConversation(peerUserId)
    db.conversationDao().upsert(conv.toEntity())
    conv
  }

  suspend fun syncConversations(): Result<Unit> = runCatching {
    val remoteList = api.getConversations()
    db.conversationDao().upsertAll(remoteList.map { it.toEntity() })
  }

  suspend fun getConversation(conversationId: String): Result<ConversationDto> = runCatching {
    val conv = api.getConversationInfo(conversationId)
    db.conversationDao().upsert(conv.toEntity())
    conv
  }

  suspend fun syncNewMessages(conversationId: String): Result<Unit> = runCatching {
    val cursor = db.syncCursorDao().getCursor(conversationId)?.lastSequence ?: 0L
    var afterSeq = cursor
    var keepGoing = true
    var latestSeen = cursor

    while (keepGoing) {
      val resp = api.syncMessages(conversationId, afterSequence = afterSeq, limit = 50)
      if (resp.messages.isNotEmpty()) {
        reconcileServerMessages(resp.messages)
        afterSeq = resp.lastSequence
        latestSeen = maxOf(latestSeen, resp.lastSequence)
        db.syncCursorDao()
          .upsert(SyncCursorEntity(conversationId, latestSeen, System.currentTimeMillis()))
      }
      keepGoing = resp.hasMore && resp.messages.isNotEmpty()
    }

    if (latestSeen > 0L) {
      runCatching { api.markMessageRead(conversationId, latestSeen) }
    }
  }

  suspend fun loadOlderMessages(conversationId: String): Result<Boolean> = runCatching {
    val minSeq = db.messageDao().getMinSequence(conversationId)
    val resp = api.getMessageHistory(conversationId, beforeSequence = minSeq, limit = 30)
    if (resp.messages.isNotEmpty()) {
      reconcileServerMessages(resp.messages)
    }
    resp.hasMore
  }

  /**
   * Sends a text message with offline queuing and idempotency key.
   * Never marks message as SERVER_CONFIRMED before backend confirmation.
   */
  suspend fun sendTextMessage(conversationId: String, text: String): Result<Unit> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Empty message"))
    val senderId =
      _currentUser.value?.userId
        ?: tokenStorage.getSession()?.userId
        ?: return Result.failure(ApiException("UNAUTHORIZED", "Please sign in first."))

    val localId = "local_${UUID.randomUUID()}"
    val idempotencyKey = "idem_${UUID.randomUUID()}"
    val entity =
      CachedMessageEntity(
        localId = localId,
        serverMessageId = null,
        conversationId = conversationId,
        senderId = senderId,
        receiverId = "",
        messageType = "text",
        content = trimmed,
        mediaId = null,
        mediaFileName = null,
        mediaMimeType = null,
        mediaSizeBytes = 0L,
        mediaDurationMs = 0L,
        localFileUri = null,
        sequenceNumber = 0L,
        idempotencyKey = idempotencyKey,
        timestamp = Instant.now().toString(),
        localStatus = "QUEUED",
      )
    db.messageDao().upsert(entity)
    return deliverQueuedMessage(localId)
  }

  suspend fun sendMediaUriMessage(
    conversationId: String,
    uri: Uri,
    caption: String = "",
    expectedCategory: String? = null,
  ): Result<Unit> {
    val validated =
      when (val res = mediaHelper.validateAndStageUri(uri, expectedCategory)) {
        is MediaValidationResult.Invalid -> return Result.failure(ApiException("INVALID_MEDIA", res.reason))
        is MediaValidationResult.Valid -> res.media
      }
    return sendValidatedLocalMedia(conversationId, validated.localFile, validated.category, validated.fileName, validated.mimeType, validated.sizeBytes, 0L, caption)
  }

  suspend fun sendRecordedVoiceMessage(
    conversationId: String,
    file: File,
    durationMs: Long,
  ): Result<Unit> {
    val validated =
      when (val res = mediaHelper.validateLocalFile(file, "voice", "audio/mp4")) {
        is MediaValidationResult.Invalid -> return Result.failure(ApiException("INVALID_MEDIA", res.reason))
        is MediaValidationResult.Valid -> res.media
      }
    return sendValidatedLocalMedia(conversationId, validated.localFile, "voice", validated.fileName, validated.mimeType, validated.sizeBytes, durationMs, "Voice message")
  }

  private suspend fun sendValidatedLocalMedia(
    conversationId: String,
    localFile: File,
    category: String,
    fileName: String,
    mimeType: String,
    sizeBytes: Long,
    durationMs: Long,
    caption: String,
  ): Result<Unit> {
    val senderId =
      _currentUser.value?.userId
        ?: tokenStorage.getSession()?.userId
        ?: return Result.failure(ApiException("UNAUTHORIZED", "Please sign in first."))

    val localId = "local_${UUID.randomUUID()}"
    val idempotencyKey = "idem_${UUID.randomUUID()}"
    val entity =
      CachedMessageEntity(
        localId = localId,
        serverMessageId = null,
        conversationId = conversationId,
        senderId = senderId,
        receiverId = "",
        messageType = category,
        content = caption,
        mediaId = null,
        mediaFileName = fileName,
        mediaMimeType = mimeType,
        mediaSizeBytes = sizeBytes,
        mediaDurationMs = durationMs,
        localFileUri = localFile.absolutePath,
        sequenceNumber = 0L,
        idempotencyKey = idempotencyKey,
        timestamp = Instant.now().toString(),
        localStatus = "QUEUED",
      )
    db.messageDao().upsert(entity)
    return deliverQueuedMessage(localId)
  }

  suspend fun retryMessage(localId: String): Result<Unit> = deliverQueuedMessage(localId)

  suspend fun flushPendingMessages() {
    if (!networkMonitor.isOnline.value) return
    queueFlushMutex.withLock {
      val pending = db.messageDao().getPendingMessages()
      for (msg in pending) {
        if (!networkMonitor.isOnline.value) break
        deliverQueuedMessage(msg.localId)
      }
    }
  }

  private suspend fun deliverQueuedMessage(localId: String): Result<Unit> {
    val queued = db.messageDao().getByLocalId(localId) ?: return Result.success(Unit)
    if (queued.localStatus == "SERVER_CONFIRMED") return Result.success(Unit)

    if (!networkMonitor.isOnline.value) {
      db.messageDao().upsert(queued.copy(localStatus = "QUEUED", errorMessage = "Waiting for network"))
      return Result.failure(ApiException("OFFLINE", "No internet connection. Queued locally."))
    }

    db.messageDao().upsert(queued.copy(localStatus = "SENDING", errorMessage = null))

    return runCatching {
      var resolvedMediaId = queued.mediaId
      if (queued.messageType != "text" && queued.messageType != "system" && resolvedMediaId == null) {
        val path = queued.localFileUri ?: throw ApiException("MISSING_FILE", "Local media file missing.")
        val file = File(path)
        if (!file.exists()) throw ApiException("MISSING_FILE", "Local media file no longer exists.")

        val chunks = mediaHelper.readBase64Chunks(file)
        if (chunks.isEmpty()) throw ApiException("EMPTY_FILE", "Media file is empty.")

        var uploadSessionId: String? = null
        var uploadedRecordId: String? = null
        for ((idx, chunkB64) in chunks.withIndex()) {
          _uploadProgress.value =
            _uploadProgress.value + (localId to (idx.toFloat() / chunks.size.toFloat()))
          val resp =
            api.uploadMediaChunk(
              category = queued.messageType,
              fileName = queued.mediaFileName ?: file.name,
              mimeType = queued.mediaMimeType ?: "application/octet-stream",
              conversationId = queued.conversationId,
              base64Data = chunkB64,
              totalBytes = queued.mediaSizeBytes,
              chunkIndex = idx,
              totalChunks = chunks.size,
              uploadSessionId = uploadSessionId,
            )
          uploadSessionId = resp.uploadSessionId ?: uploadSessionId
          if (resp.completed && resp.media != null) {
            uploadedRecordId = resp.media.mediaId
          }
        }
        _uploadProgress.value = _uploadProgress.value - localId
        resolvedMediaId =
          uploadedRecordId ?: throw ApiException("UPLOAD_FAILED", "Backend did not confirm media upload.")

        // Cache media association locally
        db.mediaCacheDao()
          .upsert(
            CachedMediaEntity(
              mediaId = resolvedMediaId,
              localPath = file.absolutePath,
              fileName = queued.mediaFileName ?: file.name,
              mimeType = queued.mediaMimeType ?: "",
              category = queued.messageType,
              sizeBytes = queued.mediaSizeBytes,
              cachedAtMs = System.currentTimeMillis(),
            )
          )
      }

      val mediaMeta =
        if (resolvedMediaId != null) {
          MediaMetaDto(
            fileName = queued.mediaFileName ?: "",
            mimeType = queued.mediaMimeType ?: "",
            sizeBytes = queued.mediaSizeBytes,
            durationMs = queued.mediaDurationMs,
          )
        } else null

      val confirmed =
        api.sendMessage(
          conversationId = queued.conversationId,
          messageType = queued.messageType,
          content = queued.content,
          idempotencyKey = queued.idempotencyKey,
          mediaId = resolvedMediaId,
          mediaMeta = mediaMeta,
        )

      db.messageDao().deleteByLocalId(localId)
      db.messageDao().upsert(confirmed.toEntity(localFileUri = queued.localFileUri))
      val prevCursor = db.syncCursorDao().getCursor(queued.conversationId)?.lastSequence ?: 0L
      if (confirmed.sequenceNumber > prevCursor) {
        db.syncCursorDao()
          .upsert(SyncCursorEntity(queued.conversationId, confirmed.sequenceNumber, System.currentTimeMillis()))
      }
      Unit
    }.onFailure { err ->
      _uploadProgress.value = _uploadProgress.value - localId
      val status = if (!networkMonitor.isOnline.value) "QUEUED" else "FAILED"
      db.messageDao().upsert(queued.copy(localStatus = status, errorMessage = err.message))
    }
  }

  private suspend fun reconcileServerMessages(serverMessages: List<MessageDto>) {
    for (m in serverMessages) {
      val existingByIdem = db.messageDao().getByIdempotencyKey(m.idempotencyKey)
      if (existingByIdem != null && existingByIdem.localId != m.messageId) {
        db.messageDao().deleteByLocalId(existingByIdem.localId)
      }
      db.messageDao().upsert(m.toEntity(localFileUri = existingByIdem?.localFileUri))
    }
  }

  suspend fun createGroup(
    name: String,
    description: String,
    memberIds: List<String>,
  ): Result<GroupInfoDto> = runCatching {
    val group = api.createGroup(name, description, memberIds)
    syncConversations()
    group
  }

  suspend fun getGroupInfo(groupId: String): Result<GroupInfoDto> = runCatching {
    api.getGroupInfo(groupId)
  }

  suspend fun addGroupMember(groupId: String, userId: String): Result<GroupInfoDto> = runCatching {
    api.addGroupMember(groupId, userId)
  }

  suspend fun removeGroupMember(groupId: String, userId: String): Result<GroupInfoDto> =
    runCatching {
      val updated = api.removeGroupMember(groupId, userId)
      if (userId == _currentUser.value?.userId) {
        db.conversationDao().deleteById(groupId)
      }
      updated
    }

  suspend fun updateGroupInfo(
    groupId: String,
    name: String? = null,
    description: String? = null,
  ): Result<GroupInfoDto> = runCatching {
    val updated = api.updateGroupInfo(groupId, name, description)
    syncConversations()
    updated
  }

  suspend fun resolveMediaFile(mediaId: String): Result<File> = runCatching {
    val cached = db.mediaCacheDao().getMedia(mediaId)
    if (cached != null) {
      val file = File(cached.localPath)
      if (file.exists() && file.length() > 0) return@runCatching file
    }
    val downloaded = api.getMedia(mediaId)
    val saved =
      mediaHelper.saveBase64MediaToCache(
        mediaId = downloaded.media.mediaId,
        fileName = downloaded.media.fileName,
        base64Data = downloaded.base64Data,
      )
    db.mediaCacheDao()
      .upsert(
        CachedMediaEntity(
          mediaId = downloaded.media.mediaId,
          localPath = saved.absolutePath,
          fileName = downloaded.media.fileName,
          mimeType = downloaded.media.mimeType,
          category = downloaded.media.category,
          sizeBytes = downloaded.media.sizeBytes,
          cachedAtMs = System.currentTimeMillis(),
        )
      )
    saved
  }
}

private fun ConversationDto.toEntity() =
  CachedConversationEntity(
    conversationId = conversationId,
    type = type,
    title = title,
    subtitle = subtitle,
    photoMediaId = photoMediaId,
    peerUserId = peerUser?.userId,
    peerUsername = peerUser?.username,
    lastSequence = lastSequence,
    unreadCount = unreadCount,
    lastMessagePreview = lastMessagePreview,
    lastMessageType = lastMessageType,
    lastMessageAt = lastMessageAt,
    updatedAt = updatedAt,
  )

private fun MessageDto.toEntity(localFileUri: String? = null) =
  CachedMessageEntity(
    localId = messageId,
    serverMessageId = messageId,
    conversationId = conversationId,
    senderId = senderId,
    receiverId = receiverId,
    messageType = messageType,
    content = content,
    mediaId = mediaId,
    mediaFileName = mediaMeta?.fileName,
    mediaMimeType = mediaMeta?.mimeType,
    mediaSizeBytes = mediaMeta?.sizeBytes ?: 0L,
    mediaDurationMs = mediaMeta?.durationMs ?: 0L,
    localFileUri = localFileUri,
    sequenceNumber = sequenceNumber,
    idempotencyKey = idempotencyKey,
    timestamp = timestamp,
    localStatus = "SERVER_CONFIRMED",
    errorMessage = null,
  )

private fun UserProfileDto.toEntity() =
  CachedContactEntity(
    userId = userId,
    username = username,
    displayName = displayName,
    bio = bio,
    photoMediaId = photoMediaId,
    accountType = accountType,
    createdAt = createdAt,
  )
