# KajutaBot Android - Agent Guide

## Project purpose

This repository contains the native Android client for KajutaBot. The goal is a small, production-oriented Android application written in Kotlin and Jetpack Compose that controls the existing KajutaBot backend.

The project is intentionally kept simple while the Android architecture is being learned. Prefer small, understandable changes over introducing abstractions pre-emptively.

## Current modules

- `:app` - Android application and UI.
- `:api` - pure Kotlin/JVM client for the KajutaBot Control API. It must not depend on Android SDK, Compose, Activities, Context, DataStore, or other Android-only APIs.

Dependency direction:

```text
:app -> :api
```

`:api` must never depend on `:app`.

Do not add more Gradle modules unless there is a concrete need. In particular, do not create `domain`, `data`, `core`, or per-feature modules just to satisfy an architecture pattern.

## Android baseline

- Kotlin
- Jetpack Compose / Material 3
- `minSdk = 29`
- `targetSdk = 37`
- `compileSdk = 37`
- Gradle Kotlin DSL
- Version catalog: `gradle/libs.versions.toml`

The current `minSdk` is intentional. Do not change SDK levels without being asked.

## Planned application architecture

For UI features prefer:

```text
Composable Screen
    -> Action
ViewModel
    -> StateFlow<UiState>
Composable Screen
```

Use immutable `UiState` data classes and unidirectional data flow. Do not imitate MAUI/XAML by creating observable properties for every field.

Keep route-level concerns (ViewModel, lifecycle, navigation) outside reusable screen Composables where practical.

Do not introduce Hilt, repositories, Room, DataStore, Navigation, or other infrastructure until the feature being implemented actually needs it.

## API module

The backend source used to define this client is KajutaBot Control API v1. The API base path is:

```text
/api/v1/
```

`KajutaBotApiClientFactory.create(baseUrl, accessTokenProvider)` accepts the server root URL and appends `/api/v1/` itself.
`createAuth(baseUrl)` builds the anonymous `KajutaBotAuthApi` (exchange + refresh) without any Bearer interceptor.

The initial mobile API surface intentionally covers only the features expected by the Android client:

- Discord guilds available to the user
- voice channels
- queue state
- enqueue / remove / reorder / clear / skip / stop / repeat
- radio state
- search
- favorites

Do not expose cache administration, diagnostics, state-sync, queue-extension-key administration, user uploads, or Jellyfin endpoints unless the Android client starts using them.

### Queue concurrency

KajutaBot queue mutations use optimistic concurrency through queue `version` / `expectedVersion`. Preserve this behavior. Do not silently remove `expectedVersion` from request models or mutation calls.

When a mutation returns a new `QueueSnapshotResponse`, prefer that returned snapshot instead of immediately issuing another GET request.

### DTOs

API DTOs should mirror the server contracts and remain in `:api`. Do not mix UI-specific state or formatting into API DTOs.

Dates are currently represented as ISO-8601 `String` values in the API module. Introduce a date/time library only when the app actually needs date arithmetic.

Use `kotlinx.serialization` for JSON. `ignoreUnknownKeys = true` is deliberate so adding fields on the server does not immediately break older clients.

## Authentication security

The mobile app uses user-scoped Bearer auth only:

```text
Login -> Discord OAuth PKCE -> POST /api/v1/auth/discord/exchange
-> access JWT + rotating refresh token (encrypted in Keystore)
-> Authorization: Bearer <access-token>
-> POST /api/v1/auth/refresh (single-flight, anonymous client)
-> POST /api/v1/auth/logout
```

`KajutaBotApi` is the authenticated user API (`auth/me`, `auth/logout`, `users/me/*`,
`discord/guilds/{id}/voice-channels`, `guilds/*`, `search`). `KajutaBotAuthApi` is the
anonymous auth API (`auth/discord/exchange`, `auth/refresh`) and must never receive
a Bearer token.

The mobile client never sends its own `discordUserId` for self-scoped data; identity
comes from the JWT on the backend.

Never hardcode or commit server-side secrets in:

- Kotlin source
- `BuildConfig`
- Gradle files
- resources
- `local.properties`
- assets
- tests

`KajutaBotApiClientFactory` accepts an `accessTokenProvider: () -> String?` only to keep transport configuration isolated while `:api` stays independent from Android storage. It never accepts API keys or secrets.

Do not implement client-side HMAC delegation using the full Control API key; that would still require shipping the secret in the APK. Do not reintroduce `X-KajutaBot-Api-Key` in the mobile client, not even as a debug fallback.

## Networking rules

- Retrofit defines HTTP endpoints.
- OkHttp owns HTTP transport/interceptors.
- kotlinx.serialization owns JSON serialization.
- Use suspending Retrofit methods; do not introduce callbacks or RxJava.
- Keep URL construction inside Retrofit annotations rather than concatenating endpoint strings in UI code.
- Discord snowflake identifiers remain `String` in API DTOs to match the backend contract and avoid accidental numeric conversions.

## Code style and scope

- Prefer straightforward Kotlin over clever DSLs.
- Keep classes focused and names explicit.
- Use constructor injection when DI is introduced later.
- Avoid service locators and global mutable state.
- Avoid reflection-heavy libraries when a generated/static alternative exists.
- Do not add wrappers/interfaces whose only purpose is forwarding a call unchanged.
- Use cases should exist only when they contain actual application/business orchestration, not one-line repository forwarding.
- Keep changes narrowly scoped to the requested feature.

## Testing

Add tests for behavior and logic that can realistically regress. Do not add tests whose only purpose is checking package structure, module dependencies, DI registration, or other architecture trivia.

For future tests prefer the Kotlin/Android equivalents of the project's usual .NET stack:

- JUnit
- MockK when mocking is useful
- AssertK for assertions
- Turbine for Flow testing

Use fakes instead of mocks when they produce simpler tests.

## Release / performance direction

When release optimization work is requested, prefer Android-native mechanisms:

- R8 shrinking/optimization
- Baseline Profiles
- Startup Profiles
- Macrobenchmark
- Compose stability / avoiding unnecessary recomposition

Do not optimize prematurely and do not add benchmark/profile modules until requested.

## Backend contract source of truth

If backend source is available, treat these server contracts/routes as the source of truth rather than guessing request shapes:

- `KajutaBot.Contracts.Api.V1.*`
- `KajutaBot.Host.ControlApi/DependencyInjection/KajutaBotControlApiEndpointExtensions.cs`

When changing an API request/response model, verify it against the current backend contract first.

## Current application shell

The app currently has four top-level Material 3 destinations:

- Odtwarzacz
- Moje Audio
- Ulubione
- Więcej

Keep top-level navigation native and simple. `NavigationBar` is the phone navigation surface; do not replace it with a custom-drawn tab bar. The screens are currently UI shells and should be connected to real state incrementally.

Theme modes are `LIGHT`, `DARK`, and `NATIVE`. `NATIVE` uses Android dynamic colors (Material You) on Android 12+ and falls back to the KajutaBot palette while following the system light/dark mode on older supported devices.

UI colors for explicit light/dark KajutaBot modes are derived from the web client palette (`#0c0e10`, `#111417`, `#191c20`, `#b59aff`, purple/magenta accent family). Prefer Material 3 color roles over hardcoding these values inside individual Composables.

Tabler is the application icon language. The dependency is `com.composables:icons-tabler-outline-android`. Selected glyphs are exposed to feature screens through app-owned `kb_ic_*` VectorDrawable resources / mappings so UI code does not depend on third-party drawable names. Keep those glyphs visually aligned with Tabler Outline and prefer outline icons unless a selected/filled state has a concrete UX reason.
