package com.tryniecki.qumi.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `concurrent submissions preserve both queries`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            temporaryFolder.root.resolve("history.preferences_pb")
        }
        val repository = UserPreferencesRepository(store)
        repository.addSearchHistory("seed")

        listOf("alpha", "beta").map { query ->
            async { repository.addSearchHistory(query) }
        }.awaitAll()

        assertEquals(setOf("alpha", "beta", "seed"), repository.searchHistory.first().toSet())
    }

    @Test fun `unrelated preference writes and repeated first query do not emit history`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            temporaryFolder.root.resolve("history.preferences_pb")
        }
        val repository = UserPreferencesRepository(store)
        repository.searchHistory.test {
            assertEquals(emptyList<String>(), awaitItem())
            repository.setGuildSelection("guild", "voice")
            runCurrent()
            expectNoEvents()
            repository.addSearchHistory("track")
            assertEquals(listOf("track"), awaitItem())
            repository.addSearchHistory("track")
            repository.setVoiceChannelId("another-voice")
            runCurrent()
            expectNoEvents()
        }
    }
}
