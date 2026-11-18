package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.core.media.MediaHelper
import com.example.core.media.VoicePlayerController
import com.example.core.media.VoiceRecorderController
import com.example.core.storage.CachedMessageEntity
import com.example.core.sync.PollingSurface
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OfflineBanner
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StatusError
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
  conversationId: String,
  title: String,
  isGroup: Boolean,
  currentUserId: String,
  isOnline: Boolean,
  messages: List<CachedMessageEntity>,
  uploadProgress: Map<String, Float>,
  resolvedMediaFiles: Map<String, File>,
  mediaHelper: MediaHelper,
  onSetPollingSurface: (PollingSurface, String?) -> Unit,
  onSyncNow: () -> Unit,
  onLoadOlder: (onResult: (Boolean) -> Unit) -> Unit,
  onSendText: (String) -> Unit,
  onSendMediaUri: (Uri, String?) -> Unit,
  onSendVoiceFile: (File, Long) -> Unit,
  onRetryMessage: (String) -> Unit,
  onResolveMedia: (String) -> Unit,
  onOpenImagePreview: (String) -> Unit,
  onOpenVideoPlayer: (String) -> Unit,
  onOpenDocumentPreview: (String, String) -> Unit,
  onOpenMediaGallery: () -> Unit,
  onOpenGroupDetails: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val listState = rememberLazyListState()
  var inputText by remember { mutableStateOf("") }
  var showAttachSheet by remember { mutableStateOf(false) }
  var hasMoreHistory by remember { mutableStateOf(true) }
  var isRecordingVoice by remember { mutableStateOf(false) }
  var activePlayingPath by remember { mutableStateOf<String?>(null) }

  val voiceRecorder = remember { VoiceRecorderController(context) }
  val voicePlayer = remember { VoicePlayerController() }

  // 3-second active chat polling lifecycle
  LaunchedEffect(conversationId) {
    onSetPollingSurface(PollingSurface.ACTIVE_CHAT, conversationId)
    onSyncNow()
  }

  DisposableEffect(conversationId) {
    onDispose {
      voiceRecorder.stopAndDiscard()
      voicePlayer.stop()
      onSetPollingSurface(PollingSurface.IDLE_BACKGROUND, null)
    }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) onSendMediaUri(uri, "image")
    }

  val videoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) onSendMediaUri(uri, "video")
    }

  val documentPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) onSendMediaUri(uri, "document")
    }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        val file = mediaHelper.createVoiceRecordingFile()
        isRecordingVoice = voiceRecorder.start(file)
      }
    }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = {
          Column(
            modifier =
              Modifier.clickable { if (isGroup) onOpenGroupDetails() }
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = if (isGroup) "Group Chat • Tap for details" else "Direct Message",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(
            onClick = onOpenMediaGallery,
            modifier = Modifier.testTag("chat_media_gallery_btn"),
          ) {
            Icon(Icons.Filled.Collections, contentDescription = "Media Gallery")
          }
          if (isGroup) {
            IconButton(
              onClick = onOpenGroupDetails,
              modifier = Modifier.testTag("chat_group_details_btn"),
            ) {
              Icon(Icons.Filled.Info, contentDescription = "Group Details")
            }
          }
        },
      )
    },
    bottomBar = {
      Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().imePadding()) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
          if (isRecordingVoice) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .background(MaterialTheme.colorScheme.errorContainer)
                  .padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Recording voice message...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
              )
              TextButton(
                onClick = {
                  voiceRecorder.stopAndDiscard()
                  isRecordingVoice = false
                }
              ) {
                Text("Cancel")
              }
              IconButton(
                onClick = {
                  val res = voiceRecorder.stopAndSave()
                  isRecordingVoice = false
                  if (res != null) {
                    onSendVoiceFile(res.first, res.second)
                  }
                },
                modifier = Modifier.testTag("stop_send_voice_btn"),
              ) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop and send voice")
              }
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              IconButton(
                onClick = { showAttachSheet = true },
                modifier = Modifier.testTag("chat_attach_btn"),
              ) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach media")
              }
              OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Message...") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).testTag("chat_message_input"),
              )
              if (inputText.isBlank()) {
                IconButton(
                  onClick = {
                    val granted =
                      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) {
                      val file = mediaHelper.createVoiceRecordingFile()
                      isRecordingVoice = voiceRecorder.start(file)
                    } else {
                      audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                  },
                  modifier = Modifier.testTag("chat_mic_btn"),
                ) {
                  Icon(Icons.Filled.Mic, contentDescription = "Record voice message")
                }
              } else {
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(48.dp),
                ) {
                  IconButton(
                    onClick = {
                      val text = inputText.trim()
                      if (text.isNotEmpty()) {
                        onSendText(text)
                        inputText = ""
                      }
                    },
                    modifier = Modifier.testTag("chat_send_btn"),
                  ) {
                    Icon(
                      Icons.AutoMirrored.Filled.Send,
                      contentDescription = "Send message",
                      tint = MaterialTheme.colorScheme.onPrimary,
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      OfflineBanner(visible = !isOnline)

      if (messages.isEmpty()) {
        EmptyStateView(
          icon = Icons.AutoMirrored.Filled.Send,
          title = "Start the conversation",
          subtitle = "Send a text, photo, video, voice note, or PDF document.",
        )
      } else {
        LazyColumn(
          state = listState,
          contentPadding = PaddingValues(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxSize().testTag("chat_messages_list"),
        ) {
          if (hasMoreHistory && messages.firstOrNull()?.sequenceNumber?.let { it > 1L } == true) {
            item(key = "load_older") {
              Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OutlinedButton(
                  onClick = { onLoadOlder { more -> hasMoreHistory = more } },
                  modifier = Modifier.testTag("load_older_messages_btn"),
                ) {
                  Text("Load Earlier Messages")
                }
              }
            }
          }

          items(messages, key = { it.localId }) { msg ->
            LaunchedEffect(msg.mediaId) {
              msg.mediaId?.let { onResolveMedia(it) }
            }
            val localFile =
              msg.localFileUri?.let { File(it) }?.takeIf { it.exists() }
                ?: msg.mediaId?.let { resolvedMediaFiles[it] }

            MessageBubbleRow(
              msg = msg,
              isMine = msg.senderId == currentUserId,
              isGroup = isGroup,
              mediaFile = localFile,
              uploadFraction = uploadProgress[msg.localId],
              isPlayingVoice =
                localFile != null && activePlayingPath == localFile.absolutePath,
              onToggleVoice = { file ->
                if (activePlayingPath == file.absolutePath) {
                  voicePlayer.stop()
                  activePlayingPath = null
                } else {
                  activePlayingPath = file.absolutePath
                  voicePlayer.play(file) { activePlayingPath = null }
                }
              },
              onOpenImage = { msg.mediaId?.let(onOpenImagePreview) },
              onOpenVideo = { msg.mediaId?.let(onOpenVideoPlayer) },
              onOpenDocument = {
                msg.mediaId?.let { id ->
                  onOpenDocumentPreview(id, msg.mediaFileName ?: "document.pdf")
                }
              },
              onRetry = { onRetryMessage(msg.localId) },
            )
          }
        }
      }
    }
  }

  if (showAttachSheet) {
    ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("Share Attachment", style = MaterialTheme.typography.titleLarge)
        Text(
          "Limits: Images 10 MB • Voice 20 MB • Documents 20 MB • Videos 50 MB",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AttachmentOptionRow(
          icon = Icons.Filled.Image,
          title = "Photo (JPG, PNG, WEBP)",
          onClick = {
            showAttachSheet = false
            imagePicker.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
        )
        AttachmentOptionRow(
          icon = Icons.Filled.Videocam,
          title = "Video (MP4, MOV)",
          onClick = {
            showAttachSheet = false
            videoPicker.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
          },
        )
        AttachmentOptionRow(
          icon = Icons.Filled.Description,
          title = "PDF Document",
          onClick = {
            showAttachSheet = false
            documentPicker.launch(arrayOf("application/pdf"))
          },
        )
        Spacer(Modifier.height(12.dp))
      }
    }
  }
}

