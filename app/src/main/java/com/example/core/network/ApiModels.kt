package com.example.core.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiErrorDto(
  val code: String = "UNKNOWN_ERROR",
  val message: String = "Unexpected error",
  val details: Map<String, Any?>? = null,
)

@JsonClass(generateAdapter = true)
data class PrivacySettingsDto(
  val showOnlineStatus: Boolean = true,
  val allowSearchByPhone: Boolean = true,
  val allowGroupInvites: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class UserProfileDto(
  val userId: String,
  val username: String,
  val displayName: String,
  val bio: String = "",
  val photoMediaId: String? = null,
  val accountType: String = "guest",
  val phoneVerified: Boolean = false,
  val phoneMasked: String? = null,
  val privacy: PrivacySettingsDto? = null,
  val createdAt: String = "",
  val updatedAt: String = "",
  val nickname: String? = null,
  val addedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class SessionDto(
  val accessToken: String,
  val refreshToken: String,
  val expiresAt: String = "",
  val expiresAtMs: Long = 0L,
  val userId: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthResponseDto(
  val user: UserProfileDto,
  val session: SessionDto,
)

@JsonClass(generateAdapter = true)
data class OtpChallengeResponseDto(
  val challengeId: String,
  val phoneMasked: String,
  val expiresAt: String,
  val provider: String,
)

@JsonClass(generateAdapter = true)
data class GroupMemberDto(
  val userId: String,
  val role: String = "member",
  val joinedAt: String = "",
  val profile: UserProfileDto? = null,
)

@JsonClass(generateAdapter = true)
data class GroupInfoDto(
  val groupId: String,
  val name: String,
  val description: String = "",
  val photoMediaId: String? = null,
  val createdBy: String = "",
  val adminIds: List<String> = emptyList(),
  val memberCount: Int = 0,
  val lastSequence: Long = 0L,
  val createdAt: String = "",
  val updatedAt: String = "",
  val members: List<GroupMemberDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ConversationDto(
  val conversationId: String,
  val type: String = "direct", // "direct" | "group"
  val title: String = "",
  val subtitle: String = "",
  val photoMediaId: String? = null,
  val peerUser: UserProfileDto? = null,
  val groupInfo: GroupInfoDto? = null,
  val lastSequence: Long = 0L,
  val unreadCount: Int = 0,
  val lastMessagePreview: String = "",
  val lastMessageType: String? = null,
  val lastMessageAt: String = "",
  val updatedAt: String = "",
)

@JsonClass(generateAdapter = true)
data class MediaMetaDto(
  val fileName: String = "",
  val mimeType: String = "",
  val sizeBytes: Long = 0L,
  val durationMs: Long = 0L,
)

@JsonClass(generateAdapter = true)
data class MediaRecordDto(
  val mediaId: String,
  val driveFileId: String = "",
  val category: String = "",
  val fileName: String = "",
  val mimeType: String = "",
  val sizeBytes: Long = 0L,
  val uploaderId: String = "",
  val conversationId: String? = null,
  val isProfileAvatar: Boolean = false,
  val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class MessageDto(
  val messageId: String,
  val conversationId: String,
  val senderId: String,
  val receiverId: String = "",
  val messageType: String = "text",
  val content: String = "",
  val mediaId: String? = null,
  val mediaMeta: MediaMetaDto? = null,
  val sequenceNumber: Long = 0L,
  val idempotencyKey: String = "",
  val timestamp: String = "",
  val deliveryStatus: String = "server_confirmed",
  val readBy: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SyncMessagesResponseDto(
  val conversationId: String,
  val messages: List<MessageDto> = emptyList(),
  val lastSequence: Long = 0L,
  val latestServerSequence: Long = 0L,
  val hasMore: Boolean = false,
  val readSequenceByUser: Map<String, Long> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class HealthCheckDto(
  val status: String = "",
  val appName: String = "ConnectMe",
  val apiVersion: String = "1.0.0",
  val rootFolderId: String = "",
  val driveAccess: Boolean = false,
  val driveMessage: String = "",
  val remediation: String? = null,
  val smsConfigured: Boolean = false,
  val timestamp: String = "",
)

@JsonClass(generateAdapter = true)
data class MediaDownloadDto(
  val media: MediaRecordDto,
  val base64Data: String,
)

@JsonClass(generateAdapter = true)
data class MediaUploadResponseDto(
  val completed: Boolean = true,
  val uploadSessionId: String? = null,
  val chunkIndex: Int? = null,
  val totalChunks: Int? = null,
  val media: MediaRecordDto? = null,
)

class ApiException(
  val code: String,
  override val message: String,
  val remediation: String? = null,
) : Exception(message)
