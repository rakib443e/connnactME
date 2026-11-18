package com.example.core.sync

import com.example.core.config.AppConfig
import com.example.core.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PollingSurface {
  IDLE_BACKGROUND,
  ACTIVE_CHAT,
  CONVERSATION_LIST,
  OTHER_ACTIVE,
}

data class PollingStatus(
  val surface: PollingSurface = PollingSurface.IDLE_BACKGROUND,
  val activeConversationId: String? = null,
  val isSyncing: Boolean = false,
  val consecutiveFailures: Int = 0,
  val lastSyncAtMs: Long = 0L,
  val lastError: String? = null,
)

class PollingManager(
  private val networkMonitor: NetworkMonitor,
  private val syncConversations: suspend () -> Result<Unit>,
  private val syncChat: suspend (String) -> Result<Unit>,
  private val flushPendingQueue: suspend () -> Unit,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val pollMutex = Mutex()
  private var pollingJob: Job? = null
  private var isForeground: Boolean = true
  private var isAuthenticated: Boolean = false

  private val _status = MutableStateFlow(PollingStatus())
  val status: StateFlow<PollingStatus> = _status.asStateFlow()

  init {
    scope.launch {
      var wasOnline = networkMonitor.isOnline.value
      networkMonitor.isOnline.collect { online ->
        if (online && !wasOnline && isAuthenticated) {
          flushPendingQueue()
          triggerImmediateTick()
        }
        wasOnline = online
      }
    }
  }

  fun setAuthenticated(authenticated: Boolean) {
    isAuthenticated = authenticated
    if (!authenticated) {
      stopPolling()
    } else if (isForeground && _status.value.surface != PollingSurface.IDLE_BACKGROUND) {
      restartLoop()
    }
  }

  fun onAppForegroundChanged(foreground: Boolean) {
    isForeground = foreground
    if (!foreground) {
      pollingJob?.cancel()
      pollingJob = null
    } else if (isAuthenticated && _status.value.surface != PollingSurface.IDLE_BACKGROUND) {
      restartLoop()
    }
  }

  fun setActiveSurface(surface: PollingSurface, conversationId: String? = null) {
    val prev = _status.value
    if (prev.surface == surface && prev.activeConversationId == conversationId && pollingJob?.isActive == true) {
      return
    }
    _status.value =
      prev.copy(
        surface = surface,
        activeConversationId = if (surface == PollingSurface.ACTIVE_CHAT) conversationId else null,
        consecutiveFailures = 0,
        lastError = null,
      )
    if (surface == PollingSurface.IDLE_BACKGROUND || !isAuthenticated || !isForeground) {
      stopPolling()
    } else {
      restartLoop()
    }
  }

  fun stopPolling() {
    pollingJob?.cancel()
    pollingJob = null
    _status.value = _status.value.copy(isSyncing = false)
  }

  fun triggerImmediateTick() {
    if (!isAuthenticated || !isForeground) return
    scope.launch { runSinglePollTick() }
  }

  private fun restartLoop() {
    pollingJob?.cancel()
    pollingJob =
      scope.launch {
        while (isActive && isAuthenticated && isForeground) {
          val surface = _status.value.surface
          if (surface == PollingSurface.IDLE_BACKGROUND) break

          if (networkMonitor.isOnline.value) {
            runSinglePollTick()
          }

          val baseInterval =
            when (_status.value.surface) {
              PollingSurface.ACTIVE_CHAT -> AppConfig.POLL_ACTIVE_CHAT_MS
              PollingSurface.CONVERSATION_LIST -> AppConfig.POLL_CONVERSATION_LIST_MS
              PollingSurface.OTHER_ACTIVE -> AppConfig.POLL_OTHER_ACTIVE_MS
              PollingSurface.IDLE_BACKGROUND -> AppConfig.POLL_OTHER_ACTIVE_MS
            }

          val failures = _status.value.consecutiveFailures
          val waitMs =
            if (failures == 0) {
              baseInterval
            } else {
              val multiplier = 1L shl failures.coerceAtMost(4)
              (baseInterval * multiplier).coerceAtMost(AppConfig.POLL_MAX_BACKOFF_MS)
            }
          delay(waitMs)
        }
      }
  }

  private suspend fun runSinglePollTick() {
    if (pollMutex.isLocked) return // Avoid overlapping polling
    pollMutex.withLock {
      val current = _status.value
      if (current.surface == PollingSurface.IDLE_BACKGROUND) return
      _status.value = current.copy(isSyncing = true)

      val result =
        when (current.surface) {
          PollingSurface.ACTIVE_CHAT -> {
            val cid = current.activeConversationId
            if (!cid.isNullOrBlank()) syncChat(cid) else Result.success(Unit)
          }
          PollingSurface.CONVERSATION_LIST,
          PollingSurface.OTHER_ACTIVE -> syncConversations()
          PollingSurface.IDLE_BACKGROUND -> Result.success(Unit)
        }

      result.fold(
        onSuccess = {
          _status.value =
            _status.value.copy(
              isSyncing = false,
              consecutiveFailures = 0,
              lastSyncAtMs = System.currentTimeMillis(),
              lastError = null,
            )
        },
        onFailure = { err ->
          _status.value =
            _status.value.copy(
              isSyncing = false,
              consecutiveFailures = (_status.value.consecutiveFailures + 1).coerceAtMost(10),
              lastError = err.message,
            )
        },
      )
    }
  }
}
