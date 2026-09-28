# KajutaBot Baseline Profile

Generate the Release Baseline + Startup Profile on a connected Android 13+ device:

```powershell
.\gradlew.bat :app:generateReleaseBaselineProfile "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile"
```

The Baseline Profile Gradle Plugin builds the target app as `nonMinifiedRelease`: non-debuggable,
non-minified and profileable-by-shell. A setup Activity included only in benchmark target variants
prepares a persisted guest session and onboarding state. Collection then launches the app from a
stopped process.
