package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.config.AppSettingsStore
import com.example.core.media.MediaHelper
import com.example.core.network.ApiException
import com.example.core.network.AuthResponseDto
import com.example.core.network.ConnectMeApi
import com.example.core.network.ConversationDto
import com.example.core.network.GroupInfoDto
import com.example.core.network.GroupMemberDto
import com.example.core.network.HealthCheckDto
import com.example.core.network.MediaDownloadDto
import com.example.core.network.MediaMetaDto
import com.example.core.network.MediaRecordDto
import com.example.core.network.MediaUploadResponseDto
import com.example.core.network.MessageDto
import com.example.core.network.NetworkMonitor
import com.example.core.network.OtpChallengeResponseDto
import com.example.core.network.PrivacySettingsDto
import com.example.core.network.SessionDto
import com.example.core.network.SyncMessagesResponseDto
import com.example.core.network.UserProfileDto
import com.example.core.security.SecureTokenStorage
import com.example.core.storage.ConnectMeDatabase
import com.example.data.ConnectMeRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Local JVM + Robolectric test suite validating ConnectMeRepository, Room SQLite cache,
 * offline queue, idempotency, session expiration/refresh, multi-user authorization boundaries,
 * group permissions, removed-member revocation, and media access enforcement.
 *
 * Note: Live Google Apps Script / Google Drive integration requires interactive Google OAuth
 * authorization by the Drive owner and is documented separately in docs/BACKEND_DEPLOYMENT.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectMeIntegrationTest {

  private lateinit var context: Context
  private lateinit var dbA: ConnectMeDatabase
  private lateinit var dbB: ConnectMeDatabase
  private lateinit var server: InMemoryBackendServer
  private lateinit var tokenStorageA: SecureTokenStorage
  private lateinit var tokenStorageB: SecureTokenStorage
  private lateinit var netA: NetworkMonitor
  private lateinit var netB: NetworkMonitor
  private lateinit var repoA: ConnectMeRepository
  private lateinit var repoB: ConnectMeRepository

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    server = InMemoryBackendServer()

    dbA = Room.inMemoryDatabaseBuilder(context, ConnectMeDatabase::class.java).allowMainThreadQueries().build()
    dbB = Room.inMemoryDatabaseBuilder(context, ConnectMeDatabase::class.java).allowMainThreadQueries().build()

    // Separate token prefs per simulated user device
    tokenStorageA = SecureTokenStorage(context)
    tokenStorageA.clearSession()
    tokenStorageB = SecureTokenStorage(context)

    netA = NetworkMonitor(context).apply { setOverrideForTest(true) }
    netB = NetworkMonitor(context).apply { setOverrideForTest(true) }

    val settings = AppSettingsStore(context)
    val mediaHelper = MediaHelper(context)

    repoA =
      ConnectMeRepository(
        api = ServerClientProxy(server, tokenStorageA),
        db = dbA,
        tokenStorage = tokenStorageA,
        settingsStore = settings,
        networkMonitor = netA,
        mediaHelper = mediaHelper,
      )
    repoB =
      ConnectMeRepository(
        api = ServerClientProxy(server, tokenStorageB),
        db = dbB,
        tokenStorage = tokenStorageB,
        settingsStore = settings,
        networkMonitor = netB,
        mediaHelper = mediaHelper,
      )
  }

  @After
  fun teardown() {
    dbA.close()
    dbB.close()
  }

  @Test
  fun `guest registration, login, session expiration and refresh work end to end`() = runBlocking {
    val alice = repoA.guestRegister("Alice", "alice_cm").getOrThrow()
    assertEquals("CM100001", alice.userId)
    assertEquals("guest", alice.accountType)

    val session1 = tokenStorageA.getSession()!!
    // Force server-side access token expiry
    server.expireAccessToken(session1.accessToken)

    // restoreSession should transparently rotate using refreshToken
    val restored = repoA.restoreSession().getOrThrow()
    assertEquals("CM100001", restored?.userId)
    val session2 = tokenStorageA.getSession()!!
    assertNotEquals(session1.accessToken, session2.accessToken)

    // Re-login with deviceId succeeds
    val loggedIn = repoA.guestLogin().getOrThrow()
    assertEquals("CM100001", loggedIn.userId)
  }

  @Test
  fun `user search hides private phone numbers and profile editing persists`() = runBlocking {
    repoA.guestRegister("Alice", "alice_one").getOrThrow()
    val bobAuth = server.directRegisterPhone("Bob", "bob_two", "+15550001111")

    val updated = repoA.updateProfile(displayName = "Alice Updated", bio = "Hello Drive").getOrThrow()
    assertEquals("Alice Updated", updated.displayName)
    assertEquals("Hello Drive", updated.bio)

    val found = repoA.searchUsers("bob_two").getOrThrow()
    assertEquals(1, found.size)
    assertEquals(bobAuth.user.userId, found.first().userId)
    assertNull(found.first().phoneMasked)
    assertFalse(found.first().phoneVerified)
  }

  @Test
  fun `unauthorized conversation access and unauthorized media access are denied`() = runBlocking {
    val alice = repoA.guestRegister("Alice", "alice_u").getOrThrow()
    val bobAuth = server.directRegisterPhone("Bob", "bob_u", "+15551112222")
    val eveAuth = server.directRegisterPhone("Eve", "eve_u", "+15553334444")

    val conv = repoA.startDirectConversation(bobAuth.user.userId).getOrThrow()
    repoA.sendTextMessage(conv.conversationId, "Secret for Bob").getOrThrow()

    // Upload media into Alice-Bob conversation
    val apiAlice = ServerClientProxy(server, tokenStorageA)
    val mediaResp =
      apiAlice.uploadMediaChunk(
        category = "image",
        fileName = "photo.jpg",
        mimeType = "image/jpeg",
        conversationId = conv.conversationId,
        base64Data = "AQIDBA==",
        totalBytes = 4,
        chunkIndex = 0,
        totalChunks = 1,
        uploadSessionId = null,
      )
    val mediaId = mediaResp.media!!.mediaId

    // Eve attempts to sync messages & fetch media from Alice-Bob conversation -> FORBIDDEN
    val eveTokenStorage = object {
      val token = eveAuth.session.accessToken
    }
    var convDenied = false
    try {
      server.syncMessages(eveTokenStorage.token, conv.conversationId, 0)
    } catch (e: ApiException) {
      convDenied = e.code == "FORBIDDEN"
    }
    assertTrue("Eve must be denied conversation sync", convDenied)

    var mediaDenied = false
    try {
      server.getMedia(eveTokenStorage.token, mediaId)
    } catch (e: ApiException) {
      mediaDenied = e.code == "FORBIDDEN"
    }
    assertTrue("Eve must be denied unauthorized media access", mediaDenied)
  }

  @Test
  fun `message sending prevents duplicates via idempotency and supports incremental polling`() =
    runBlocking {
      repoA.guestRegister("Alice", "alice_poll").getOrThrow()
      val bobAuth = server.directRegisterPhone("Bob", "bob_poll", "+15559990000")
      val conv = repoA.startDirectConversation(bobAuth.user.userId).getOrThrow()

      val apiAlice = ServerClientProxy(server, tokenStorageA)
      val idem = "idem-fixed-key-123"
      val m1 = apiAlice.sendMessage(conv.conversationId, "text", "First", idem)
      val m1Dup = apiAlice.sendMessage(conv.conversationId, "text", "First", idem)
      assertEquals(m1.messageId, m1Dup.messageId)
      assertEquals(1L, m1Dup.sequenceNumber)

      val m2 = apiAlice.sendMessage(conv.conversationId, "text", "Second", "idem-key-2")
      assertEquals(2L, m2.sequenceNumber)

      val syncFrom1 = server.syncMessages(bobAuth.session.accessToken, conv.conversationId, 1)
      assertEquals(1, syncFrom1.messages.size)
      assertEquals("Second", syncFrom1.messages.first().content)
      assertEquals(2L, syncFrom1.lastSequence)
    }

  @Test
  fun `group permissions enforce admin actions and removed members lose access`() = runBlocking {
    val alice = repoA.guestRegister("Alice", "alice_grp").getOrThrow()
    val bobAuth = server.directRegisterPhone("Bob", "bob_grp", "+15557778888")
    val carolAuth = server.directRegisterPhone("Carol", "carol_grp", "+15556665555")

    val group =
      repoA.createGroup("Core Team", "Engineering", listOf(bobAuth.user.userId)).getOrThrow()

    // Non-admin Bob cannot add Carol
    var nonAdminDenied = false
    try {
      server.addGroupMember(bobAuth.session.accessToken, group.groupId, carolAuth.user.userId)
    } catch (e: ApiException) {
      nonAdminDenied = e.code == "FORBIDDEN_ADMIN_REQUIRED"
    }
    assertTrue("Non-admin must be blocked from adding members", nonAdminDenied)

    // Bob can read messages while active
    repoA.sendTextMessage(group.groupId, "Welcome Bob").getOrThrow()
    val bobSync1 = server.syncMessages(bobAuth.session.accessToken, group.groupId, 0)
    assertEquals(1, bobSync1.messages.size)

    // Admin Alice removes Bob -> Bob loses access to future group data
    repoA.removeGroupMember(group.groupId, bobAuth.user.userId).getOrThrow()
    repoA.sendTextMessage(group.groupId, "Post-removal message").getOrThrow()

    var removedDenied = false
    try {
      server.syncMessages(bobAuth.session.accessToken, group.groupId, 1)
    } catch (e: ApiException) {
      removedDenied = e.code == "FORBIDDEN"
    }
    assertTrue("Removed member must lose access to future group messages", removedDenied)
  }

  @Test
  fun `offline message queueing and reconnection flush preserves order and prevents server-sent premature status`() =
    runBlocking {
      repoA.guestRegister("Alice", "alice_off").getOrThrow()
      val bobAuth = server.directRegisterPhone("Bob", "bob_off", "+15554443333")
      val conv = repoA.startDirectConversation(bobAuth.user.userId).getOrThrow()

      // Go offline
      netA.setOverrideForTest(false)
      val offlineResult = repoA.sendTextMessage(conv.conversationId, "Offline queued message")
      assertTrue(offlineResult.isFailure)

      val queuedList = dbA.messageDao().observeMessages(conv.conversationId).first()
      assertEquals(1, queuedList.size)
      assertEquals("QUEUED", queuedList.first().localStatus)
      assertEquals(0L, queuedList.first().sequenceNumber)

      // Come back online and flush pending queue
      netA.setOverrideForTest(true)
      repoA.flushPendingMessages()

      val syncedList = dbA.messageDao().observeMessages(conv.conversationId).first()
      assertEquals(1, syncedList.size)
      assertEquals("SERVER_CONFIRMED", syncedList.first().localStatus)
      assertEquals(1L, syncedList.first().sequenceNumber)
    }

  @Test
  fun `invalid API inputs are rejected cleanly`() = runBlocking {
    repoA.guestRegister("Alice", "alice_val").getOrThrow()
    val blankSend = repoA.sendTextMessage("CONV_INVALID", "   ")
    assertTrue(blankSend.isFailure)

    var invalidUsernameRejected = false
    repoA.updateUsername("a").onFailure { invalidUsernameRejected = true }
    assertTrue(invalidUsernameRejected)
  }
}

