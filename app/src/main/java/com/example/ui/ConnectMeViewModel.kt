package com.example.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.config.AppSettingsStore
import com.example.core.network.ConversationDto
import com.example.core.network.GroupInfoDto
import com.example.core.network.HealthCheckDto
import com.example.core.network.OtpChallengeResponseDto
import com.example.core.network.PrivacySettingsDto
import com.example.core.network.UserProfileDto
import com.example.core.storage.CachedContactEntity
import com.example.core.storage.CachedConversationEntity
import com.example.core.storage.CachedMessageEntity
import com.example.core.sync.PollingManager
import com.example.core.sync.PollingStatus
import com.example.core.sync.PollingSurface
import com.example.data.ConnectMeRepository
import com.example.ui.theme.ThemePreference
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiActionState {
  data object Idle : UiActionState

  data class Loading(val message: String = "Working...") : UiActionState

  data class Error(val message: String, val remediation: String? = null) : UiActionState

  data class Success(val message: String) : UiActionState
}

sealed interface AuthGateState {
  data object Checking : AuthGateState

  data object Unauthenticated : AuthGateState

  data class Authenticated(val user: UserProfileDto) : AuthGateState
}

class ConnectMeViewModel(
  private val repository: ConnectMeRepository,
  private val pollingManager: PollingManager,
  private val settingsStore: AppSettingsStore,
) : ViewModel() {

  private val _authGate = MutableStateFlow<AuthGateState>(AuthGateState.Checking)
  val authGate: StateFlow<AuthGateState> = _authGate.asStateFlow()

  private val _actionState = MutableStateFlow<UiActionState>(UiActionState.Idle)
  val actionState: StateFlow<UiActionState> = _actionState.asStateFlow()

  private val _pendingOtpChallenge = MutableStateFlow<OtpChallengeResponseDto?>(null)
  val pendingOtpChallenge: StateFlow<OtpChallengeResponseDto?> = _pendingOtpChallenge.asStateFlow()

  private val _healthStatus = MutableStateFlow<HealthCheckDto?>(null)
  val healthStatus: StateFlow<HealthCheckDto?> = _healthStatus.asStateFlow()

  private val _searchResults = MutableStateFlow<List<UserProfileDto>>(emptyList())
  val searchResults: StateFlow<List<UserProfileDto>> = _searchResults.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  private val _viewedUserProfile = MutableStateFlow<UserProfileDto?>(null)
  val viewedUserProfile: StateFlow<UserProfileDto?> = _viewedUserProfile.asStateFlow()

  private val _activeGroup = MutableStateFlow<GroupInfoDto?>(null)
  val activeGroup: StateFlow<GroupInfoDto?> = _activeGroup.asStateFlow()

  private val _resolvedMediaFiles = MutableStateFlow<Map<String, File>>(emptyMap())
  val resolvedMediaFiles: StateFlow<Map<String, File>> = _resolvedMediaFiles.asStateFlow()

  val isOnline: StateFlow<Boolean> = repository.isOnline
  val currentUser: StateFlow<UserProfileDto?> = repository.currentUser
  val uploadProgress: StateFlow<Map<String, Float>> = repository.uploadProgress
  val pollingStatus: StateFlow<PollingStatus> = pollingManager.status

  val backendUrl: StateFlow<String> =
    settingsStore.backendUrlFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

  val themePreference: StateFlow<ThemePreference> =
    settingsStore.themePreferenceFlow.stateIn(
      viewModelScope,
      SharingStarted.Eagerly,
      ThemePreference.SYSTEM,
    )

  val conversations: StateFlow<List<CachedConversationEntity>> =
    repository
      .observeConversations()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val contacts: StateFlow<List<CachedContactEntity>> =
    repository
      .observeContacts()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  init {
    viewModelScope.launch {
      repository.currentUser.collect { user ->
        if (user != null) {
          _authGate.value = AuthGateState.Authenticated(user)
          pollingManager.setAuthenticated(true)
        }
      }
    }
    restoreSessionOnLaunch()
  }

  fun clearActionState() {
    _actionState.value = UiActionState.Idle
  }

  fun setPollingSurface(surface: PollingSurface, conversationId: String? = null) {
    pollingManager.setActiveSurface(surface, conversationId)
  }

  fun onAppForegroundChanged(foreground: Boolean) {
    pollingManager.onAppForegroundChanged(foreground)
  }

  fun restoreSessionOnLaunch() {
    viewModelScope.launch {
      _authGate.value = AuthGateState.Checking
      if (!repository.hasStoredSession()) {
        _authGate.value = AuthGateState.Unauthenticated
        pollingManager.setAuthenticated(false)
        return@launch
      }
      repository
        .restoreSession()
        .onSuccess { user ->
          if (user != null) {
            _authGate.value = AuthGateState.Authenticated(user)
            pollingManager.setAuthenticated(true)
            refreshHomeData()
          } else {
            _authGate.value = AuthGateState.Unauthenticated
            pollingManager.setAuthenticated(false)
          }
        }
        .onFailure {
          _authGate.value = AuthGateState.Unauthenticated
          pollingManager.setAuthenticated(false)
        }
    }
  }

  fun refreshHomeData() {
    viewModelScope.launch {
      repository.syncConversations()
      repository.syncContacts()
      repository.flushPendingMessages()
    }
  }

  fun saveBackendUrl(url: String, runCheck: Boolean = true) {
    viewModelScope.launch {
      settingsStore.setBackendUrl(url)
      if (runCheck && url.isNotBlank()) {
        runHealthCheck()
      }
    }
  }

  fun runHealthCheck() {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Checking Apps Script & Google Drive...")
      repository
        .checkBackendHealth()
        .onSuccess { health ->
          _healthStatus.value = health
          if (health.driveAccess) {
            _actionState.value =
              UiActionState.Success("Backend healthy: ${health.driveMessage}")
          } else {
            _actionState.value =
              UiActionState.Error(health.driveMessage, health.remediation)
          }
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Health check failed.")
        }
    }
  }

  fun initializeDriveStructure() {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Initializing ConnectMe_Database in Google Drive...")
      repository
        .initializeDrive()
        .onSuccess {
          runHealthCheck()
          _actionState.value =
            UiActionState.Success("Google Drive folders & System indexes initialized.")
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Drive initialization failed.")
        }
    }
  }

  fun setThemePreference(pref: ThemePreference) {
    viewModelScope.launch { settingsStore.setThemePreference(pref) }
  }

  fun registerGuest(displayName: String, username: String?, onDone: () -> Unit) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Creating guest identity...")
      repository
        .guestRegister(displayName, username)
        .onSuccess { user ->
          _authGate.value = AuthGateState.Authenticated(user)
          pollingManager.setAuthenticated(true)
          _actionState.value = UiActionState.Idle
          refreshHomeData()
          onDone()
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Guest registration failed.")
        }
    }
  }

  fun loginExistingGuest(onDone: () -> Unit) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Restoring device guest session...")
      repository
        .guestLogin()
        .onSuccess { user ->
          _authGate.value = AuthGateState.Authenticated(user)
          pollingManager.setAuthenticated(true)
          _actionState.value = UiActionState.Idle
          refreshHomeData()
          onDone()
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Guest login failed.")
        }
    }
  }

  fun requestPhoneOtp(
    countryCode: String,
    phoneNumber: String,
    isUpgrade: Boolean = false,
    onChallengeCreated: (OtpChallengeResponseDto) -> Unit,
  ) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Requesting verification code...")
      repository
        .requestPhoneOtp(countryCode, phoneNumber, isUpgrade)
        .onSuccess { challenge ->
          _pendingOtpChallenge.value = challenge
          _actionState.value = UiActionState.Idle
          onChallengeCreated(challenge)
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Could not request OTP.")
        }
    }
  }

  fun verifyPhoneOtp(
    challengeId: String,
    countryCode: String,
    phoneNumber: String,
    otpCode: String,
    displayName: String?,
    onVerified: () -> Unit,
  ) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Verifying OTP...")
      repository
        .verifyPhoneOtp(challengeId, countryCode, phoneNumber, otpCode, displayName)
        .onSuccess { user ->
          _pendingOtpChallenge.value = null
          _authGate.value = AuthGateState.Authenticated(user)
          pollingManager.setAuthenticated(true)
          _actionState.value = UiActionState.Success("Phone verified!")
          refreshHomeData()
          onVerified()
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "OTP verification failed.")
        }
    }
  }

  fun logout(onLoggedOut: () -> Unit) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Signing out...")
      pollingManager.setAuthenticated(false)
      repository.logout()
      _authGate.value = AuthGateState.Unauthenticated
      _actionState.value = UiActionState.Idle
      onLoggedOut()
    }
  }

  fun updateProfile(
    displayName: String? = null,
    bio: String? = null,
    privacy: PrivacySettingsDto? = null,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Saving profile...")
      repository
        .updateProfile(displayName, bio, privacy)
        .onSuccess {
          _actionState.value = UiActionState.Success("Profile updated.")
          onDone()
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Profile update failed.")
        }
    }
  }

  fun updateUsername(username: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Updating username...")
      repository
        .updateUsername(username)
        .onSuccess {
          _actionState.value = UiActionState.Success("Username updated to @$username.")
          onDone()
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Could not update username.")
        }
    }
  }

  fun uploadAvatar(uri: Uri) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Uploading avatar to Drive...")
      repository
        .uploadProfilePhoto(uri)
        .onSuccess {
          _actionState.value = UiActionState.Success("Profile photo updated.")
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Avatar upload failed.")
        }
    }
  }

  fun searchUsers(query: String) {
    if (query.trim().length < 2) {
      _searchResults.value = emptyList()
      return
    }
    viewModelScope.launch {
      _isSearching.value = true
      repository
        .searchUsers(query.trim())
        .onSuccess { _searchResults.value = it }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Search failed.")
        }
      _isSearching.value = false
    }
  }

  fun loadUserProfile(userId: String) {
    viewModelScope.launch {
      _viewedUserProfile.value = null
      repository
        .getUserProfile(userId)
        .onSuccess { _viewedUserProfile.value = it }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Could not load user profile.")
        }
    }
  }

  fun addContact(userId: String) {
    viewModelScope.launch {
      repository
        .addContact(userId)
        .onSuccess { _actionState.value = UiActionState.Success("Added to contacts.") }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Could not add contact.")
        }
    }
  }

  fun removeContact(userId: String) {
    viewModelScope.launch {
      repository
        .removeContact(userId)
        .onSuccess { _actionState.value = UiActionState.Success("Removed from contacts.") }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Could not remove contact.")
        }
    }
  }

  fun startDirectChat(peerUserId: String, onOpenChat: (ConversationDto) -> Unit) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Opening conversation...")
      repository
        .startDirectConversation(peerUserId)
        .onSuccess { conv ->
          _actionState.value = UiActionState.Idle
          onOpenChat(conv)
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Failed to start chat.")
        }
    }
  }

  fun observeMessages(conversationId: String): Flow<List<CachedMessageEntity>> =
    repository.observeMessages(conversationId)

  fun observeConversationMedia(conversationId: String): Flow<List<CachedMessageEntity>> =
    repository.observeConversationMedia(conversationId)

  fun syncChatNow(conversationId: String) {
    viewModelScope.launch { repository.syncNewMessages(conversationId) }
  }

  fun loadOlderMessages(conversationId: String, onResult: (Boolean) -> Unit = {}) {
    viewModelScope.launch {
      repository
        .loadOlderMessages(conversationId)
        .onSuccess { hasMore -> onResult(hasMore) }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Failed to load history.")
        }
    }
  }

  fun sendTextMessage(conversationId: String, text: String) {
    viewModelScope.launch {
      repository.sendTextMessage(conversationId, text).onFailure { err ->
        if (err.message?.contains("Queued", ignoreCase = true) != true) {
          _actionState.value = UiActionState.Error(err.message ?: "Message delivery failed.")
        }
      }
    }
  }

  fun sendMediaMessage(
    conversationId: String,
    uri: Uri,
    caption: String = "",
    expectedCategory: String? = null,
  ) {
    viewModelScope.launch {
      repository
        .sendMediaUriMessage(conversationId, uri, caption, expectedCategory)
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Media send failed.")
        }
    }
  }

  fun sendVoiceMessage(conversationId: String, file: File, durationMs: Long) {
    viewModelScope.launch {
      repository.sendRecordedVoiceMessage(conversationId, file, durationMs).onFailure { err ->
        _actionState.value = UiActionState.Error(err.message ?: "Voice upload failed.")
      }
    }
  }

  fun retryFailedMessage(localId: String) {
    viewModelScope.launch { repository.retryMessage(localId) }
  }

  fun createGroup(
    name: String,
    description: String,
    memberIds: List<String>,
    onCreated: (GroupInfoDto) -> Unit,
  ) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Creating group...")
      repository
        .createGroup(name, description, memberIds)
        .onSuccess { group ->
          _activeGroup.value = group
          _actionState.value = UiActionState.Idle
          onCreated(group)
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Could not create group.")
        }
    }
  }

  fun loadGroupInfo(groupId: String) {
    viewModelScope.launch {
      repository
        .getGroupInfo(groupId)
        .onSuccess { _activeGroup.value = it }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Failed to load group info.")
        }
    }
  }

  fun addGroupMember(groupId: String, userId: String) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Adding member...")
      repository
        .addGroupMember(groupId, userId)
        .onSuccess {
          _activeGroup.value = it
          _actionState.value = UiActionState.Success("Member added.")
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Could not add member.")
        }
    }
  }

  fun removeGroupMember(groupId: String, userId: String, onSelfLeft: () -> Unit = {}) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Updating membership...")
      repository
        .removeGroupMember(groupId, userId)
        .onSuccess { updated ->
          _activeGroup.value = updated
          _actionState.value = UiActionState.Success("Member removed.")
          if (userId == currentUser.value?.userId) {
            onSelfLeft()
          }
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Could not remove member.")
        }
    }
  }

  fun updateGroupInfo(groupId: String, name: String, description: String) {
    viewModelScope.launch {
      _actionState.value = UiActionState.Loading("Saving group details...")
      repository
        .updateGroupInfo(groupId, name, description)
        .onSuccess {
          _activeGroup.value = it
          _actionState.value = UiActionState.Success("Group updated.")
        }
        .onFailure { err ->
          _actionState.value = UiActionState.Error(err.message ?: "Could not update group.")
        }
    }
  }

  fun resolveMedia(mediaId: String) {
    if (_resolvedMediaFiles.value.containsKey(mediaId)) return
    viewModelScope.launch {
      repository.resolveMediaFile(mediaId).onSuccess { file ->
        _resolvedMediaFiles.value = _resolvedMediaFiles.value + (mediaId to file)
      }
    }
  }

  suspend fun fetchMediaSync(mediaId: String): Result<File> {
    _resolvedMediaFiles.value[mediaId]?.let { if (it.exists()) return Result.success(it) }
    val res = repository.resolveMediaFile(mediaId)
    res.onSuccess { file ->
      _resolvedMediaFiles.value = _resolvedMediaFiles.value + (mediaId to file)
    }
    return res
  }

  class Factory(
    private val repository: ConnectMeRepository,
    private val pollingManager: PollingManager,
    private val settingsStore: AppSettingsStore,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      ConnectMeViewModel(repository, pollingManager, settingsStore) as T
  }
}
