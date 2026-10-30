package com.tryniecki.baselineprofile

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

private const val DEFAULT_TIMEOUT_MS = 60_000L
private const val SHORT_TIMEOUT_MS = 3_000L
private const val SETUP_ACTIVITY = ".BaselineProfileSetupActivity"
private const val SETUP_ACTION = "com.tryniecki.kajutabot.action.BASELINE_PROFILE_SETUP"
private const val SETUP_ERROR_PREFIX = "BASELINE_PROFILE_SETUP_ERROR: "
private const val SETUP_SUCCESS_MARKER = "BASELINE_PROFILE_SETUP_OK"
private const val SCREENS_ACTIVITY = ".BaselineProfileScreensActivity"
private const val SCREENS_EXTRA = "screen"
private const val ONBOARDING_SCREEN = "onboarding"
private const val ONBOARDING_PAGE_SWIPES = 5

// Screen name to a text proving it rendered, null for screens with no stable text.
private val ISOLATED_SCREENS = listOf(
    "login" to null,
    "access-checking" to null,
    "no-access" to "No access",
    "access-error" to "Could not check access",
    ONBOARDING_SCREEN to null,
    "shared-loading" to "Shared link",
    "shared-failed" to "Shared link",
    "dialog" to "Clear the queue?",
)

internal object KajutaBotJourneys {

    /**
     * Prepares a stable returning-user state outside BaselineProfileRule.collect.
     *
     * The setup activity exists only in benchmark target variants and persists guest/onboarding
     * state before collection starts from a stopped process.
     */
    fun prepareAuthenticatedSession(packageName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = device()
        val appInfo = instrumentation.context.packageManager.getApplicationInfo(packageName, 0)
        check(appInfo.isProfileableByShell) {
            "The $packageName variant used by Baseline Profile is not profileable-by-shell. " +
                "The generator must target nonMinifiedRelease created by the Baseline Profile Gradle Plugin."
        }

        device.pressHome()
        // Baseline Profile journeys use deterministic English selectors so they do not depend
        // on the benchmark device's system language. Baseline Profile collection requires API 33+.
        device.executeShellCommand(
            "cmd locale set-app-locales $packageName --user current --locales en",
        )
        instrumentation.context.startActivity(
            Intent(SETUP_ACTION).apply {
                setClassName(packageName, "$packageName$SETUP_ACTIVITY")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )

        val deadline = SystemClock.uptimeMillis() + DEFAULT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.hasObject(By.text(SETUP_SUCCESS_MARKER)) ||
                device.hasObject(By.desc(SETUP_SUCCESS_MARKER))
            ) {
                // Setup only persists auth/onboarding state. Stop the app before collection so
                // the profiled startup always begins from a process-dead state.
                device.executeShellCommand("am force-stop $packageName")
                device.pressHome()
                return
            }

            device.findObject(By.textStartsWith(SETUP_ERROR_PREFIX))?.let { error ->
                error(error.text ?: "Unknown Baseline Profile setup error.")
            }
            device.findObject(By.descStartsWith(SETUP_ERROR_PREFIX))?.let { error ->
                error(error.contentDescription ?: "Unknown Baseline Profile setup error.")
            }

            SystemClock.sleep(100)
        }

