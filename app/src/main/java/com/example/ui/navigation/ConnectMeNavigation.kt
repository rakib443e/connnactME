package com.example.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.media.MediaHelper
import com.example.ui.AuthGateState
import com.example.ui.ConnectMeViewModel
import com.example.ui.UiActionState
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AccountUpgradeScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DocumentPreviewScreen
import com.example.ui.screens.GroupCreationScreen
import com.example.ui.screens.GroupDetailsScreen
import com.example.ui.screens.GroupMembersScreen
import com.example.ui.screens.GuestRegistrationScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImagePreviewScreen
import com.example.ui.screens.MediaGalleryScreen
import com.example.ui.screens.OtpVerificationScreen
import com.example.ui.screens.PhoneLoginScreen
import com.example.ui.screens.PrivacySettingsScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.WelcomeScreen

object ConnectMeRoutes {
  const val SPLASH = "splash"
  const val WELCOME = "welcome"
  const val GUEST_REGISTER = "guest_register"
  const val PHONE_LOGIN = "phone_login"
  const val OTP_VERIFY = "otp_verify"
  const val PROFILE_SETUP = "profile_setup"
  const val HOME = "home"
  const val CHAT = "chat/{conversationId}/{title}/{isGroup}"
  const val GROUP_CREATE = "group_create"
  const val GROUP_DETAILS = "group_details/{groupId}"
  const val GROUP_MEMBERS = "group_members/{groupId}"
  const val USER_PROFILE = "user_profile/{userId}"
  const val PRIVACY = "privacy"
  const val ACCOUNT_UPGRADE = "account_upgrade"
  const val ABOUT = "about"
  const val IMAGE_PREVIEW = "image_preview/{mediaId}"
  const val VIDEO_PLAYER = "video_player/{mediaId}"
  const val DOCUMENT_PREVIEW = "document_preview/{mediaId}/{fileName}"
  const val MEDIA_GALLERY = "media_gallery/{conversationId}"

  fun chat(conversationId: String, title: String, isGroup: Boolean) =
    "chat/${Uri.encode(conversationId)}/${Uri.encode(title.ifBlank { "Chat" })}/$isGroup"

  fun groupDetails(groupId: String) = "group_details/${Uri.encode(groupId)}"

  fun groupMembers(groupId: String) = "group_members/${Uri.encode(groupId)}"

  fun userProfile(userId: String) = "user_profile/${Uri.encode(userId)}"

  fun imagePreview(mediaId: String) = "image_preview/${Uri.encode(mediaId)}"

  fun videoPlayer(mediaId: String) = "video_player/${Uri.encode(mediaId)}"

  fun documentPreview(mediaId: String, fileName: String) =
    "document_preview/${Uri.encode(mediaId)}/${Uri.encode(fileName.ifBlank { "document.pdf" })}"

  fun mediaGallery(conversationId: String) = "media_gallery/${Uri.encode(conversationId)}"
}

