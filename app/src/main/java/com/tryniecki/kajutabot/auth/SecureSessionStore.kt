package com.tryniecki.kajutabot.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SessionStore {
    fun save(session: UserSession)
    fun load(): UserSession?
    fun clear()
}

/**
 * AES-256-GCM session storage backed by Android Keystore.
 *
 * Secrets (tokens) are encrypted with a Keystore key and stored in a private prefs file
 * excluded from backup. Non-secret metadata lives in a separate private prefs file.
 * No biometrics: background refresh must work without user prompt.
 */
class SecureSessionStore(context: Context) : SessionStore {
    private val appContext = context.applicationContext

    private val secretPrefs: SharedPreferences = appContext.getSharedPreferences(
        SECRET_PREFS,
        Context.MODE_PRIVATE,
    )
    private val metaPrefs: SharedPreferences = appContext.getSharedPreferences(
        META_PREFS,
        Context.MODE_PRIVATE,
    )

    override fun save(session: UserSession) {
        secretPrefs.edit()
            .putString(KEY_ACCESS, encrypt(session.accessToken))
            .putString(KEY_REFRESH, encrypt(session.refreshToken))
            .apply()
        metaPrefs.edit()
            .putString(KEY_ACCESS_EXP, session.accessTokenExpiresAtUtc)
            .putString(KEY_REFRESH_EXP, session.refreshTokenExpiresAtUtc)
            .putString(KEY_USER_ID, session.user.discordUserId)
            .putString(KEY_USERNAME, session.user.username)
            .putString(KEY_DISPLAY_NAME, session.user.displayName)
            .putString(KEY_AVATAR, session.user.avatarUrl)
            .apply()
    }

    override fun load(): UserSession? {
        val encAccess = secretPrefs.getString(KEY_ACCESS, null) ?: return null
        val encRefresh = secretPrefs.getString(KEY_REFRESH, null) ?: return null
        val accessExp = metaPrefs.getString(KEY_ACCESS_EXP, null) ?: return null
        val refreshExp = metaPrefs.getString(KEY_REFRESH_EXP, null) ?: return null
        val userId = metaPrefs.getString(KEY_USER_ID, null) ?: return null
        val username = metaPrefs.getString(KEY_USERNAME, null) ?: return null
        val displayName = metaPrefs.getString(KEY_DISPLAY_NAME, null) ?: return null
        return try {
            val access = decrypt(encAccess) ?: return null
            val refresh = decrypt(encRefresh) ?: return null
            UserSession(
                accessToken = access,
                accessTokenExpiresAtUtc = accessExp,
                refreshToken = refresh,
                refreshTokenExpiresAtUtc = refreshExp,
                user = AuthUserResponse(
                    discordUserId = userId,
                    username = username,
                    displayName = displayName,
                    avatarUrl = metaPrefs.getString(KEY_AVATAR, null),
                ),
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun clear() {
        secretPrefs.edit().clear().apply()
        metaPrefs.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? {
        return try {
            val combined = Base64.decode(stored, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return null
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv),
            )
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val SECRET_PREFS = "kajutabot_secure_session"
        const val META_PREFS = "kajutabot_secure_session_meta"
        private const val KEY_ALIAS = "kajutabot_session_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12

        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_ACCESS_EXP = "access_exp"
        private const val KEY_REFRESH_EXP = "refresh_exp"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR = "avatar_url"
    }
}
