package com.example.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
  @Query("SELECT * FROM conversations_cache ORDER BY lastMessageAt DESC")
  fun observeConversations(): Flow<List<CachedConversationEntity>>

  @Query("SELECT * FROM conversations_cache WHERE conversationId = :conversationId LIMIT 1")
  suspend fun getById(conversationId: String): CachedConversationEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(items: List<CachedConversationEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: CachedConversationEntity)

  @Query("DELETE FROM conversations_cache WHERE conversationId = :conversationId")
  suspend fun deleteById(conversationId: String)

  @Query("DELETE FROM conversations_cache")
  suspend fun clearAll()
}

@Dao
interface MessageDao {
  @Query(
    """
    SELECT * FROM messages_cache
    WHERE conversationId = :conversationId
    ORDER BY
      CASE WHEN sequenceNumber > 0 THEN 0 ELSE 1 END ASC,
      sequenceNumber ASC,
      timestamp ASC
    """
  )
  fun observeMessages(conversationId: String): Flow<List<CachedMessageEntity>>

  @Query("SELECT * FROM messages_cache WHERE localStatus IN ('QUEUED', 'FAILED') ORDER BY timestamp ASC")
  suspend fun getPendingMessages(): List<CachedMessageEntity>

  @Query("SELECT * FROM messages_cache WHERE localId = :localId LIMIT 1")
  suspend fun getByLocalId(localId: String): CachedMessageEntity?

  @Query("SELECT * FROM messages_cache WHERE idempotencyKey = :idempotencyKey LIMIT 1")
  suspend fun getByIdempotencyKey(idempotencyKey: String): CachedMessageEntity?

  @Query(
    """
    SELECT * FROM messages_cache
    WHERE conversationId = :conversationId AND mediaId IS NOT NULL
    ORDER BY sequenceNumber DESC
    """
  )
  fun observeConversationMedia(conversationId: String): Flow<List<CachedMessageEntity>>

  @Query(
    "SELECT MIN(sequenceNumber) FROM messages_cache WHERE conversationId = :conversationId AND sequenceNumber > 0"
  )
  suspend fun getMinSequence(conversationId: String): Long?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(message: CachedMessageEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(messages: List<CachedMessageEntity>)

  @Query("DELETE FROM messages_cache WHERE localId = :localId")
  suspend fun deleteByLocalId(localId: String)

  @Query("DELETE FROM messages_cache")
  suspend fun clearAll()
}

@Dao
interface SyncCursorDao {
  @Query("SELECT * FROM sync_cursors WHERE conversationId = :conversationId LIMIT 1")
  suspend fun getCursor(conversationId: String): SyncCursorEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(cursor: SyncCursorEntity)

  @Query("DELETE FROM sync_cursors")
  suspend fun clearAll()
}

@Dao
interface ContactDao {
  @Query("SELECT * FROM contacts_cache ORDER BY displayName ASC")
  fun observeContacts(): Flow<List<CachedContactEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(contacts: List<CachedContactEntity>)

  @Query("DELETE FROM contacts_cache WHERE userId = :userId")
  suspend fun deleteById(userId: String)

  @Query("DELETE FROM contacts_cache")
  suspend fun clearAll()
}

@Dao
interface MediaCacheDao {
  @Query("SELECT * FROM media_cache WHERE mediaId = :mediaId LIMIT 1")
  suspend fun getMedia(mediaId: String): CachedMediaEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: CachedMediaEntity)

  @Query("DELETE FROM media_cache")
  suspend fun clearAll()
}