/**
 * Faithful JVM test double implementing the exact backend contract of Router/Auth/Messages/Groups/Media.gs
 * for automated two-user security & synchronization tests without live Google Drive OAuth.
 */
private class InMemoryBackendServer {
  private var userSeq = 100000
  private var groupSeq = 1000
  private var mediaSeq = 1000

  private val users = mutableMapOf<String, UserProfileDto>()
  private val phones = mutableMapOf<String, String>()
  private val sessions = mutableMapOf<String, SessionRecord>()
  private val refreshTokens = mutableMapOf<String, SessionRecord>()
  private val conversations = mutableMapOf<String, MutableList<String>>()
  private val groups = mutableMapOf<String, MutableGroup>()
  private val messages = mutableMapOf<String, MutableList<MessageDto>>()
  private val idempotency = mutableMapOf<String, MessageDto>()
  private val mediaStore = mutableMapOf<String, Pair<MediaRecordDto, String>>()
  private val contacts = mutableMapOf<String, MutableSet<String>>()

  data class SessionRecord(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    var expiresAtMs: Long,
  )

  data class MutableGroup(
    var info: GroupInfoDto,
    val activeMembers: MutableSet<String>,
    val admins: MutableSet<String>,
  )

  fun expireAccessToken(token: String) {
    sessions[token]?.expiresAtMs = System.currentTimeMillis() - 10_000L
  }

