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
private const val MAIN_ACTIVITY = ".MainActivity"
private const val SETUP_ACTION = "com.tryniecki.kajutabot.action.BASELINE_PROFILE_SETUP"
private const val SETUP_ERROR_PREFIX = "BASELINE_PROFILE_SETUP_ERROR: "
private const val SETUP_SUCCESS_MARKER = "BASELINE_PROFILE_SETUP_OK"

internal object KajutaBotJourneys {

    /**
     * Prepares a stable returning-user state outside BaselineProfileRule.collect.
     *
     * The generator starts the app's normal exported MainActivity with a dedicated setup action.
     * MainActivity accepts that action only when the installed APK is profileable-by-shell, which
     * is true for the Baseline Profile plugin's nonMinified target and false for a normal release.
     * This prepares persisted guest/onboarding state without a variant-only manifest component.
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
                setClassName(packageName, "$packageName$MAIN_ACTIVITY")
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
        device().waitForObject(By.desc("Player"), DEFAULT_TIMEOUT_MS)
    }

    /** Covers the most common code paths without mutating playback/queue state. */
    fun runCriticalUserJourneys() {
        val device = device()

        // Player -> Favorites, including a representative list gesture.
        device.clickAndWait(By.desc("Favorites"))
        swipeContent(device, down = false)
        swipeContent(device, down = true)

        // Favorites -> More -> libraries/contact detail routes.
        device.clickAndWait(By.desc("More"))
        device.scrollUntilVisible(By.text("Libraries used")).click()
        device.waitForObject(By.desc("Back"))
        device.pressBack()
        device.waitForObject(By.desc("More"))

        device.scrollUntilVisible(By.text("Contact")).click()
        device.waitForObject(By.desc("Back"))
        device.pressBack()
        device.waitForObject(By.desc("More"))

        // Back to Player and exercise both full-screen player detail routes.
        device.clickAndWait(By.desc("Player"))

        device.clickAndWait(By.desc("Change server and voice channel"))
        device.waitForObject(By.text("Server and channel"))
        device.pressBack()
        device.waitForObject(By.desc("Player"))

        device.clickAndWait(By.desc("Add track"))
        device.waitForObject(By.text("Add to queue"))

        // Exercise Compose text input/search-state code without depending on a live search result.
        device.waitForObject(By.clazz("android.widget.EditText")).apply {
            click()
            setText("Daft Punk")
        }
        device.waitForIdle()

        // First Back closes IME, second Back closes AddTrack and profiles the real system-back path.
        device.pressBack()
        SystemClock.sleep(200)
        if (device.hasObject(By.text("Add to queue"))) {
            device.pressBack()
        }
        device.waitForObject(By.desc("Player"))
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
