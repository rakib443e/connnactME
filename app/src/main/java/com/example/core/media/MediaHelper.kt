package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import com.example.core.config.AppConfig
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ValidatedLocalMedia(
  val category: String, // image | video | voice | document
  val fileName: String,
  val mimeType: String,
  val sizeBytes: Long,
  val localFile: File,
)

sealed class MediaValidationResult {
  data class Valid(val media: ValidatedLocalMedia) : MediaValidationResult()

  data class Invalid(val reason: String) : MediaValidationResult()
}

class MediaHelper(private val context: Context) {
  private val mediaDir: File by lazy {
    File(context.filesDir, "connectme_media").apply { mkdirs() }
  }

  fun validateAndStageUri(uri: Uri, expectedCategory: String? = null): MediaValidationResult {
    return runCatching {
        val cr = context.contentResolver
        var fileName = "attachment_${System.currentTimeMillis()}"
        var sizeBytes = -1L

        cr.query(uri, null, null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
            if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
          }
        }

        val mimeType = cr.getType(uri) ?: guessMimeFromName(fileName)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val category = expectedCategory ?: inferCategory(ext, mimeType)

        if (category == null) {
          return MediaValidationResult.Invalid("Unsupported file type: .$ext ($mimeType)")
        }

        val allowedExt =
          when (category) {
            "image" -> AppConfig.ALLOWED_IMAGE_EXT
            "video" -> AppConfig.ALLOWED_VIDEO_EXT
            "voice" -> AppConfig.ALLOWED_VOICE_EXT
            "document" -> AppConfig.ALLOWED_DOC_EXT
            else -> emptySet()
          }

        if (ext.isNotBlank() && ext !in allowedExt) {
          return MediaValidationResult.Invalid(
            "Unsupported .$ext format for $category. Allowed: ${allowedExt.joinToString(", ")}"
          )
        }

        val stagedFile = File(mediaDir, "staged_${UUID.randomUUID()}_$fileName")
        cr.openInputStream(uri)?.use { input ->
          FileOutputStream(stagedFile).use { output -> input.copyTo(output) }
        } ?: return MediaValidationResult.Invalid("Unable to read selected file.")

        val actualSize = if (sizeBytes > 0) sizeBytes else stagedFile.length()
        val maxBytes = maxBytesForCategory(category)
        if (actualSize <= 0 || actualSize > maxBytes) {
          stagedFile.delete()
          val maxMb = maxBytes / (1024 * 1024)
          return MediaValidationResult.Invalid(
            "File size (${actualSize / (1024 * 1024)} MB) exceeds $maxMb MB limit for $category."
          )
        }

        MediaValidationResult.Valid(
          ValidatedLocalMedia(
            category = category,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = actualSize,
            localFile = stagedFile,
          )
        )
      }
      .getOrElse { MediaValidationResult.Invalid(it.message ?: "Failed to read media file.") }
  }

  fun validateLocalFile(file: File, category: String, mimeType: String): MediaValidationResult {
    if (!file.exists() || file.length() <= 0) {
      return MediaValidationResult.Invalid("Media file is missing or empty.")
    }
    val maxBytes = maxBytesForCategory(category)
    if (file.length() > maxBytes) {
      return MediaValidationResult.Invalid(
        "File exceeds ${maxBytes / (1024 * 1024)} MB limit for $category."
      )
    }
    return MediaValidationResult.Valid(
      ValidatedLocalMedia(
        category = category,
        fileName = file.name,
        mimeType = mimeType,
        sizeBytes = file.length(),
        localFile = file,
      )
    )
  }

  fun readBase64Chunks(file: File, chunkSizeBytes: Int = AppConfig.CHUNK_SIZE_BYTES): List<String> {
    val bytes = file.readBytes()
    if (bytes.isEmpty()) return emptyList()
    val chunks = mutableListOf<String>()
    var offset = 0
    while (offset < bytes.size) {
      val end = minOf(offset + chunkSizeBytes, bytes.size)
      val slice = bytes.copyOfRange(offset, end)
      chunks.add(Base64.encodeToString(slice, Base64.NO_WRAP))
      offset = end
    }
    return chunks
  }

  fun saveBase64MediaToCache(mediaId: String, fileName: String, base64Data: String): File {
    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val target = File(mediaDir, "${mediaId}_$safeName")
    target.writeBytes(bytes)
    return target
  }

  fun createVoiceRecordingFile(): File =
    File(mediaDir, "voice_${System.currentTimeMillis()}.m4a")

  private fun maxBytesForCategory(category: String): Long =
    when (category) {
      "image" -> AppConfig.MAX_IMAGE_BYTES
      "voice" -> AppConfig.MAX_VOICE_BYTES
      "video" -> AppConfig.MAX_VIDEO_BYTES
      "document" -> AppConfig.MAX_DOCUMENT_BYTES
      else -> 0L
    }

  private fun inferCategory(ext: String, mimeType: String): String? =
    when {
      ext in AppConfig.ALLOWED_IMAGE_EXT || mimeType.startsWith("image/") -> "image"
      ext in AppConfig.ALLOWED_VIDEO_EXT || mimeType.startsWith("video/") -> "video"
      ext in AppConfig.ALLOWED_VOICE_EXT || mimeType.startsWith("audio/") -> "voice"
      ext in AppConfig.ALLOWED_DOC_EXT || mimeType == "application/pdf" -> "document"
      else -> null
    }

  private fun guessMimeFromName(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
  }

  companion object {
    fun renderPdfPage(file: File, pageIndex: Int): Pair<Bitmap, Int>? {
      return runCatching {
          ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
              val count = renderer.pageCount
              if (count == 0 || pageIndex !in 0 until count) return null
              renderer.openPage(pageIndex).use { page ->
                val width = (page.width * 2).coerceIn(600, 1800)
                val height = (page.height * 2).coerceIn(800, 2600)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp to count
              }
            }
          }
        }
        .getOrNull()
    }
  }
}