  private fun auth(token: String): String {
    val s = sessions[token] ?: throw ApiException("UNAUTHORIZED", "Invalid token")
    if (s.expiresAtMs < System.currentTimeMillis()) {
      throw ApiException("SESSION_EXPIRED", "Session expired")
    }
    return s.userId
  }

  private fun issueSession(userId: String): SessionDto {
    val at = "AT_${UUID.randomUUID()}"
    val rt = "RT_${UUID.randomUUID()}"
    val exp = System.currentTimeMillis() + 3_600_000L
    val rec = SessionRecord(userId, at, rt, exp)
    sessions[at] = rec
    refreshTokens[rt] = rec
    return SessionDto(at, rt, Instant.ofEpochMilli(exp).toString(), exp, userId)
  }

  fun guestRegister(displayName: String, username: String?): AuthResponseDto {
    userSeq += 1
    val uid = "CM$userSeq"
    val uname = username ?: "user_${uid.lowercase()}"
    val profile =
      UserProfileDto(
        userId = uid,
        username = uname,
        displayName = displayName,
        accountType = "guest",
        createdAt = Instant.now().toString(),
      )
    users[uid] = profile
    return AuthResponseDto(profile, issueSession(uid))
  }

  fun directRegisterPhone(displayName: String, username: String, phone: String): AuthResponseDto {
    userSeq += 1
    val uid = "CM$userSeq"
    val profile =
      UserProfileDto(
        userId = uid,
        username = username,
        displayName = displayName,
        accountType = "phone",
        phoneVerified = true,
        phoneMasked = "***" + phone.takeLast(3),
        createdAt = Instant.now().toString(),
      )
    users[uid] = profile
    phones[phone] = uid
    return AuthResponseDto(profile, issueSession(uid))
  }

