package com.example.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities =
    [
      CachedConversationEntity::class,
      CachedMessageEntity::class,
      SyncCursorEntity::class,
      CachedContactEntity::class,
      CachedMediaEntity::class,
    ],
  version = 1,
  exportSchema = false,
)
abstract class ConnectMeDatabase : RoomDatabase() {
  abstract fun conversationDao(): ConversationDao

  abstract fun messageDao(): MessageDao

  abstract fun syncCursorDao(): SyncCursorDao

  abstract fun contactDao(): ContactDao

  abstract fun mediaCacheDao(): MediaCacheDao

  companion object {
    fun build(context: Context): ConnectMeDatabase =
      Room.databaseBuilder(
          context.applicationContext,
          ConnectMeDatabase::class.java,
          "connectme_local_cache.db",
        )
        .fallbackToDestructiveMigration(true)
        .build()
  }
}