class VoiceRecorderController(private val context: Context) {
  private var recorder: MediaRecorder? = null
  private var outputFile: File? = null
  private var startedAtMs: Long = 0L

  fun start(targetFile: File): Boolean {
    stopAndDiscard()
    return runCatching {
        @Suppress("DEPRECATION")
        val mr =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
          else MediaRecorder()
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setAudioEncodingBitRate(96000)
        mr.setAudioSamplingRate(44100)
        mr.setOutputFile(targetFile.absolutePath)
        mr.prepare()
        mr.start()
        recorder = mr
        outputFile = targetFile
        startedAtMs = System.currentTimeMillis()
        true
      }
      .getOrElse {
        stopAndDiscard()
        false
      }
  }

  fun stopAndSave(): Pair<File, Long>? {
    val mr = recorder ?: return null
    val file = outputFile ?: return null
    val duration = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(500L)
    return runCatching {
        mr.stop()
        mr.release()
        recorder = null
        outputFile = null
        file to duration
      }
      .getOrElse {
        stopAndDiscard()
        null
      }
  }

  fun stopAndDiscard() {
    runCatching { recorder?.stop() }
    runCatching { recorder?.release() }
    recorder = null
    outputFile?.delete()
    outputFile = null
  }
}

class VoicePlayerController {
  private var player: MediaPlayer? = null
  var activeFilePath: String? = null
    private set

  fun play(file: File, onComplete: () -> Unit) {
    stop()
    runCatching {
      val mp = MediaPlayer()
      mp.setDataSource(file.absolutePath)
      mp.prepare()
      mp.setOnCompletionListener {
        stop()
        onComplete()
      }
      mp.start()
      player = mp
      activeFilePath = file.absolutePath
    }
  }

  fun stop() {
    runCatching { player?.stop() }
    runCatching { player?.release() }
    player = null
    activeFilePath = null
  }
}