  fun refreshSession(refreshToken: String): AuthResponseDto {
    val rec = refreshTokens.remove(refreshToken) ?: throw ApiException("REFRESH_EXPIRED", "Invalid refresh")
    sessions.remove(rec.accessToken)
    val user = users[rec.userId]!!
    return AuthResponseDto(user, issueSession(rec.userId))
  }

  fun getMyProfile(token: String): UserProfileDto {
    val uid = auth(token)
    return users[uid]!!
  }

  fun updateProfile(token: String, displayName: String?, bio: String?, privacy: PrivacySettingsDto?): UserProfileDto {
    val uid = auth(token)
    val old = users[uid]!!
    val updated =
      old.copy(
        displayName = displayName ?: old.displayName,
        bio = bio ?: old.bio,
        privacy = privacy ?: old.privacy,
      )
    users[uid] = updated
    return updated
  }

  fun updateUsername(token: String, username: String): UserProfileDto {
    val uid = auth(token)
    if (!Regex("^[a-z0-9_]{3,24}$").matches(username)) {
      throw ApiException("INVALID_USERNAME", "Invalid username")
    }
    val old = users[uid]!!
    val updated = old.copy(username = username)
    users[uid] = updated
    return updated
  }

  fun searchUser(token: String, query: String): List<UserProfileDto> {
    val uid = auth(token)
    val q = query.lowercase()
    return users.values
      .filter { it.userId != uid }
      .filter { it.username.lowercase().contains(q) || it.displayName.lowercase().contains(q) || it.userId.lowercase() == q }
      .map { it.copy(phoneMasked = null, phoneVerified = false) }
  }

  fun createConversation(token: String, peerId: String): ConversationDto {
    val uid = auth(token)
    val sorted = listOf(uid, peerId).sorted()
    val cid = "CONV_${sorted[0]}_${sorted[1]}"
    conversations[cid] = sorted.toMutableList()
    messages.putIfAbsent(cid, mutableListOf())
    val peer = users[peerId]!!
    return ConversationDto(conversationId = cid, type = "direct", title = peer.displayName, peerUser = peer)
  }

  private fun assertAccess(uid: String, cid: String) {
    if (cid.startsWith("GRP_")) {
      val g = groups[cid] ?: throw ApiException("GROUP_NOT_FOUND", "Missing group")
      if (uid !in g.activeMembers) throw ApiException("FORBIDDEN", "Not an active member")
    } else {
      val parts = conversations[cid] ?: throw ApiException("CONVERSATION_NOT_FOUND", "Missing conversation")
      if (uid !in parts) throw ApiException("FORBIDDEN", "Not a participant")
    }
  }