@Composable
private fun AttachmentOptionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  onClick: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().clickable { onClick() },
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.width(12.dp))
      Text(title, style = MaterialTheme.typography.titleSmall)
    }
  }
}

@Composable
private fun MessageBubbleRow(
  msg: CachedMessageEntity,
  isMine: Boolean,
  isGroup: Boolean,
  mediaFile: File?,
  uploadFraction: Float?,
  isPlayingVoice: Boolean,
  onToggleVoice: (File) -> Unit,
  onOpenImage: () -> Unit,
  onOpenVideo: () -> Unit,
  onOpenDocument: () -> Unit,
  onRetry: () -> Unit,
) {
  if (msg.messageType == "system") {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
      ) {
        Text(
          text = msg.content,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }
    return
  }

  val alignment = if (isMine) Alignment.End else Alignment.Start
  val bubbleColor =
    if (isMine) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
  val textColor =
    if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

  Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
    Surface(
      color = bubbleColor,
      contentColor = textColor,
      shape =
        RoundedCornerShape(
          topStart = 18.dp,
          topEnd = 18.dp,
          bottomStart = if (isMine) 18.dp else 4.dp,
          bottomEnd = if (isMine) 4.dp else 18.dp,
        ),
      modifier = Modifier.widthIn(max = 310.dp).testTag("msg_bubble_${msg.localId}"),
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        if (isGroup && !isMine) {
          Text(
            text = msg.senderId,
            style = MaterialTheme.typography.labelSmall,
            color = CyanPrimary,
            fontWeight = FontWeight.Bold,
          )
          Spacer(Modifier.height(4.dp))
        }

        when (msg.messageType) {
          "image" -> {
            if (mediaFile != null && mediaFile.exists()) {
              AsyncImage(
                model = mediaFile,
                contentDescription = "Shared image",
                contentScale = ContentScale.Crop,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenImage() },
              )
            } else {
              Text("[Image: ${msg.mediaFileName ?: "Loading..."}]")
            }
            if (msg.content.isNotBlank()) {
              Spacer(Modifier.height(6.dp))
              Text(msg.content, style = MaterialTheme.typography.bodyMedium)
            }
          }
          "video" -> {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                  .clickable { onOpenVideo() }
                  .padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Filled.PlayArrow, contentDescription = "Play video")
              Spacer(Modifier.width(8.dp))
              Column {
                Text(
                  msg.mediaFileName ?: "Video attachment",
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                )
                Text("Tap to play video", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
          "voice" -> {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                  .clickable(enabled = mediaFile != null) { mediaFile?.let(onToggleVoice) }
                  .padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                if (isPlayingVoice) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play voice message",
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text =
                  if (isPlayingVoice) "Playing voice..."
                  else "Voice note (${(msg.mediaDurationMs / 1000).coerceAtLeast(1)}s)",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
          "document" -> {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                  .clickable { onOpenDocument() }
                  .padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Filled.Description, contentDescription = "Open PDF")
              Spacer(Modifier.width(8.dp))
              Column {
                Text(
                  msg.mediaFileName ?: "Document.pdf",
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                )
                Text("Tap to preview PDF", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
          else -> {
            Text(text = msg.content, style = MaterialTheme.typography.bodyLarge)
          }
        }

        if (uploadFraction != null) {
          Spacer(Modifier.height(6.dp))
          LinearProgressIndicator(progress = { uploadFraction }, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(4.dp))
        Row(
          modifier = Modifier.align(Alignment.End),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (msg.sequenceNumber > 0) {
            Text(
              text = "#${msg.sequenceNumber}",
              style = MaterialTheme.typography.labelSmall,
              color = textColor.copy(alpha = 0.65f),
            )
          }
          when (msg.localStatus) {
            "SERVER_CONFIRMED" ->
              Icon(
                Icons.Filled.Check,
                contentDescription = "Confirmed by server",
                modifier = Modifier.size(14.dp),
                tint = CyanPrimary,
              )
            "QUEUED", "SENDING" ->
              Icon(
                Icons.Filled.Schedule,
                contentDescription = "Pending delivery",
                modifier = Modifier.size(14.dp),
                tint = textColor.copy(alpha = 0.7f),
              )
            "FAILED" -> {
              Row(
                modifier = Modifier.clickable { onRetry() }.testTag("retry_msg_${msg.localId}"),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Filled.Error,
                  contentDescription = "Delivery failed",
                  modifier = Modifier.size(14.dp),
                  tint = StatusError,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                  "Failed • Tap to retry",
                  style = MaterialTheme.typography.labelSmall,
                  color = StatusError,
                )
              }
            }
          }
        }
      }
    }
  }
}
