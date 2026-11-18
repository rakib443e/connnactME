package com.example.core.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "connectme_settings")

object AppConfig {
  const val APP_NAME = "ConnectMe"
  const val ANDROID_PACKAGE = "com.connectme.messaging"
  const val ROOT_DRIVE_FOLDER_ID = "1YFrpi1h1L747bTrfexoutVX5zUAT0kKj"
  const val ROOT_DRIVE_FOLDER_NAME = "ConnectMe_Database"

  // Replace or configure at runtime in Settings -> Backend Endpoint after deploying /backend/Code.gs
  const val DEFAULT_APPS_SCRIPT_URL = ""

  // Polling intervals (milliseconds)
  const val POLL_ACTIVE_CHAT_MS = 3_000L
  const val POLL_CONVERSATION_LIST_MS = 5_000L
  const val POLL_OTHER_ACTIVE_MS = 15_000L
  const val POLL_MAX_BACKOFF_MS = 60_000L

  // Upload limits (bytes)
  const val MAX_IMAGE_BYTES = 10L * 1024L * 1024L
  const val MAX_VOICE_BYTES = 20L * 1024L * 1024L
  const val MAX_VIDEO_BYTES = 50L * 1024L * 1024L
  const val MAX_DOCUMENT_BYTES = 20L * 1024L * 1024L
  const val CHUNK_SIZE_BYTES = 2 * 1024 * 1024 // 2 MB raw per chunk (< 4 MB base64 for GAS)

  val ALLOWED_IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
  val ALLOWED_VIDEO_EXT = setOf("mp4", "mov")
  val ALLOWED_VOICE_EXT = setOf("m4a", "aac", "mp3", "wav")
  val ALLOWED_DOC_EXT = setOf("pdf")
}

class AppSettingsStore(private val context: Context) {
  private object Keys {
    val BACKEND_URL = stringPreferencesKey("apps_script_web_app_url")
    val THEME_PREF = stringPreferencesKey("theme_preference")
    val DEFAULT_COUNTRY_CODE = stringPreferencesKey("default_country_code")
  }

  val backendUrlFlow: Flow<String> =
    context.settingsDataStore.data.map { prefs ->
      prefs[Keys.BACKEND_URL] ?: AppConfig.DEFAULT_APPS_SCRIPT_URL
    }

  val themePreferenceFlow: Flow<ThemePreference> =
    context.settingsDataStore.data.map { prefs ->
      val raw = prefs[Keys.THEME_PREF] ?: ThemePreference.SYSTEM.name
      runCatching { ThemePreference.valueOf(raw) }.getOrDefault(ThemePreference.SYSTEM)
    }

  val defaultCountryCodeFlow: Flow<String> =
    context.settingsDataStore.data.map { prefs ->
      prefs[Keys.DEFAULT_COUNTRY_CODE] ?: "+1"
    }

  suspend fun getBackendUrl(): String = backendUrlFlow.first()

  suspend fun setBackendUrl(url: String) {
    context.settingsDataStore.edit { prefs ->
      prefs[Keys.BACKEND_URL] = url.trim()
    }
  }

  suspend fun setThemePreference(preference: ThemePreference) {
    context.settingsDataStore.edit { prefs ->
      prefs[Keys.THEME_PREF] = preference.name
    }
  }

  suspend fun setDefaultCountryCode(code: String) {
    context.settingsDataStore.edit { prefs ->
      prefs[Keys.DEFAULT_COUNTRY_CODE] = code.trim()
    }
  }
}