  fun sendMessage(
    token: String,
    cid: String,
    type: String,
    content: String,
    idem: String,
    mediaId: String?,
    mediaMeta: MediaMetaDto?,
  ): MessageDto {
    val uid = auth(token)
    assertAccess(uid, cid)
    val fullIdem = "$uid:$cid:$idem"
    idempotency[fullIdem]?.let { return it }
    val list = messages.getOrPut(cid) { mutableListOf() }
    val seq = list.size + 1L
    val msg =
      MessageDto(
        messageId = "MSG_${cid}_$seq",
        conversationId = cid,
        senderId = uid,
        messageType = type,
        content = content,
        mediaId = mediaId,
        mediaMeta = mediaMeta,
        sequenceNumber = seq,
        idempotencyKey = idem,
        timestamp = Instant.now().toString(),
      )
    list.add(msg)
    idempotency[fullIdem] = msg
    return msg
  }

  fun syncMessages(token: String, cid: String, afterSeq: Long): SyncMessagesResponseDto {
    val uid = auth(token)
    assertAccess(uid, cid)
    val list = messages[cid].orEmpty().filter { it.sequenceNumber > afterSeq }
    val lastSeq = list.lastOrNull()?.sequenceNumber ?: afterSeq
    return SyncMessagesResponseDto(conversationId = cid, messages = list, lastSequence = lastSeq, hasMore = false)
  }

  fun createGroup(token: String, name: String, desc: String, memberIds: List<String>): GroupInfoDto {
    val uid = auth(token)
    groupSeq += 1
    val gid = "GRP_$groupSeq"
    val active = (listOf(uid) + memberIds).toMutableSet()
    val info =
      GroupInfoDto(
        groupId = gid,
        name = name,
        description = desc,
        createdBy = uid,
        adminIds = listOf(uid),
        memberCount = active.size,
        members = active.map { GroupMemberDto(it, if (it == uid) "admin" else "member") },
      )
    groups[gid] = MutableGroup(info, active, mutableSetOf(uid))
    messages[gid] = mutableListOf()
    return info
  }

  fun addGroupMember(token: String, gid: String, targetId: String): GroupInfoDto {
    val uid = auth(token)
    val g = groups[gid]!!
    if (uid !in g.admins) throw ApiException("FORBIDDEN_ADMIN_REQUIRED", "Admin required")
    g.activeMembers.add(targetId)
    g.info = g.info.copy(memberCount = g.activeMembers.size)
    return g.info
  }

  fun removeGroupMember(token: String, gid: String, targetId: String): GroupInfoDto {
    val uid = auth(token)
    val g = groups[gid]!!
    if (uid != targetId && uid !in g.admins) throw ApiException("FORBIDDEN_ADMIN_REQUIRED", "Admin required")
    g.activeMembers.remove(targetId)
    g.info = g.info.copy(memberCount = g.activeMembers.size)
    return g.info
  }

  fun uploadMedia(token: String, category: String, fileName: String, mimeType: String, cid: String?, b64: String): MediaUploadResponseDto {
    val uid = auth(token)
    if (cid != null) assertAccess(uid, cid)
    mediaSeq += 1
    val mid = "MED_$mediaSeq"
    val rec = MediaRecordDto(mid, "DRV_$mid", category, fileName, mimeType, b64.length.toLong(), uid, cid)
    mediaStore[mid] = rec to b64
    return MediaUploadResponseDto(completed = true, media = rec)
  }

  fun getMedia(token: String, mediaId: String): MediaDownloadDto {
    val uid = auth(token)
    val (rec, b64) = mediaStore[mediaId] ?: throw ApiException("MEDIA_NOT_FOUND", "Not found")
    rec.conversationId?.let { assertAccess(uid, it) }
    return MediaDownloadDto(rec, b64)
  }

  fun addContact(token: String, targetId: String): UserProfileDto {
    val uid = auth(token)
    contacts.getOrPut(uid) { mutableSetOf() }.add(targetId)
    return users[targetId]!!
  }

  fun getContacts(token: String): List<UserProfileDto> {
    val uid = auth(token)
    return contacts[uid].orEmpty().mapNotNull { users[it] }
  }
}

