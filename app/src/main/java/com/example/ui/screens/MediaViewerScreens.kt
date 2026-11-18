package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.core.media.MediaHelper
import com.example.core.storage.CachedMessageEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewScreen(
  mediaId: String,
  fetchMedia: suspend (String) -> Result<File>,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  var file by remember { mutableStateOf<File?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var scale by remember { mutableFloatStateOf(1f) }

  LaunchedEffect(mediaId) {
    fetchMedia(mediaId).onSuccess { file = it }.onFailure { error = it.message }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Image Preview") },
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
      contentAlignment = Alignment.Center,
    ) {
      when {
        error != null ->
          ErrorStateCard(
            message = error ?: "Unable to load image",
            modifier = Modifier.padding(20.dp),
          )
        file == null -> LoadingStateView("Downloading authorized image...")
        else ->
          AsyncImage(
            model = file,
            contentDescription = "Full image preview",
            contentScale = ContentScale.Fit,
            modifier =
              Modifier.fillMaxSize()
                .pointerInput(Unit) {
                  detectTransformGestures { _, _, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                  }
                }
                .graphicsLayer(scaleX = scale, scaleY = scale),
          )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
  mediaId: String,
  fetchMedia: suspend (String) -> Result<File>,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  var file by remember { mutableStateOf<File?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(mediaId) {
    fetchMedia(mediaId).onSuccess { file = it }.onFailure { error = it.message }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Video Player") },
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
      contentAlignment = Alignment.Center,
    ) {
      when {
        error != null ->
          ErrorStateCard(
            message = error ?: "Unable to load video",
            modifier = Modifier.padding(20.dp),
          )
        file == null -> LoadingStateView("Downloading authorized video...")
        else -> {
          val videoFile = file!!
          AndroidView(
            factory = { ctx ->
              VideoView(ctx).apply {
                val controller = MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoPath(videoFile.absolutePath)
                setOnPreparedListener { mp ->
                  mp.isLooping = false
                  start()
                }
              }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPreviewScreen(
  mediaId: String,
  fileName: String,
  fetchMedia: suspend (String) -> Result<File>,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  var file by remember { mutableStateOf<File?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var pageIndex by remember { mutableIntStateOf(0) }
  var totalPages by remember { mutableIntStateOf(0) }
  var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(mediaId) {
    fetchMedia(mediaId).onSuccess { file = it }.onFailure { error = it.message }
  }

  LaunchedEffect(file, pageIndex) {
    val pdfFile = file ?: return@LaunchedEffect
    val rendered = withContext(Dispatchers.IO) { MediaHelper.renderPdfPage(pdfFile, pageIndex) }
    if (rendered != null) {
      renderedBitmap = rendered.first
      totalPages = rendered.second
    } else {
      error = "Unable to render PDF document."
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text(fileName) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    bottomBar = {
      if (totalPages > 1) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedButton(
              onClick = { if (pageIndex > 0) pageIndex-- },
              enabled = pageIndex > 0,
            ) {
              Text("Previous")
            }
            Text("Page ${pageIndex + 1} / $totalPages")
            Button(
              onClick = { if (pageIndex < totalPages - 1) pageIndex++ },
              enabled = pageIndex < totalPages - 1,
            ) {
              Text("Next")
            }
          }
        }
      }
    },
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.Center,
    ) {
      when {
        error != null ->
          ErrorStateCard(
            message = error ?: "Document load failed",
            modifier = Modifier.padding(20.dp),
          )
        renderedBitmap == null -> LoadingStateView("Rendering PDF document...")
        else ->
          Image(
            bitmap = renderedBitmap!!.asImageBitmap(),
            contentDescription = "PDF page ${pageIndex + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(12.dp),
          )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
  mediaMessages: List<CachedMessageEntity>,
  resolvedMediaFiles: Map<String, File>,
  onResolveMedia: (String) -> Unit,
  onOpenImage: (String) -> Unit,
  onOpenVideo: (String) -> Unit,
  onOpenDocument: (String, String) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Shared Media Gallery") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    if (mediaMessages.isEmpty()) {
      EmptyStateView(
        icon = Icons.Filled.Collections,
        title = "No shared media yet",
        subtitle = "Photos, videos, voice notes, and PDF documents shared in this conversation appear here.",
        modifier = Modifier.padding(padding),
      )
      return@Scaffold
    }

    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 110.dp),
      contentPadding = PaddingValues(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.fillMaxSize().padding(padding),
    ) {
      items(mediaMessages, key = { it.localId }) { item ->
        LaunchedEffect(item.mediaId) {
          item.mediaId?.let(onResolveMedia)
        }
        val localFile = item.mediaId?.let { resolvedMediaFiles[it] }
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(14.dp),
          modifier =
            Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).clickable {
              val mid = item.mediaId ?: return@clickable
              when (item.messageType) {
                "image" -> onOpenImage(mid)
                "video" -> onOpenVideo(mid)
                "document" -> onOpenDocument(mid, item.mediaFileName ?: "document.pdf")
              }
            },
        ) {
          when {
            item.messageType == "image" && localFile != null && localFile.exists() ->
              AsyncImage(
                model = localFile,
                contentDescription = item.mediaFileName ?: "Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            else ->
              Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Icon(
                  imageVector =
                    when (item.messageType) {
                      "video" -> Icons.Filled.PlayCircle
                      "voice" -> Icons.Filled.Audiotrack
                      else -> Icons.Filled.Description
                    },
                  contentDescription = item.messageType,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                  text = item.mediaFileName ?: item.messageType.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 2,
                )
              }
          }
        }
      }
    }
  }
}
