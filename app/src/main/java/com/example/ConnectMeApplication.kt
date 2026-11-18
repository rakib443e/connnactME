package com.example

import android.app.Application
import com.example.core.config.AppSettingsStore
import com.example.core.media.MediaHelper
import com.example.core.network.NetworkMonitor
import com.example.core.network.OkHttpAppsScriptClient
import com.example.core.security.SecureTokenStorage
import com.example.core.storage.ConnectMeDatabase
import com.example.core.sync.PollingManager
import com.example.data.ConnectMeRepository

class ConnectMeApplication : Application() {
  lateinit var settingsStore: AppSettingsStore
    private set

  lateinit var tokenStorage: SecureTokenStorage
    private set

  lateinit var networkMonitor: NetworkMonitor
    private set

  lateinit var database: ConnectMeDatabase
    private set

  lateinit var mediaHelper: MediaHelper
    private set

  lateinit var repository: ConnectMeRepository
    private set

  lateinit var pollingManager: PollingManager
    private set

  override fun onCreate() {
    super.onCreate()
    settingsStore = AppSettingsStore(this)
    tokenStorage = SecureTokenStorage(this)
    networkMonitor = NetworkMonitor(this)
    database = ConnectMeDatabase.build(this)
    mediaHelper = MediaHelper(this)

    val apiClient = OkHttpAppsScriptClient(settingsStore, tokenStorage)
    repository =
      ConnectMeRepository(
        api = apiClient,
        db = database,
        tokenStorage = tokenStorage,
        settingsStore = settingsStore,
        networkMonitor = networkMonitor,
        mediaHelper = mediaHelper,
      )

    pollingManager =
      PollingManager(
        networkMonitor = networkMonitor,
        syncConversations = { repository.syncConversations() },
        syncChat = { cid -> repository.syncNewMessages(cid) },
        flushPendingQueue = { repository.flushPendingMessages() },
      )
  }
}
