package com.example.core.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations_cache")
data class CachedConversationEntity(
  @PrimaryKey val conversationId: String,
  val type: String, // "direct" | "group"
  val title: String,
  val subtitle: String,
  val photoMediaId: String?,
  val peerUserId: String?,
  val peerUsername: String?,
  val lastSequence: Long,
  val unreadCount: Int,
  val lastMessagePreview: String,
  val lastMessageType: String?,
  val lastMessageAt: String,
  val updatedAt: String,
)

@Entity(
  tableName = "messages_cache",
  indices = [
    Index(value = ["conversationId", "sequenceNumber"]),
    Index(value = ["idempotencyKey"], unique = true),
  ],
)
data class CachedMessageEntity(
  @PrimaryKey val localId: String,
  val serverMessageId: String?,
  val conversationId: String,
  val senderId: String,
  val receiverId: String,
  val messageType: String, // text, image, video, voice, document, system
  val content: String,
  val mediaId: String?,
  val mediaFileName: String?,
  val mediaMimeType: String?,
  val mediaSizeBytes: Long,
  val mediaDurationMs: Long,
  val localFileUri: String?,
  val sequenceNumber: Long, // 0 while queued/failed locally before server confirmation
  val idempotencyKey: String,
  val timestamp: String,
  val localStatus: String, // QUEUED | SENDING | FAILED | SERVER_CONFIRMED
  val errorMessage: String? = null,
)

@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
  @PrimaryKey val conversationId: String,
  val lastSequence: Long,
  val updatedAtMs: Long,
)

@Entity(tableName = "contacts_cache")
data class CachedContactEntity(
  @PrimaryKey val userId: String,
  val username: String,
  val displayName: String,
  val bio: String,
  val photoMediaId: String?,
  val accountType: String,
  val createdAt: String,
)

@Entity(tableName = "media_cache")
data class CachedMediaEntity(
  @PrimaryKey val mediaId: String,
  val localPath: String,
  val fileName: String,
  val mimeType: String,
  val category: String,
  val sizeBytes: Long,
  val cachedAtMs: Long,
)