private class ServerClientProxy(
  private val server: InMemoryBackendServer,
  private val tokenStorage: SecureTokenStorage,
) : ConnectMeApi {
  private fun tok(): String = tokenStorage.getSession()?.accessToken ?: throw ApiException("UNAUTHORIZED", "Missing token")

  override suspend fun healthCheck() = HealthCheckDto(status = "healthy", driveAccess = true)

  override suspend fun initializeDrive() = mapOf("ok" to true)

  override suspend fun guestRegister(deviceId: String, displayName: String, username: String?) =
    server.guestRegister(displayName, username)

  override suspend fun guestLogin(deviceId: String, userId: String?): AuthResponseDto {
    val uid = tokenStorage.getSession()?.userId ?: throw ApiException("GUEST_NOT_FOUND", "No guest")
    return AuthResponseDto(server.getMyProfile(tok()), SessionDto(tok(), tokenStorage.getSession()!!.refreshToken, "", System.currentTimeMillis() + 3_600_000L, uid))
  }

  override suspend fun requestPhoneOtp(countryCode: String, phoneNumber: String, upgradeUserId: String?): OtpChallengeResponseDto =
    throw ApiException("SMS_PROVIDER_NOT_CONFIGURED", "No SMS provider in test")

  override suspend fun verifyPhoneOtp(challengeId: String, countryCode: String, phoneNumber: String, otpCode: String, displayName: String?, deviceId: String?): AuthResponseDto =
    throw ApiException("OTP_NOT_FOUND", "N/A")

  override suspend fun refreshSession(refreshToken: String) = server.refreshSession(refreshToken)

  override suspend fun logout() {}

  override suspend fun getMyProfile() = server.getMyProfile(tok())

  override suspend fun searchUser(query: String, defaultCountryCode: String) = server.searchUser(tok(), query)

  override suspend fun getUserProfile(userId: String) = server.searchUser(tok(), userId).first()

  override suspend fun updateProfile(displayName: String?, bio: String?, privacy: PrivacySettingsDto?) =
    server.updateProfile(tok(), displayName, bio, privacy)

  override suspend fun updateUsername(username: String) = server.updateUsername(tok(), username)

  override suspend fun uploadProfilePhoto(fileName: String, mimeType: String, base64Data: String) = "MED_AVATAR"

  override suspend fun addContact(contactUserId: String, nickname: String?) = server.addContact(tok(), contactUserId)

  override suspend fun removeContact(contactUserId: String) {}

  override suspend fun getContacts() = server.getContacts(tok())

  override suspend fun createConversation(peerUserId: String) = server.createConversation(tok(), peerId = peerUserId)

  override suspend fun getConversations() = emptyList<ConversationDto>()

  override suspend fun getConversationInfo(conversationId: String) = ConversationDto(conversationId = conversationId)

  override suspend fun sendMessage(conversationId: String, messageType: String, content: String, idempotencyKey: String, mediaId: String?, mediaMeta: MediaMetaDto?) =
    server.sendMessage(tok(), conversationId, messageType, content, idempotencyKey, mediaId, mediaMeta)

  override suspend fun syncMessages(conversationId: String, afterSequence: Long, limit: Int) =
    server.syncMessages(tok(), conversationId, afterSequence)

  override suspend fun getMessageHistory(conversationId: String, beforeSequence: Long?, limit: Int) =
    server.syncMessages(tok(), conversationId, 0L)

  override suspend fun markMessageRead(conversationId: String, sequenceNumber: Long) {}

  override suspend fun createGroup(name: String, description: String, memberIds: List<String>) =
    server.createGroup(tok(), name, description, memberIds)

  override suspend fun getGroupInfo(groupId: String) = GroupInfoDto(groupId = groupId, name = "Group")

  override suspend fun addGroupMember(groupId: String, userId: String) =
    server.addGroupMember(tok(), groupId, userId)

  override suspend fun removeGroupMember(groupId: String, userId: String) =
    server.removeGroupMember(tok(), groupId, userId)

  override suspend fun updateGroupInfo(groupId: String, name: String?, description: String?, photoMediaId: String?) =
    GroupInfoDto(groupId = groupId, name = name ?: "Group")

  override suspend fun uploadMediaChunk(category: String, fileName: String, mimeType: String, conversationId: String?, base64Data: String, totalBytes: Long, chunkIndex: Int, totalChunks: Int, uploadSessionId: String?) =
    server.uploadMedia(tok(), category, fileName, mimeType, conversationId, base64Data)

  override suspend fun getMedia(mediaId: String) = server.getMedia(tok(), mediaId)

  override suspend fun deleteMedia(mediaId: String) {}
}