        error(
            "Failed to prepare KajutaBot for profiling within ${DEFAULT_TIMEOUT_MS} ms. " +
                "profileableByShell=${appInfo.isProfileableByShell}, " +
                "foregroundPackage=${device.currentPackageName ?: "<none>"}."
        )
    }

    fun MacrobenchmarkScope.launchAuthenticatedApp(packageName: String) {
        pressHome()
        startActivityAndWait()
        device().waitForObject(By.text("Player"), DEFAULT_TIMEOUT_MS)
    }

    /**
     * Covers every screen reachable in a live session without mutating playback/queue state.
     *
     * Only navigation, scrolling and dismissing are allowed here: this may run against a real
     * session, so never tap a track, a playback control or a dialog's confirm button. Never tap
     * scrolling content after a swipe either: a list that is still settling moves the target,
     * and the tap lands on whatever took its place. Fixed bars and FABs are safe.
     */
    fun runCriticalUserJourneys() {
        val device = device()

        // The volume sheet is disabled until the bot is connected to a voice channel. Opened
        // before the first swipe, while the Player content cannot move under the tap.
        device.findObject(By.desc("KajutaBot volume in Discord"))?.takeIf { it.isEnabled }?.let { volume ->
            volume.click()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }

        // Player, including the queue below the fold.
        swipeContent(device, down = false)
        swipeContent(device, down = true)

        // The Jellyfin tab only exists for accounts with a linked server.
        if (device.hasObject(By.text("Jellyfin"))) {
            device.clickAndWait(By.text("Jellyfin"))
            swipeContent(device, down = false)
            swipeContent(device, down = true)
        }

        // Player -> Favorites -> History, with a representative list gesture on each.
        device.clickAndWait(By.text("Favorites"))
        swipeContent(device, down = false)
        swipeContent(device, down = true)

        device.clickAndWait(By.text("History"))
        swipeContent(device, down = false)
        swipeContent(device, down = true)

        device.clickAndWait(By.text("More"))

        // Back to Player and exercise both full-screen player detail routes.
        device.clickAndWait(By.text("Player"))

        device.clickAndWait(By.desc("Change server and voice channel"))
        device.waitForObject(By.text("Server and channel"))
        device.pressBack()
        device.waitForObject(By.text("Player"))

        device.clickAndWait(By.desc("Search"))

        // Exercise Compose text input/search-state code without depending on a live search result.
        device.waitForObject(By.clazz("android.widget.EditText")).apply {
            click()
            setText("Daft Punk")
        }
        device.waitForIdle()

        // First Back closes IME, second Back closes Search and profiles the real system-back path.
        device.pressBack()
        SystemClock.sleep(200)
        if (device.hasObject(By.clazz("android.widget.EditText"))) {
            device.pressBack()
        }
        device.waitForObject(By.text("Player"))
    }

    /** Opens the screens hosted by BaselineProfileScreensActivity, one launch per screen. */
    fun MacrobenchmarkScope.runIsolatedScreens(packageName: String) {
        val device = device()
        ISOLATED_SCREENS.forEach { (screen, marker) ->
            pressHome()
            startActivityAndWait { intent ->
                intent.setClassName(packageName, "$packageName$SCREENS_ACTIVITY")
                intent.putExtra(SCREENS_EXTRA, screen)
            }
            if (marker != null) device.waitForObject(By.text(marker))
            device.waitForIdle()
            if (screen == ONBOARDING_SCREEN) {
                repeat(ONBOARDING_PAGE_SWIPES) {
                    device.swipe(
                        (device.displayWidth * 0.85f).toInt(),
                        device.displayHeight / 2,
                        (device.displayWidth * 0.15f).toInt(),
                        device.displayHeight / 2,
                        12,
                    )
                    device.waitForIdle()
                }
            }
        }
    }

    private fun UiDevice.clickAndWait(selector: BySelector) {
        waitForObject(selector).click()
        waitForIdle()
    }

    private fun UiDevice.waitForObject(
        selector: BySelector,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): UiObject2 = wait(Until.findObject(selector), timeoutMs)
        ?: error("Element $selector was not found within ${timeoutMs} ms")

    private fun UiDevice.scrollUntilVisible(selector: BySelector): UiObject2 {
        findObject(selector)?.let { return it }
        repeat(5) {
            swipeContent(this, down = false)
            wait(Until.findObject(selector), SHORT_TIMEOUT_MS)?.let { return it }
        }
        error("Element $selector was not found after scrolling")
    }

    private fun swipeContent(device: UiDevice, down: Boolean) {
        val x = device.displayWidth / 2
        val top = (device.displayHeight * 0.32f).toInt()
        val bottom = (device.displayHeight * 0.78f).toInt()
        if (down) {
            device.swipe(x, top, x, bottom, 12)
        } else {
            device.swipe(x, bottom, x, top, 12)
        }
        device.waitForIdle()
    }

    private fun device(): UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
}
