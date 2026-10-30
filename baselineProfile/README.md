# Baseline / Startup Profiles (AndroidX)

The app ships an AndroidX `:baselineprofile` generator with three journeys:
`startup()` (Startup Profile + Baseline Profile), `criticalUserJourneys()`
(every screen reachable in a live session) and `isolatedScreens()` (the rest).
App `build.gradle.kts` has `saveInSrc = true` and
`automaticGenerationDuringBuild = false` for reproducible local release builds.

Do not commit an ART profile dumped from a device: it has R8-obfuscated names
that match nothing at build time.

`isolatedScreens()` covers login, access gates, onboarding, shared links and the
confirmation dialog through `BaselineProfileScreensActivity` with no-op
callbacks, because in a live session they need signing out or act on the real
bot. Test-only code lives in `app/src/baselineProfileTarget/`, which
`app/build.gradle.kts` adds to the `nonMinifiedRelease` and `benchmarkRelease`
variants only.

Journeys may run against a real session and only navigate, scroll and dismiss.
Never add a tap on a track, a playback control or a confirm button, and never
tap scrolling content after a swipe: a list still settling moves the target.

Connect one Android 13+ device with a reachable backend. An existing session and
its server selection are reused; a clean install signs in as a guest. Gradle
uninstalls the app after the run, so keep it (and the session) installed with
the property below:

```powershell
.\gradlew.bat :app:generateReleaseBaselineProfile "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
adb shell cmd locale set-app-locales com.tryniecki.kajutabot --user current --locales '""'
adb uninstall com.tryniecki.baselineprofile
```

The second command undoes the English app language set for stable selectors.

Generated files with `saveInSrc = true` live under:
`app/src/release/generated/baselineProfiles/` (profile filename/path may vary
with the plugin version and source-set variant).

Confirm the output contains human-readable symbols from this application's
packages, not app-wide R8 obfuscated names, then commit the generated files.
The profile generator targets `nonMinifiedRelease`, built without R8; the regular
release build uses R8 to map the profile to its optimized code.

```powershell
.\gradlew.bat :app:assembleRelease
```

When regenerating, smoke-test the release flow and measure startup on physical
hardware. Baseline profiles improve ART compilation/startup; they are not an
alternative to R8 and cannot be validated only by counting rules.

Do not use `Collect-BaselineProfile.ps1`: it still dumps the ART profile from the
device into `app/src/main/baseline-prof.txt`, the obfuscated output described above.
