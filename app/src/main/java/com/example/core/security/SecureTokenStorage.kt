package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredSession(
  val accessToken: String,
  val refreshToken: String,
  val expiresAtMs: Long,
  val userId: String,
)

/**
 * Hardware-backed Android Keystore AES-256-GCM encrypted token storage.
 * Falls back cleanly to private SharedPreferences only when running under JVM/Robolectric
 * environments that do not implement the AndroidKeyStore provider.
 */
class SecureTokenStorage(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val keystoreAvailable: Boolean by lazy {
    runCatching {
      val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
      ks.load(null)
      true
    }.getOrDefault(false)
  }

  private fun getOrCreateAesKey(): SecretKey {
    val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
    ks.load(null)
    val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
    if (existing != null) return existing

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  private fun encrypt(plainText: String): String {
    if (!keystoreAvailable) {
      return "PLAIN:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey())
    val iv = cipher.iv
    val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
    val ctB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
    return "GCM:$ivB64:$ctB64"
  }

  private fun decrypt(encoded: String?): String? {
    if (encoded.isNullOrBlank()) return null
    return runCatching {
        when {
          encoded.startsWith("PLAIN:") -> {
            val bytes = Base64.decode(encoded.removePrefix("PLAIN:"), Base64.NO_WRAP)
            String(bytes, Charsets.UTF_8)
          }
          encoded.startsWith("GCM:") -> {
            val parts = encoded.split(":")
            if (parts.size != 3) return null
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ct = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
          }
          else -> null
        }
      }
      .getOrNull()
  }

  fun getOrCreateDeviceId(): String {
    val existing = decrypt(prefs.getString(KEY_DEVICE_ID, null))
    if (!existing.isNullOrBlank()) return existing
    val created = "dev_${UUID.randomUUID()}"
    prefs.edit().putString(KEY_DEVICE_ID, encrypt(created)).apply()
    return created
  }

  fun saveSession(session: StoredSession) {
    prefs
      .edit()
      .putString(KEY_ACCESS_TOKEN, encrypt(session.accessToken))
      .putString(KEY_REFRESH_TOKEN, encrypt(session.refreshToken))
      .putLong(KEY_EXPIRES_AT_MS, session.expiresAtMs)
      .putString(KEY_USER_ID, encrypt(session.userId))
      .apply()
  }

  fun getSession(): StoredSession? {
    val access = decrypt(prefs.getString(KEY_ACCESS_TOKEN, null)) ?: return null
    val refresh = decrypt(prefs.getString(KEY_REFRESH_TOKEN, null)) ?: return null
    val userId = decrypt(prefs.getString(KEY_USER_ID, null)) ?: return null
    val expiresAtMs = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)
    if (access.isBlank() || refresh.isBlank() || userId.isBlank()) return null
    return StoredSession(
      accessToken = access,
      refreshToken = refresh,
      expiresAtMs = expiresAtMs,
      userId = userId,
    )
  }

  fun clearSession() {
    prefs
      .edit()
      .remove(KEY_ACCESS_TOKEN)
      .remove(KEY_REFRESH_TOKEN)
      .remove(KEY_EXPIRES_AT_MS)
      .remove(KEY_USER_ID)
      .apply()
  }

  companion object {
    private const val PREFS_NAME = "connectme_secure_tokens"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "connectme_token_aes_key"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

    private const val KEY_ACCESS_TOKEN = "enc_access_token"
    private const val KEY_REFRESH_TOKEN = "enc_refresh_token"
    private const val KEY_EXPIRES_AT_MS = "expires_at_ms"
    private const val KEY_USER_ID = "enc_user_id"
    private const val KEY_DEVICE_ID = "enc_device_id"
  }
}
