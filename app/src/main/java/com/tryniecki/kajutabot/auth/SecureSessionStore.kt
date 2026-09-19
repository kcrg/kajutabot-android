package com.tryniecki.kajutabot.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import org.json.JSONObject
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
 * The complete session is one encrypted preference value and one synchronous commit.
 * The old two-file layout is read for migration on the next successful token rotation.
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
        val payload = JSONObject()
            .put(KEY_ACCESS, session.accessToken)
            .put(KEY_REFRESH, session.refreshToken)
            .put(KEY_ACCESS_EXP, session.accessTokenExpiresAtUtc)
            .put(KEY_REFRESH_EXP, session.refreshTokenExpiresAtUtc)
            .put(KEY_USER_ID, session.user.discordUserId)
            .put(KEY_USERNAME, session.user.username)
            .put(KEY_DISPLAY_NAME, session.user.displayName)
            .put(KEY_AVATAR, session.user.avatarUrl)
            .toString()
        val encrypted = encrypt(payload)
        check(secretPrefs.edit()
            .putString(KEY_SESSION, encrypted)
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .commit()) { "Could not persist encrypted session" }
    }

    override fun load(): UserSession? {
        secretPrefs.getString(KEY_SESSION, null)?.let { encrypted ->
            val payload = JSONObject(requireNotNull(decrypt(encrypted)))
            return UserSession(
                accessToken = payload.getString(KEY_ACCESS),
                accessTokenExpiresAtUtc = payload.getString(KEY_ACCESS_EXP),
                refreshToken = payload.getString(KEY_REFRESH),
                refreshTokenExpiresAtUtc = payload.getString(KEY_REFRESH_EXP),
                user = AuthUserResponse(
                    discordUserId = payload.getString(KEY_USER_ID),
                    username = payload.getString(KEY_USERNAME),
                    displayName = payload.getString(KEY_DISPLAY_NAME),
                    avatarUrl = if (payload.isNull(KEY_AVATAR)) null else payload.getString(KEY_AVATAR),
                ),
            )
        }
        // Legacy layout, retained only to read sessions from installed older versions.
        val encAccess = secretPrefs.getString(KEY_ACCESS, null)
        val encRefresh = secretPrefs.getString(KEY_REFRESH, null)
        if (encAccess == null && encRefresh == null && !metaPrefs.contains(KEY_USER_ID)) return null
        check(encAccess != null && encRefresh != null) { "Incomplete encrypted session" }
        val accessExp = requireNotNull(metaPrefs.getString(KEY_ACCESS_EXP, null))
        val refreshExp = requireNotNull(metaPrefs.getString(KEY_REFRESH_EXP, null))
        val userId = requireNotNull(metaPrefs.getString(KEY_USER_ID, null))
        val username = requireNotNull(metaPrefs.getString(KEY_USERNAME, null))
        val displayName = requireNotNull(metaPrefs.getString(KEY_DISPLAY_NAME, null))
        return try {
            val access = requireNotNull(decrypt(encAccess))
            val refresh = requireNotNull(decrypt(encRefresh))
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
        } catch (e: Exception) {
            throw IllegalStateException("Cannot decrypt stored session", e)
        }
    }

    override fun clear() {
        check(secretPrefs.edit().clear().commit()) { "Could not clear encrypted session" }
        check(metaPrefs.edit().clear().commit()) { "Could not clear session metadata" }
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
        private const val KEY_SESSION = "session_record"

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
