package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.config.AppConfig
import com.example.core.network.HealthCheckDto
import com.example.core.network.PrivacySettingsDto
import com.example.core.network.UserProfileDto
import com.example.ui.UiActionState
import com.example.ui.components.AvatarCircle
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView
import com.example.ui.theme.ThemePreference
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
  userId: String,
  profile: UserProfileDto?,
  resolvedMediaFiles: Map<String, File>,
  onLoadProfile: () -> Unit,
  onResolveMedia: (String) -> Unit,
  onStartChat: (String) -> Unit,
  onAddContact: (String) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  LaunchedEffect(userId) { onLoadProfile() }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("User Profile") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    if (profile == null) {
      LoadingStateView("Loading profile...", modifier = Modifier.padding(padding))
      return@Scaffold
    }

    LaunchedEffect(profile.photoMediaId) {
      profile.photoMediaId?.let(onResolveMedia)
    }

    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 520.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        AvatarCircle(
          name = profile.displayName,
          photoFile = profile.photoMediaId?.let { resolvedMediaFiles[it] },
          size = 96.dp,
        )
        Text(profile.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(
          "@${profile.username} • ${profile.userId}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(
            text = "Account: ${profile.accountType.uppercase()} • Joined ${profile.createdAt.take(10)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
        if (profile.bio.isNotBlank()) {
          Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Button(
          onClick = { onStartChat(profile.userId) },
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("profile_message_btn"),
        ) {
          Icon(Icons.Filled.ChatBubble, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Send Message")
        }
        OutlinedButton(
          onClick = { onAddContact(profile.userId) },
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("profile_add_contact_btn"),
        ) {
          Icon(Icons.Filled.PersonAdd, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Add to Contacts")
        }
      }
    }
  }
}

@Composable
fun SettingsTabContent(
  currentUser: UserProfileDto?,
  backendUrl: String,
  healthStatus: HealthCheckDto?,
  themePreference: ThemePreference,
  onSetTheme: (ThemePreference) -> Unit,
  onOpenBackendConfig: () -> Unit,
  onOpenProfileSetup: () -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenUpgrade: () -> Unit,
  onOpenAbout: () -> Unit,
  onLogout: () -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (currentUser != null) {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          AvatarCircle(name = currentUser.displayName, size = 54.dp)
          Spacer(Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(currentUser.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
              "@${currentUser.username} • ${currentUser.userId}",
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              "Type: ${currentUser.accountType.uppercase()}" +
                (currentUser.phoneMasked?.let { " ($it)" } ?: ""),
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }
    }

    Text("Appearance", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ThemePreference.entries.forEach { pref ->
        FilterChip(
          selected = themePreference == pref,
          onClick = { onSetTheme(pref) },
          label = { Text(pref.name.lowercase().replaceFirstChar { it.uppercase() }) },
        )
      }
    }

    SettingsRowCard(
      icon = Icons.Filled.Person,
      title = "Edit Profile & Avatar",
      subtitle = "Display name, @username, bio, and photo",
      tag = "settings_profile_row",
      onClick = onOpenProfileSetup,
    )
    SettingsRowCard(
      icon = Icons.Filled.Lock,
      title = "Privacy Settings",
      subtitle = "Phone search visibility & group invite controls",
      tag = "settings_privacy_row",
      onClick = onOpenPrivacy,
    )
    if (currentUser?.accountType == "guest") {
      SettingsRowCard(
        icon = Icons.Filled.Upgrade,
        title = "Upgrade Guest to Phone Account",
        subtitle = "Link a verified phone number without losing chats",
        tag = "settings_upgrade_row",
        onClick = onOpenUpgrade,
      )
    }
    SettingsRowCard(
      icon = Icons.Filled.SettingsEthernet,
      title = "Apps Script & Drive Backend",
      subtitle =
        if (backendUrl.isBlank()) "Not configured"
        else "${healthStatus?.status ?: "Configured"} • ${backendUrl.take(36)}...",
      tag = "settings_backend_row",
      onClick = onOpenBackendConfig,
    )
    SettingsRowCard(
      icon = Icons.Filled.Info,
      title = "About ConnectMe & Security Model",
      subtitle = "Central Drive storage architecture & limitations",
      tag = "settings_about_row",
      onClick = onOpenAbout,
    )

    OutlinedButton(
      onClick = onLogout,
      modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_logout_btn"),
    ) {
      Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text("Log Out")
    }
  }
}

@Composable
private fun SettingsRowCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  tag: String,
  onClick: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth().clickable { onClick() }.testTag(tag),
  ) {
    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
  user: UserProfileDto?,
  actionState: UiActionState,
  onSavePrivacy: (PrivacySettingsDto) -> Unit,
  onClearState: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val initial = user?.privacy ?: PrivacySettingsDto()
  var showOnline by remember(initial) { mutableStateOf(initial.showOnlineStatus) }
  var allowPhoneSearch by remember(initial) { mutableStateOf(initial.allowSearchByPhone) }
  var allowGroupInvites by remember(initial) { mutableStateOf(initial.allowGroupInvites) }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Privacy Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      PrivacyToggleRow(
        title = "Show Online Status",
        subtitle = "Allow contacts to see recent activity",
        checked = showOnline,
        onCheckedChange = { showOnline = it },
      )
      PrivacyToggleRow(
        title = "Discoverable by Phone Number",
        subtitle = "Private phone numbers are never displayed in search results",
        checked = allowPhoneSearch,
        onCheckedChange = { allowPhoneSearch = it },
      )
      PrivacyToggleRow(
        title = "Allow Group Invites",
        subtitle = "Permit contacts and peers to add you to group chats",
        checked = allowGroupInvites,
        onCheckedChange = { allowGroupInvites = it },
      )

      if (actionState is UiActionState.Error) {
        ErrorStateCard(message = actionState.message, onDismiss = onClearState)
      }

      Button(
        onClick = {
          onSavePrivacy(PrivacySettingsDto(showOnline, allowPhoneSearch, allowGroupInvites))
        },
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_privacy_btn"),
      ) {
        Text("Save Privacy Preferences")
      }
    }
  }
}

@Composable
private fun PrivacyToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountUpgradeScreen(
  currentUser: UserProfileDto?,
  actionState: UiActionState,
  onRequestUpgradeOtp: (countryCode: String, phoneNumber: String) -> Unit,
  onClearError: () -> Unit,
  onBack: () -> Unit,
) {
  PhoneLoginScreen(
    title = "Upgrade Guest Account (${currentUser?.userId ?: ""})",
    isUpgrade = true,
    actionState = actionState,
    onRequestOtp = onRequestUpgradeOtp,
    onClearError = onClearError,
    onBack = onBack,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("About ConnectMe") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text("ConnectMe Messaging", style = MaterialTheme.typography.headlineSmall)
      Text(
        "Package: ${AppConfig.ANDROID_PACKAGE}\nCentral Root Folder ID: ${AppConfig.ROOT_DRIVE_FOLDER_ID}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
      )
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Architecture & Security Disclosure", style = MaterialTheme.typography.titleMedium)
          Text(
            "ConnectMe uses a Google Apps Script HTTPS backend and a central Google Drive folder (${AppConfig.ROOT_DRIVE_FOLDER_NAME}) for JSON records and private media files, with local Room SQLite caching and Android Keystore AES-GCM token protection.",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            "Important: The central Google Drive owner and authorized Apps Script backend can access stored message contents and media. ConnectMe does not claim end-to-end encryption (E2EE).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
