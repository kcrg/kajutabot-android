package com.tryniecki.kajutabot.data.preferences

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tryniecki.kajutabot.api.model.auth.SessionType
import com.tryniecki.kajutabot.auth.UserSession
import com.tryniecki.kajutabot.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private const val DATA_STORE_NAME = "kajutabot_user_preferences"

private val Context.userPreferencesDataStore by preferencesDataStore(
    name = DATA_STORE_NAME,
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "kajutabot_preferences"),
            SharedPreferencesMigration(context, "kajutabot_selection"),
            SharedPreferencesMigration(context, "kajutabot_onboarding"),
            SharedPreferencesMigration(context, "kajutabot_favorites"),
            SharedPreferencesMigration(context, "kajutabot_search_history"),
        )
    },
)

data class GuildSelection(
    val guildId: String? = null,
    val voiceChannelId: String? = null,
)

/**
 * Single source of truth for user-controlled, non-sensitive preferences.
 *
 * Sensitive OAuth/session material deliberately stays in SecureSessionStore.
 * Legacy SharedPreferences files are migrated by DataStore before first read.
 */
class UserPreferencesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.userPreferencesDataStore
    private val preferences = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val themeMode: Flow<ThemeMode> = preferences.map { preferences ->
        preferences[THEME_MODE]
            ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.NATIVE
    }

    val guildSelection: Flow<GuildSelection> = preferences.map { preferences ->
        GuildSelection(
            guildId = preferences[GUILD_ID],
            voiceChannelId = preferences[VOICE_CHANNEL_ID],
        )
    }

    val searchHistory: Flow<List<String>> = preferences.map { preferences ->
        decodeSearchHistory(preferences[SEARCH_HISTORY])
    }

    suspend fun currentGuildSelection(): GuildSelection = guildSelection.first()

    suspend fun setGuildId(guildId: String?) {
        editSafely { preferences ->
            if (guildId == null) preferences.remove(GUILD_ID) else preferences[GUILD_ID] = guildId
        }
    }

    suspend fun setVoiceChannelId(channelId: String?) {
        editSafely { preferences ->
            if (channelId == null) preferences.remove(VOICE_CHANNEL_ID)
            else preferences[VOICE_CHANNEL_ID] = channelId
        }
    }

    suspend fun setGuildSelection(guildId: String?, channelId: String?) {
        editSafely { preferences ->
            if (guildId == null) preferences.remove(GUILD_ID) else preferences[GUILD_ID] = guildId
            if (channelId == null) preferences.remove(VOICE_CHANNEL_ID)
            else preferences[VOICE_CHANNEL_ID] = channelId
        }
    }

    suspend fun clearGuildSelection() {
        editSafely { preferences ->
            preferences.remove(GUILD_ID)
            preferences.remove(VOICE_CHANNEL_ID)
        }
    }

    fun onboardingCompleted(sessionType: SessionType, discordUserId: String): Flow<Boolean> =
        preferences.map { preferences ->
            preferences[booleanPreferencesKey(onboardingCompletedKey(sessionType, discordUserId))] ?: false
        }

    suspend fun setOnboardingCompleted(
        sessionType: SessionType,
        discordUserId: String,
        completed: Boolean = true,
    ) {
        val key = booleanPreferencesKey(onboardingCompletedKey(sessionType, discordUserId))
        editSafely { preferences -> preferences[key] = completed }
    }

    fun favoritesShuffle(ownerKey: String): Flow<Boolean> = preferences.map { preferences ->
        preferences[booleanPreferencesKey(favoritesShuffleKey(ownerKey))] ?: false
    }

    suspend fun setFavoritesShuffle(ownerKey: String, enabled: Boolean) {
        editSafely { preferences ->
            preferences[booleanPreferencesKey(favoritesShuffleKey(ownerKey))] = enabled
        }
    }

    suspend fun addSearchHistory(query: String): List<String> {
        val updated = updateSearchHistory(searchHistory.first(), query)
        editSafely { preferences -> preferences[SEARCH_HISTORY] = Json.encodeToString(updated) }
        return updated
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        editSafely { preferences -> preferences[THEME_MODE] = mode.name }
    }

    private suspend fun editSafely(transform: suspend (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(transform)
        } catch (_: IOException) {
            // Preference persistence must not turn an otherwise successful user/network
            // action into an unrelated UI failure. A later write can persist the latest state.
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GUILD_ID = stringPreferencesKey("guild_id")
        val VOICE_CHANNEL_ID = stringPreferencesKey("voice_channel_id")
        val SEARCH_HISTORY = stringPreferencesKey("entries")
    }
}

internal fun favoritesPreferenceOwnerKey(session: UserSession): String = when (session.sessionType) {
    SessionType.DISCORD -> session.user.discordUserId
    SessionType.GUEST -> "guest"
}

internal fun onboardingIdentityKey(sessionType: SessionType, discordUserId: String): String =
    if (sessionType == SessionType.GUEST) "guest" else discordUserId

private fun onboardingCompletedKey(sessionType: SessionType, discordUserId: String): String =
    "completed_${onboardingIdentityKey(sessionType, discordUserId)}"

private fun favoritesShuffleKey(ownerKey: String): String = "shuffle_$ownerKey"

internal const val MAX_SEARCH_HISTORY_ENTRIES = 5

internal fun updateSearchHistory(
    current: List<String>,
    query: String,
    maxEntries: Int = MAX_SEARCH_HISTORY_ENTRIES,
): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || maxEntries <= 0) return current.take(maxEntries.coerceAtLeast(0))

    return buildList(maxEntries) {
        add(trimmed)
        current.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it.equals(trimmed, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(maxEntries - 1)
            .forEach(::add)
    }
}

private fun decodeSearchHistory(encoded: String?): List<String> = encoded
    ?.let { value -> runCatching { Json.decodeFromString<List<String>>(value) }.getOrNull() }
    ?.take(MAX_SEARCH_HISTORY_ENTRIES)
    .orEmpty()
