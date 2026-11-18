package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.network.HealthCheckDto
import com.example.core.network.OtpChallengeResponseDto
import com.example.core.network.UserProfileDto
import com.example.ui.UiActionState
import com.example.ui.components.AvatarCircle
import com.example.ui.components.BackendConfigDialog
import com.example.ui.components.ErrorStateCard
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.IndigoSecondary
import java.io.File

@Composable
fun SplashScreen() {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(ColorSplashTop, ColorSplashBottom))),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Image(
        painter = painterResource(id = R.drawable.img_app_icon_fg),
        contentDescription = "ConnectMe Logo",
        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)),
      )
      Spacer(Modifier.height(20.dp))
      Text(
        text = "ConnectMe",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onPrimary,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = "Google Drive Backed Messaging",
        style = MaterialTheme.typography.bodyMedium,
        color = CyanPrimary.copy(alpha = 0.9f),
      )
      Spacer(Modifier.height(28.dp))
      CircularProgressIndicator(color = CyanPrimary)
    }
  }
}

private val ColorSplashTop = androidx.compose.ui.graphics.Color(0xFF0B1120)
private val ColorSplashBottom = androidx.compose.ui.graphics.Color(0xFF141D2E)

@Composable
fun WelcomeScreen(
  backendUrl: String,
  healthStatus: HealthCheckDto?,
  actionState: UiActionState,
  onSaveBackendUrl: (String) -> Unit,
  onTestHealth: () -> Unit,
  onInitializeDrive: () -> Unit,
  onNavigateGuestRegister: () -> Unit,
  onRestoreGuest: () -> Unit,
  onNavigatePhoneLogin: () -> Unit,
  onClearError: () -> Unit,
) {
  var showBackendDialog by remember { mutableStateOf(false) }

  Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 560.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Hero Banner Illustration
        Card(
          shape = RoundedCornerShape(24.dp),
          elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
          modifier = Modifier.fillMaxWidth().height(200.dp),
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            Image(
              painter = painterResource(id = R.drawable.img_welcome_hero),
              contentDescription = "ConnectMe network illustration",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Surface(
              color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            ) {
              Text(
                text = "ConnectMe",
                style = MaterialTheme.typography.headlineMedium,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }
        }

        Text(
          text = "Direct & group messaging powered by your Apps Script & Google Drive vault.",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )

        // Backend Endpoint Card
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(16.dp),
          modifier =
            Modifier.fillMaxWidth()
              .clickable { showBackendDialog = true }
              .testTag("welcome_backend_config_card"),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector =
                if (backendUrl.isNotBlank()) Icons.Filled.CloudDone
                else Icons.Filled.SettingsEthernet,
              contentDescription = "Backend Status",
              tint =
                if (backendUrl.isNotBlank()) CyanPrimary
                else MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text =
                  if (backendUrl.isNotBlank()) "Apps Script Endpoint Configured"
                  else "Configure Apps Script Web App URL",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text =
                  if (backendUrl.isNotBlank()) backendUrl
                  else "Tap to paste your deployed /exec URL & verify Drive access",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
              )
            }
            TextButton(onClick = { showBackendDialog = true }) { Text("Setup") }
          }
        }

        if (actionState is UiActionState.Error) {
          ErrorStateCard(
            message = actionState.message,
            remediation = actionState.remediation,
            onDismiss = onClearError,
          )
        }

        Spacer(Modifier.height(4.dp))

        Button(
          onClick = onNavigateGuestRegister,
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("welcome_guest_register_btn"),
          shape = RoundedCornerShape(16.dp),
        ) {
          Icon(Icons.Filled.PersonAdd, contentDescription = null)
          Spacer(Modifier.width(10.dp))
          Text("Continue as Guest")
        }

        FilledTonalButton(
          onClick = onNavigatePhoneLogin,
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("welcome_phone_login_btn"),
          shape = RoundedCornerShape(16.dp),
        ) {
          Icon(Icons.Filled.PhoneAndroid, contentDescription = null)
          Spacer(Modifier.width(10.dp))
          Text("Sign In with Phone Number")
        }

        OutlinedButton(
          onClick = onRestoreGuest,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("welcome_restore_guest_btn"),
          shape = RoundedCornerShape(16.dp),
        ) {
          Icon(Icons.Filled.Restore, contentDescription = null)
          Spacer(Modifier.width(10.dp))
          Text("Restore Existing Device Guest")
        }

        if (actionState is UiActionState.Loading) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(actionState.message, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }

  if (showBackendDialog) {
    BackendConfigDialog(
      currentUrl = backendUrl,
      healthStatus = healthStatus,
      onSaveUrl = onSaveBackendUrl,
      onTestHealth = onTestHealth,
      onInitializeDrive = onInitializeDrive,
      onDismiss = { showBackendDialog = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestRegistrationScreen(
  actionState: UiActionState,
  onRegister: (displayName: String, username: String?) -> Unit,
  onClearError: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  var displayName by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Guest Registration") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 520.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text = "Create your device-linked ConnectMe guest identity. You can upgrade to a verified phone number anytime.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = displayName,
          onValueChange = { displayName = it },
          label = { Text("Display Name *") },
          placeholder = { Text("e.g. Alex Rivera") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("guest_display_name_input"),
        )

        OutlinedTextField(
          value = username,
          onValueChange = { username = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' } },
          label = { Text("Username (optional)") },
          placeholder = { Text("e.g. alex_r") },
          supportingText = { Text("3-24 lowercase letters, numbers, or underscores") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("guest_username_input"),
        )

        if (actionState is UiActionState.Error) {
          ErrorStateCard(message = actionState.message, onDismiss = onClearError)
        }

        Button(
          onClick = { onRegister(displayName.trim(), username.trim().ifBlank { null }) },
          enabled = displayName.isNotBlank() && actionState !is UiActionState.Loading,
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("guest_submit_btn"),
        ) {
          if (actionState is UiActionState.Loading) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
          }
          Text("Create Guest Account")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneLoginScreen(
  title: String = "Phone Number Login",
  isUpgrade: Boolean = false,
  actionState: UiActionState,
  onRequestOtp: (countryCode: String, phoneNumber: String) -> Unit,
  onClearError: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val countryCodes = listOf("+1 (US/CA)", "+44 (UK)", "+880 (BD)", "+91 (IN)", "+61 (AU)", "+81 (JP)", "+49 (DE)")
  var selectedCountry by remember { mutableStateOf("+1") }
  var showCountryMenu by remember { mutableStateOf(false) }
  var phoneNumber by remember { mutableStateOf("") }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text(title) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 520.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text =
            if (isUpgrade) {
              "Link a verified phone number to upgrade your Guest account while preserving your conversations and groups."
            } else {
              "Enter your phone number to receive a 6-digit OTP verification code via the configured backend SMS provider."
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Box {
            OutlinedButton(
              onClick = { showCountryMenu = true },
              modifier = Modifier.height(56.dp).testTag("country_code_btn"),
            ) {
              Text(selectedCountry)
            }
            DropdownMenu(
              expanded = showCountryMenu,
              onDismissRequest = { showCountryMenu = false },
            ) {
              countryCodes.forEach { item ->
                val code = item.substringBefore(" ")
                DropdownMenuItem(
                  text = { Text(item) },
                  onClick = {
                    selectedCountry = code
                    showCountryMenu = false
                  },
                )
              }
            }
          }

          OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it.filter { ch -> ch.isDigit() } },
            label = { Text("Phone Number") },
            placeholder = { Text("5551234567") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("phone_number_input"),
          )
        }

        if (actionState is UiActionState.Error) {
          ErrorStateCard(message = actionState.message, onDismiss = onClearError)
        }

        Button(
          onClick = { onRequestOtp(selectedCountry, phoneNumber) },
          enabled = phoneNumber.length >= 6 && actionState !is UiActionState.Loading,
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("request_otp_btn"),
        ) {
          if (actionState is UiActionState.Loading) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
          }
          Text("Send Verification Code")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
  challenge: OtpChallengeResponseDto?,
  countryCode: String,
  phoneNumber: String,
  actionState: UiActionState,
  onVerifyOtp: (challengeId: String, otpCode: String, displayName: String?) -> Unit,
  onResendOtp: () -> Unit,
  onClearError: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  var otpCode by remember { mutableStateOf("") }
  var displayName by remember { mutableStateOf("") }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Verify OTP") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 520.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text = "Enter the 6-digit code sent to ${challenge?.phoneMasked ?: "$countryCode $phoneNumber"}",
          style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
          value = otpCode,
          onValueChange = { if (it.length <= 6) otpCode = it.filter { ch -> ch.isDigit() } },
          label = { Text("6-Digit Verification Code") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("otp_code_input"),
        )

        OutlinedTextField(
          value = displayName,
          onValueChange = { displayName = it },
          label = { Text("Display Name (for new accounts)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("otp_display_name_input"),
        )

        if (actionState is UiActionState.Error) {
          ErrorStateCard(message = actionState.message, onDismiss = onClearError)
        }

        Button(
          onClick = {
            val cid = challenge?.challengeId ?: return@Button
            onVerifyOtp(cid, otpCode, displayName.trim().ifBlank { null })
          },
          enabled = otpCode.length == 6 && challenge != null && actionState !is UiActionState.Loading,
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("verify_otp_btn"),
        ) {
          Text("Verify & Continue")
        }

        TextButton(
          onClick = onResendOtp,
          modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
          Text("Resend OTP Code")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
  user: UserProfileDto?,
  avatarFile: File?,
  actionState: UiActionState,
  onSaveProfile: (displayName: String, username: String, bio: String) -> Unit,
  onUploadAvatar: (android.net.Uri) -> Unit,
  onClearState: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  var displayName by remember(user?.displayName) { mutableStateOf(user?.displayName ?: "") }
  var username by remember(user?.username) { mutableStateOf(user?.username ?: "") }
  var bio by remember(user?.bio) { mutableStateOf(user?.bio ?: "") }

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) onUploadAvatar(uri)
    }

  LaunchedEffect(actionState) {
    if (actionState is UiActionState.Success) {
      // Keep visible feedback briefly or let user tap back
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Profile Setup") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 520.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Box(contentAlignment = Alignment.BottomEnd) {
          AvatarCircle(
            name = displayName.ifBlank { user?.displayName ?: "CM" },
            photoFile = avatarFile,
            size = 96.dp,
          )
          Surface(
            shape = RoundedCornerShape(50),
            color = IndigoSecondary,
            modifier =
              Modifier.size(36.dp)
                .clickable {
                  photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                }
                .testTag("upload_avatar_btn"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                Icons.Filled.CameraAlt,
                contentDescription = "Upload profile photo",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }

        if (user != null) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(
              text = "User ID: ${user.userId} • ${user.accountType.uppercase()}",
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }

        OutlinedTextField(
          value = displayName,
          onValueChange = { displayName = it },
          label = { Text("Display Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("profile_display_name_input"),
        )

        OutlinedTextField(
          value = username,
          onValueChange = {
            username = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }
          },
          label = { Text("Username") },
          prefix = { Text("@") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("profile_username_input"),
        )

        OutlinedTextField(
          value = bio,
          onValueChange = { if (it.length <= 180) bio = it },
          label = { Text("Bio") },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth().testTag("profile_bio_input"),
        )

        when (actionState) {
          is UiActionState.Error ->
            ErrorStateCard(message = actionState.message, onDismiss = onClearState)
          is UiActionState.Success ->
            Text(
              text = actionState.message,
              color = CyanPrimary,
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.Center,
            )
          else -> Unit
        }

        Button(
          onClick = { onSaveProfile(displayName.trim(), username.trim(), bio.trim()) },
          enabled = displayName.isNotBlank() && username.length >= 3 && actionState !is UiActionState.Loading,
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("save_profile_btn"),
        ) {
          Text("Save Profile")
        }
      }
    }
  }
}
