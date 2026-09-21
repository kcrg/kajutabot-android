package com.tryniecki.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline + Startup Profile generator tailored to KajutaBot.
 *
 * Startup and post-startup CUJs are intentionally captured separately. Only the returning-user
 * launcher path is included in the Startup Profile, keeping DEX layout optimization focused on
 * cold startup. Navigation, Compose screens and text input still land in the Baseline Profile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val packageName: String
        get() = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not passed as instrumentation runner arg")

    @Before
    fun prepareAppState() {
        KajutaBotJourneys.prepareAuthenticatedSession(packageName)
    }

    @Test
    fun startup() {
        rule.collect(
            packageName = packageName,
            includeInStartupProfile = true,
            maxIterations = 10,
            stableIterations = 3,
            outputFilePrefix = "startup",
        ) {
            KajutaBotJourneys.run { launchAuthenticatedApp(packageName) }
        }
    }

    @Test
    fun criticalUserJourneys() {
        rule.collect(
            packageName = packageName,
            includeInStartupProfile = false,
            maxIterations = 10,
            stableIterations = 3,
            outputFilePrefix = "critical-user-journeys",
        ) {
            KajutaBotJourneys.run {
                launchAuthenticatedApp(packageName)
                runCriticalUserJourneys()
            }
        }
    }
}
