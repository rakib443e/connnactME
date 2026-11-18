package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.config.AppConfig
import com.example.core.network.HealthCheckDto
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.IndigoSecondary
import java.io.File

@Composable
fun OfflineBanner(visible: Boolean, modifier: Modifier = Modifier) {
  AnimatedVisibility(visible = visible, modifier = modifier) {
    Surface(
      color = MaterialTheme.colorScheme.errorContainer,
      contentColor = MaterialTheme.colorScheme.onErrorContainer,
      modifier = Modifier.fillMaxWidth().testTag("offline_banner"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Filled.CloudOff,
          contentDescription = "Offline status",
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
          text = "Offline mode — queued messages will sync automatically on reconnection.",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
        )
      }
    }
  }
}

@Composable
fun BackendUnconfiguredBanner(
  backendUrl: String,
  onConfigureClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(visible = backendUrl.isBlank(), modifier = modifier) {
    Surface(
      color = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      modifier =
        Modifier.fillMaxWidth()
          .clickable { onConfigureClick() }
          .testTag("backend_unconfigured_banner"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Filled.SettingsEthernet,
          contentDescription = "Configure backend",
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
          text = "Apps Script Web App URL not set. Tap to connect Google Drive backend.",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
fun AvatarCircle(
  name: String,
  photoFile: File? = null,
  size: Dp = 48.dp,
  isGroup: Boolean = false,
) {
  val initials =
    name
      .trim()
      .split(Regex("\\s+"))
      .filter { it.isNotEmpty() }
      .take(2)
      .joinToString("") { it.first().uppercase() }
      .ifEmpty { if (isGroup) "GR" else "CM" }

  if (photoFile != null && photoFile.exists()) {
    AsyncImage(
      model = photoFile,
      contentDescription = "$name avatar",
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(size).clip(CircleShape),
    )
  } else {
    val gradient =
      if (isGroup) {
        Brush.linearGradient(listOf(IndigoSecondary, CyanPrimary))
      } else {
        Brush.linearGradient(listOf(CyanPrimary, IndigoSecondary))
      }
    Box(
      modifier = Modifier.size(size).clip(CircleShape).background(gradient),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = initials,
        style =
          if (size >= 56.dp) MaterialTheme.typography.titleMedium
          else MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun EmptyStateView(
  icon: ImageVector,
  title: String,
  subtitle: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
      modifier = Modifier.size(76.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp),
        )
      }
    }
    Spacer(Modifier.height(20.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (actionLabel != null && onAction != null) {
      Spacer(Modifier.height(20.dp))
      Button(onClick = onAction) { Text(actionLabel) }
    }
  }
}

@Composable
fun ErrorStateCard(
  message: String,
  remediation: String? = null,
  onRetry: (() -> Unit)? = null,
  onDismiss: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Card(
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
      ),
    shape = RoundedCornerShape(16.dp),
    modifier = modifier.fillMaxWidth().testTag("error_state_card"),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = "Error")
        Spacer(Modifier.width(8.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
      }
      if (!remediation.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
          text = remediation,
          style = MaterialTheme.typography.bodySmall,
        )
      }
      if (onRetry != null || onDismiss != null) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (onRetry != null) {
            OutlinedButton(onClick = onRetry) { Text("Retry") }
          }
          if (onDismiss != null) {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
          }
        }
      }
    }
  }
}

@Composable
fun LoadingStateView(label: String = "Loading...", modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun BackendConfigDialog(
  currentUrl: String,
  healthStatus: HealthCheckDto?,
  onSaveUrl: (String) -> Unit,
  onTestHealth: () -> Unit,
  onInitializeDrive: () -> Unit,
  onDismiss: () -> Unit,
) {
  var urlInput by remember(currentUrl) { mutableStateOf(currentUrl) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Apps Script & Drive Backend") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Central Root Folder ID: ${AppConfig.ROOT_DRIVE_FOLDER_ID}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
          value = urlInput,
          onValueChange = { urlInput = it },
          label = { Text("Apps Script Web App URL (/exec)") },
          placeholder = { Text("https://script.google.com/macros/s/.../exec") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("backend_url_input"),
        )
        if (healthStatus != null) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text(
                text = "Status: ${healthStatus.status}",
                style = MaterialTheme.typography.labelLarge,
              )
              Text(
                text = healthStatus.driveMessage,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  if (healthStatus.smsConfigured) "SMS Provider: Configured"
                  else "SMS Provider: Not configured in Script Properties",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = {
              onSaveUrl(urlInput)
              onTestHealth()
            },
            modifier = Modifier.weight(1f).testTag("health_check_button"),
          ) {
            Text("Health Check")
          }
          OutlinedButton(
            onClick = {
              onSaveUrl(urlInput)
              onInitializeDrive()
            },
            modifier = Modifier.weight(1f).testTag("init_drive_button"),
          ) {
            Text("Init Drive")
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSaveUrl(urlInput)
          onDismiss()
        },
        modifier = Modifier.testTag("save_backend_url_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}
