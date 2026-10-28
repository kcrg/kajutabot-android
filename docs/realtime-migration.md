# Realtime client migration

The Android client uses SignalRKore 0.9.16 (latest Maven Central release verified on 2026-10-24), Ktor 3.2.3 with its OkHttp engine, and generated kotlinx.serialization serializers. Kotlin 2.4.21, minimum SDK 29, Retrofit, and the existing application architecture are unchanged.

## Contracts and ownership

- Incoming: `QueueUpdated(QueueSnapshotResponse)`, `RealtimeHeartbeat(Long)`, `LocalVolumeStateChanged(LocalVolumeStateDto)`.
- Outgoing: `SubscribeGuild(String): Boolean`, `GetLocalVolumeState(): LocalVolumeStateDto`, `SetLocalVolume(Int): Unit`. The volume command still waits for server completion, validates 0..200, and never assumes an acknowledged command is an observed volume.
- Existing DTOs already had `@Serializable`; reuse their explicit generated serializers and existing JSON configuration. CamelCase names, enum strings, nullable/omitted fields, defaults, nested collections, timestamp strings and GUID strings retain their contracts. REST serialization was not rewritten.
- Native cold event flows are collected before connection startup. Collection and decoding run in the existing owner coroutines; there are no forwarding scopes, Rx subscriptions, reflection serializers or persistent callback registrations. Native flow backpressure preserves event order without unbounded queues.
- The Ktor facade shares the existing OkHttp TLS configuration and connection pool. Ktor owns its dispatcher; shutdown closes and cancels the facade without shutting down the REST dispatcher. All required WebSocket/SSE, timeout and JSON plugins are installed.

## Recovery and authentication

`PlayerRealtime` retains the existing UI/MediaSession owner arbitration, login identity checks, guild selection, diagnostics StateFlow, and REST recovery for missing snapshots. Native reconnect owns ordinary transport retries, using the previous bounded delay sequence (0, 1, 2, 5, 8 seconds, then at most 8 seconds plus 250 ms jitter) through prolonged outages. Successful reconnect restores the selected guild and refreshes local volume on the same client instance.

A connection-ID key prevents fast StateFlow transitions from losing resubscription. The existing outer lifecycle loop only handles startup/authentication failures or terminal closes; a five-second terminal-close watchdog distinguishes native 0.9.16's transient DISCONNECTED state from a close that does not enter native recovery. Mutations are never automatically replayed. Startup and outstanding invocations have 30-second bounds; disconnection cancels completion waits.

The suspend access-token provider calls the existing identity-bound `SessionManager` on every native attempt. An atomic shutdown guard fences both token retrieval and reconnect policy, including logout during refresh. Explicit stop is idempotent; coroutine cancellation is propagated. No credentials or hub payloads are logged. The repository has no Sentry SDK/telemetry abstraction to integrate; existing safe lifecycle diagnostics remain in place, without introducing another SDK.

## Dependencies and optimization

Removed Microsoft SignalR and its now-unused transitive RxJava/Gson dependencies, both Rx-to-coroutine adapters, all eight application SignalR keep/dontwarn directives (39 lines), and obsolete Microsoft/Rx/Gson baseline-profile entries. No replacement keep or dontwarn directives were added. OkHttp's existing 5.5.1 BOM aligns the SSE dependency brought by Ktor, fixing an actual R8 missing-class conflict with SSE 4.12. AndroidX Test's annotation dependency is confined to androidTest.

## Verification

```powershell
.\gradlew.bat :api:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease -PplaybackTestBaseUrl=https://api.kajuta.tryniecki.eu
.\gradlew.bat :app:assembleReleaseAndroidTest -PandroidTestBuildType=release
```

The production smoke tests are explicit opt-ins. They create an ephemeral guest session, subscribe to the demo guild and receive queue/heartbeat events. They do not modify playback or volume. The Android test additionally breaks only its own TCP connection and reconnects with new guest credentials. No local backend fixture is required or retained.

The JVM transport tests use MockWebServer for protocol completions, all three event contracts, concurrent subscriptions/start calls, 503 recovery, actual TCP loss with token replacement, invocation cancellation, and stop during token refresh. Existing deterministic PlayerRealtime/SessionManager tests cover owner arbitration, guild switching, recovery, logout and account changes.