@Composable
fun ConnectMeNavHost(
  viewModel: ConnectMeViewModel,
  mediaHelper: MediaHelper,
) {
  val navController = rememberNavController()
  val lifecycleOwner = LocalLifecycleOwner.current
  val snackbarHostState = remember { SnackbarHostState() }

  val authGate by viewModel.authGate.collectAsStateWithLifecycle()
  val actionState by viewModel.actionState.collectAsStateWithLifecycle()
  val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
  val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
  val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
  val healthStatus by viewModel.healthStatus.collectAsStateWithLifecycle()
  val pollingStatus by viewModel.pollingStatus.collectAsStateWithLifecycle()
  val conversations by viewModel.conversations.collectAsStateWithLifecycle()
  val contacts by viewModel.contacts.collectAsStateWithLifecycle()
  val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
  val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
  val viewedUserProfile by viewModel.viewedUserProfile.collectAsStateWithLifecycle()
  val activeGroup by viewModel.activeGroup.collectAsStateWithLifecycle()
  val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
  val resolvedMediaFiles by viewModel.resolvedMediaFiles.collectAsStateWithLifecycle()
  val pendingOtpChallenge by viewModel.pendingOtpChallenge.collectAsStateWithLifecycle()
  val themePref by viewModel.themePreference.collectAsStateWithLifecycle()

  var otpCountryCode by rememberSaveable { mutableStateOf("+1") }
  var otpPhoneNumber by rememberSaveable { mutableStateOf("") }
  var otpIsUpgrade by rememberSaveable { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> viewModel.onAppForegroundChanged(true)
        Lifecycle.Event.ON_PAUSE -> viewModel.onAppForegroundChanged(false)
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(authGate) {
    val currentRoute = navController.currentDestination?.route
    when (authGate) {
      AuthGateState.Checking -> Unit
      AuthGateState.Unauthenticated -> {
        if (currentRoute == ConnectMeRoutes.SPLASH || currentRoute == ConnectMeRoutes.HOME) {
          navController.navigate(ConnectMeRoutes.WELCOME) {
            popUpTo(0) { inclusive = true }
          }
        }
      }
      is AuthGateState.Authenticated -> {
        if (
          currentRoute == ConnectMeRoutes.SPLASH ||
            currentRoute == ConnectMeRoutes.WELCOME ||
            currentRoute == ConnectMeRoutes.GUEST_REGISTER
        ) {
          navController.navigate(ConnectMeRoutes.HOME) {
            popUpTo(0) { inclusive = true }
          }
        }
      }
    }
  }

  LaunchedEffect(actionState) {
    if (actionState is UiActionState.Success) {
      snackbarHostState.showSnackbar((actionState as UiActionState.Success).message)
      viewModel.clearActionState()
    }
  }

  Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { outerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(outerPadding)) {
      NavHost(navController = navController, startDestination = ConnectMeRoutes.SPLASH) {
        composable(ConnectMeRoutes.SPLASH) { SplashScreen() }

        composable(ConnectMeRoutes.WELCOME) {
          WelcomeScreen(
            backendUrl = backendUrl,
            healthStatus = healthStatus,
            actionState = actionState,
            onSaveBackendUrl = { viewModel.saveBackendUrl(it) },
            onTestHealth = { viewModel.runHealthCheck() },
            onInitializeDrive = { viewModel.initializeDriveStructure() },
            onNavigateGuestRegister = {
              viewModel.clearActionState()
              navController.navigate(ConnectMeRoutes.GUEST_REGISTER)
            },
            onRestoreGuest = {
              viewModel.loginExistingGuest {
                navController.navigate(ConnectMeRoutes.HOME) { popUpTo(0) { inclusive = true } }
              }
            },
            onNavigatePhoneLogin = {
              viewModel.clearActionState()
              otpIsUpgrade = false
              navController.navigate(ConnectMeRoutes.PHONE_LOGIN)
            },
            onClearError = { viewModel.clearActionState() },
          )
        }

        composable(ConnectMeRoutes.GUEST_REGISTER) {
          GuestRegistrationScreen(
            actionState = actionState,
            onRegister = { displayName, username ->
              viewModel.registerGuest(displayName, username) {
                navController.navigate(ConnectMeRoutes.HOME) { popUpTo(0) { inclusive = true } }
              }
            },
            onClearError = { viewModel.clearActionState() },
            onBack = { navController.popBackStack() },
          )
        }

        composable(ConnectMeRoutes.PHONE_LOGIN) {
          PhoneLoginScreen(
            actionState = actionState,
            onRequestOtp = { cc, phone ->
              otpCountryCode = cc
              otpPhoneNumber = phone
              otpIsUpgrade = false
              viewModel.requestPhoneOtp(cc, phone, isUpgrade = false) {
                navController.navigate(ConnectMeRoutes.OTP_VERIFY)
              }
            },
            onClearError = { viewModel.clearActionState() },
            onBack = { navController.popBackStack() },
          )
        }

        composable(ConnectMeRoutes.OTP_VERIFY) {
          OtpVerificationScreen(
            challenge = pendingOtpChallenge,
            countryCode = otpCountryCode,
            phoneNumber = otpPhoneNumber,
            actionState = actionState,
            onVerifyOtp = { challengeId, otp, displayName ->
              viewModel.verifyPhoneOtp(challengeId, otpCountryCode, otpPhoneNumber, otp, displayName) {
                navController.navigate(ConnectMeRoutes.HOME) { popUpTo(0) { inclusive = true } }
              }
            },
            onResendOtp = {
              viewModel.requestPhoneOtp(otpCountryCode, otpPhoneNumber, otpIsUpgrade) {}
            },
            onClearError = { viewModel.clearActionState() },
            onBack = { navController.popBackStack() },
          )
        }

        composable(ConnectMeRoutes.PROFILE_SETUP) {
          ProfileSetupScreen(
            user = currentUser,
            avatarFile = currentUser?.photoMediaId?.let { resolvedMediaFiles[it] },
            actionState = actionState,
            onSaveProfile = { dn, un, bio ->
              viewModel.updateProfile(displayName = dn, bio = bio)
              if (un != currentUser?.username) {
                viewModel.updateUsername(un)
              }
            },
            onUploadAvatar = { uri -> viewModel.uploadAvatar(uri) },
            onClearState = { viewModel.clearActionState() },
            onBack = { navController.popBackStack() },
          )
        }

        composable(ConnectMeRoutes.HOME) {
          HomeScreen(
            currentUser = currentUser,
            isOnline = isOnline,
            backendUrl = backendUrl,
            healthStatus = healthStatus,
            pollingStatus = pollingStatus,
            conversations = conversations,
            contacts = contacts,
            searchResults = searchResults,
            isSearching = isSearching,
            resolvedMediaFiles = resolvedMediaFiles,
            themePreference = themePref,
            onSetPollingSurface = { surface -> viewModel.setPollingSurface(surface) },
            onRefreshHome = { viewModel.refreshHomeData() },
            onOpenConversation = { cid, title, isGroup ->
              navController.navigate(ConnectMeRoutes.chat(cid, title, isGroup))
            },
            onStartDirectChat = { peerId ->
              viewModel.startDirectChat(peerId) { conv ->
                navController.navigate(
                  ConnectMeRoutes.chat(conv.conversationId, conv.title, conv.type == "group")
                )
              }
            },
            onSearchUsers = { q -> viewModel.searchUsers(q) },
            onOpenUserProfile = { uid -> navController.navigate(ConnectMeRoutes.userProfile(uid)) },
            onAddContact = { uid -> viewModel.addContact(uid) },
            onRemoveContact = { uid -> viewModel.removeContact(uid) },
            onCreateGroup = { navController.navigate(ConnectMeRoutes.GROUP_CREATE) },
            onOpenProfileSetup = { navController.navigate(ConnectMeRoutes.PROFILE_SETUP) },
            onOpenPrivacy = { navController.navigate(ConnectMeRoutes.PRIVACY) },
            onOpenUpgrade = { navController.navigate(ConnectMeRoutes.ACCOUNT_UPGRADE) },
            onOpenAbout = { navController.navigate(ConnectMeRoutes.ABOUT) },
            onSetTheme = { pref -> viewModel.setThemePreference(pref) },
            onSaveBackendUrl = { url -> viewModel.saveBackendUrl(url) },
            onTestHealth = { viewModel.runHealthCheck() },
            onInitializeDrive = { viewModel.initializeDriveStructure() },
            onLogout = {
              viewModel.logout {
                navController.navigate(ConnectMeRoutes.WELCOME) {
                  popUpTo(0) { inclusive = true }
                }
              }
            },
            onResolveMedia = { mid -> viewModel.resolveMedia(mid) },
          )
        }

        composable(
          route = ConnectMeRoutes.CHAT,
          arguments =
            listOf(
              navArgument("conversationId") { type = NavType.StringType },
              navArgument("title") { type = NavType.StringType },
              navArgument("isGroup") { type = NavType.BoolType },
            ),
        ) { backStack ->
          val cid = backStack.arguments?.getString("conversationId").orEmpty()
          val title = backStack.arguments?.getString("title").orEmpty()
          val isGroup = backStack.arguments?.getBoolean("isGroup") ?: false
          val messagesFlow = remember(cid) { viewModel.observeMessages(cid) }
          val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

          ChatScreen(
            conversationId = cid,
            title = title,
            isGroup = isGroup,
            currentUserId = currentUser?.userId.orEmpty(),
            isOnline = isOnline,
            messages = messages,
            uploadProgress = uploadProgress,
            resolvedMediaFiles = resolvedMediaFiles,
            mediaHelper = mediaHelper,
            onSetPollingSurface = { surface, id -> viewModel.setPollingSurface(surface, id) },
            onSyncNow = { viewModel.syncChatNow(cid) },
            onLoadOlder = { cb -> viewModel.loadOlderMessages(cid, cb) },
            onSendText = { txt -> viewModel.sendTextMessage(cid, txt) },
            onSendMediaUri = { uri, cat -> viewModel.sendMediaMessage(cid, uri, expectedCategory = cat) },
            onSendVoiceFile = { f, dur -> viewModel.sendVoiceMessage(cid, f, dur) },
            onRetryMessage = { localId -> viewModel.retryFailedMessage(localId) },
            onResolveMedia = { mid -> viewModel.resolveMedia(mid) },
            onOpenImagePreview = { mid -> navController.navigate(ConnectMeRoutes.imagePreview(mid)) },
            onOpenVideoPlayer = { mid -> navController.navigate(ConnectMeRoutes.videoPlayer(mid)) },
            onOpenDocumentPreview = { mid, fname ->
              navController.navigate(ConnectMeRoutes.documentPreview(mid, fname))
            },
            onOpenMediaGallery = { navController.navigate(ConnectMeRoutes.mediaGallery(cid)) },
            onOpenGroupDetails = { navController.navigate(ConnectMeRoutes.groupDetails(cid)) },
            onBack = { navController.popBackStack() },
          )
        }

        composable(ConnectMeRoutes.GROUP_CREATE) {
          GroupCreationScreen(
            contacts = contacts,
            actionState = actionState,
            onCreateGroup = { name, desc, memberIds ->
              viewModel.createGroup(name, desc, memberIds) { group ->
                navController.navigate(
                  ConnectMeRoutes.chat(group.groupId, group.name, isGroup = true)
                ) {
                  popUpTo(ConnectMeRoutes.HOME)
                }
              }
            },
            onClearError = { viewModel.clearActionState() },
            onBack = { navController.popBackStack() },
          )
        }

        composable(
          route = ConnectMeRoutes.GROUP_DETAILS,
          arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStack ->
          val gid = backStack.arguments?.getString("groupId").orEmpty()
          GroupDetailsScreen(
            groupId = gid,
            currentUserId = currentUser?.userId.orEmpty(),
            groupInfo = activeGroup,
            actionState = actionState,
            onLoadGroup = { viewModel.loadGroupInfo(gid) },
            onUpdateGroupInfo = { n, d -> viewModel.updateGroupInfo(gid, n, d) },
            onOpenMembers = { navController.navigate(ConnectMeRoutes.groupMembers(gid)) },
            onOpenMediaGallery = { navController.navigate(ConnectMeRoutes.mediaGallery(gid)) },
            onLeaveGroup = {
              val myId = currentUser?.userId ?: return@GroupDetailsScreen
              viewModel.removeGroupMember(gid, myId) {
                navController.popBackStack(ConnectMeRoutes.HOME, inclusive = false)
              }
            },
            onClearState = { viewModel.clearActionState() },
            onBack = { navController.popBackStack() },
          )
        }

        composable(
          route = ConnectMeRoutes.GROUP_MEMBERS,
          arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStack ->
          val gid = backStack.arguments?.getString("groupId").orEmpty()
          GroupMembersScreen(
            groupId = gid,
            currentUserId = currentUser?.userId.orEmpty(),
            groupInfo = activeGroup,
            actionState = actionState,
            onLoadGroup = { viewModel.loadGroupInfo(gid) },
            onAddMember = { uid -> viewModel.addGroupMember(gid, uid) },
            onRemoveMember = { uid -> viewModel.removeGroupMember(gid, uid) },
            onClearState = { viewModel.clearActionState() },
            onBack = { navController.popBackStack() },
          )
        }

        composable(
          route = ConnectMeRoutes.USER_PROFILE,
          arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) { backStack ->
          val uid = backStack.arguments?.getString("userId").orEmpty()
          UserProfileScreen(
            userId = uid,
            profile = viewedUserProfile,
            resolvedMediaFiles = resolvedMediaFiles,
            onLoadProfile = { viewModel.loadUserProfile(uid) },
            onResolveMedia = { mid -> viewModel.resolveMedia(mid) },
            onStartChat = { peerId ->
              viewModel.startDirectChat(peerId) { conv ->
                navController.navigate(
                  ConnectMeRoutes.chat(conv.conversationId, conv.title, conv.type == "group")
                )
              }
            },
            onAddContact = { peerId -> viewModel.addContact(peerId) },
            onBack = { navController.popBackStack() },
          )
        }

        composable(ConnectMeRoutes.PRIVACY) {
          PrivacySettingsScreen(
            user = currentUser,
            actionState = actionState,
            onSavePrivacy = { priv -> viewModel.updateProfile(privacy = priv) },
            onClearState = { viewModel.clearActionState() },
            onBack = { navController.popBackStack() },
          )
        }

        composable(ConnectMeRoutes.ACCOUNT_UPGRADE) {
          AccountUpgradeScreen(
            currentUser = currentUser,
            actionState = actionState,
            onRequestUpgradeOtp = { cc, phone ->
              otpCountryCode = cc
              otpPhoneNumber = phone
              otpIsUpgrade = true
              viewModel.requestPhoneOtp(cc, phone, isUpgrade = true) {
                navController.navigate(ConnectMeRoutes.OTP_VERIFY)
              }
            },
            onClearError = { viewModel.clearActionState() },
            onBack = { navController.popBackStack() },
          )
        }

        composable(ConnectMeRoutes.ABOUT) {
          AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(
          route = ConnectMeRoutes.IMAGE_PREVIEW,
          arguments = listOf(navArgument("mediaId") { type = NavType.StringType }),
        ) { backStack ->
          val mid = backStack.arguments?.getString("mediaId").orEmpty()
          ImagePreviewScreen(
            mediaId = mid,
            fetchMedia = { viewModel.fetchMediaSync(it) },
            onBack = { navController.popBackStack() },
          )
        }

        composable(
          route = ConnectMeRoutes.VIDEO_PLAYER,
          arguments = listOf(navArgument("mediaId") { type = NavType.StringType }),
        ) { backStack ->
          val mid = backStack.arguments?.getString("mediaId").orEmpty()
          VideoPlayerScreen(
            mediaId = mid,
            fetchMedia = { viewModel.fetchMediaSync(it) },
            onBack = { navController.popBackStack() },
          )
        }

        composable(
          route = ConnectMeRoutes.DOCUMENT_PREVIEW,
          arguments =
            listOf(
              navArgument("mediaId") { type = NavType.StringType },
              navArgument("fileName") { type = NavType.StringType },
            ),
        ) { backStack ->
          val mid = backStack.arguments?.getString("mediaId").orEmpty()
          val fname = backStack.arguments?.getString("fileName").orEmpty()
          DocumentPreviewScreen(
            mediaId = mid,
            fileName = fname,
            fetchMedia = { viewModel.fetchMediaSync(it) },
            onBack = { navController.popBackStack() },
          )
        }

        composable(
          route = ConnectMeRoutes.MEDIA_GALLERY,
          arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { backStack ->
          val cid = backStack.arguments?.getString("conversationId").orEmpty()
          val mediaFlow = remember(cid) { viewModel.observeConversationMedia(cid) }
          val mediaMessages by mediaFlow.collectAsStateWithLifecycle(initialValue = emptyList())
          MediaGalleryScreen(
            mediaMessages = mediaMessages,
            resolvedMediaFiles = resolvedMediaFiles,
            onResolveMedia = { mid -> viewModel.resolveMedia(mid) },
            onOpenImage = { mid -> navController.navigate(ConnectMeRoutes.imagePreview(mid)) },
            onOpenVideo = { mid -> navController.navigate(ConnectMeRoutes.videoPlayer(mid)) },
            onOpenDocument = { mid, fname ->
              navController.navigate(ConnectMeRoutes.documentPreview(mid, fname))
            },
            onBack = { navController.popBackStack() },
          )
        }
      }
    }
  }
}
