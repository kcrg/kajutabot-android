# Baseline / Startup Profiles (AndroidX)

The app ships an AndroidX `:baselineprofile` generator with two journeys:
`startup()` (Startup Profile + Baseline Profile), and `criticalUserJourneys()`
(Baseline Profile only). App `build.gradle.kts` has `saveInSrc = true` and
`automaticGenerationDuringBuild = false` for reproducible local release builds.

**Important:** The old `app/src/main/baseline-prof.txt` came from a device ART
profile dump and contains R8-obfuscated method names. That file has been
archived under `optimization-notes/` and is **not** shipped. Generate a new,
unobfuscated profile before producing the final production release.

Connect one Android 13+ device (the old/manual scripts required API 34; the
AndroidX generator supports API 33+). With a reachable backend/guest session:

```powershell
.\gradlew.bat :app:generateReleaseBaselineProfile
```

Generated files with `saveInSrc = true` live under:
`app/src/release/generated/baselineProfiles/` (profile filename/path may vary
with the plugin version and source-set variant).

Confirm the output contains human-readable symbols from this application's
packages, not app-wide R8 obfuscated names, then commit the generated files.
The profile generator targets the non-minified benchmark variant; the regular
release build uses R8 to map the profile to its optimized code.

```powershell
.\gradlew.bat :app:assembleRelease
```

When regenerating, smoke-test the release flow and measure startup on physical
hardware. Baseline profiles improve ART compilation/startup; they are not an
alternative to R8 and cannot be validated only by counting rules.

The two old PowerShell entry points have been converted to safe wrappers:
`Prepare-BaselineProfile.ps1` gives instructions, and
`Collect-BaselineProfile.ps1` invokes the official generator.
