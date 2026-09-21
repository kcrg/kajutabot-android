# KajutaBot Baseline Profile

Generate the Release Baseline + Startup Profile on a connected Android 13+ device:

```powershell
.\gradlew.bat :app:generateReleaseBaselineProfile "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile"
```

The Baseline Profile Gradle Plugin builds the target app as `nonMinifiedRelease`: non-debuggable,
non-minified and profileable-by-shell. `MainActivity` accepts the internal setup action only in a
profileable-by-shell APK, prepares a persisted guest session/onboarding state, and then the actual
profile collection launches the app normally. No variant-only Activity or manifest source set is
required.
