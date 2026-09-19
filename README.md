# kajutabot-android
Native Kotlin client app for KajutaBot - Discord music bot.

## Konfiguracja Android

Publiczne wartości konfiguracyjne (NIE są sekretami):

```properties
KAJUTABOT_API_BASE_URL=https://api.kajuta.tryniecki.eu
KAJUTABOT_DISCORD_CLIENT_ID=<twoje-discord-application-id>
```

Gdzie skonfigurować:

- `gradle.properties` (lokalnie / per maszyna), albo
- `-PKAJUTABOT_API_BASE_URL=... -PKAJUTABOT_DISCORD_CLIENT_ID=...` przy wywołaniu Gradle, albo
- zmienne środowiskowe / CI properties.

Wartości trafiają do `BuildConfig`:

- `BuildConfig.KAJUTABOT_API_BASE_URL`
- `BuildConfig.KAJUTABOT_DISCORD_CLIENT_ID`

Brak `DISCORD_CLIENT_ID` nie przerywa builda, ale ekran logowania pokaże błąd konfiguracji
zamiast otwierać OAuth URL.

### Redirect URI

Finalny redirect URI wynika z konfiguracji:

```text
discord-<DISCORD_CLIENT_ID>:/authorize/callback
```

Przykład dla `DISCORD_CLIENT_ID=1234567890`:

```text
discord-1234567890:/authorize/callback
```

Ten DOKŁADNIE ten sam URI trzeba dodać w dwóch miejscach:

1. Discord Developer Portal -> Twoja aplikacja -> OAuth2 -> Redirects:
   `discord-<DISCORD_CLIENT_ID>:/authorize/callback`
2. Backend KajutaBot:
   `DiscordOAuth__AllowedRedirectUris__0=discord-<DISCORD_CLIENT_ID>:/authorize/callback`
   (kolejne wpisy `__1`, `__2`, … jeśli potrzeba więcej URI).

Android przechwytuje ten URI przez `VIEW`/`DEFAULT`/`BROWSABLE` intent-filter
(scheme ustawiany przez manifest placeholder `${discordScheme}`).

### Sekrety

NIGDY nie umieszczaj w repo Android:

- `ControlApi:AdminApiKey` / `KajutaBotApi:AdminApiKey`
- `X-KajutaBot-Api-Key`
- Discord ClientSecret
- InternalApiKey / StateSyncApiKey / HMAC secretów

Aplikacja używa wyłącznie user-scoped JWT (`Authorization: Bearer`) uzyskanego
przez Discord OAuth + `POST /api/v1/auth/discord/exchange` i odświeżanego przez
`POST /api/v1/auth/refresh`.

## Auth flow

```text
Login
-> PKCE + state + pending store
-> Discord Custom Tab
-> callback discord-<id>:/authorize/callback
-> ControlApi exchange (code + verifier + redirectUri)
-> encrypted session (Keystore AES-GCM)
-> Bearer API
-> proactive + single-flight refresh
```

## Build

```bash
./gradlew test
./gradlew assembleDebug
```

The checked-in Gradle Wrapper downloads Gradle 9.7.1 and verifies its distribution checksum. CI runs the API and app unit tests, Android Lint, a debug build and an **unsigned** release build. No release signing credentials are required for those checks.

## Release signing

To sign a release, set all four environment variables before invoking `:app:assembleRelease`:

| Variable | Value |
|---|---|
| `KAJUTABOT_RELEASE_STORE_FILE` | Path to a keystore outside this repository |
| `KAJUTABOT_RELEASE_STORE_PASSWORD` | Keystore password |
| `KAJUTABOT_RELEASE_KEY_ALIAS` | Signing key alias |
| `KAJUTABOT_RELEASE_KEY_PASSWORD` | Signing key password |

With none of these variables set, `assembleRelease` builds an unsigned APK and logs that fact. With only some set, configuration fails. Keep credentials in a local secret store or the CI secret manager; do not put them in project files, command-line arguments, or logs. The unsigned CI artifact is for compilation checks and must not be distributed as a signed release.

`clean-repo.cmd` removes only generated Gradle and module build directories. It preserves `.idea`, including shelves, `local.properties`, and other user files.

## Logout semantics

The current backend `/api/v1/auth/logout` requires a valid Bearer access token. The app refreshes an expired access token before logout and reports server-confirmed logout only after this endpoint succeeds. If refresh is already invalid, it ends the local session and reports that remote revocation was not confirmed. Fully reliable revocation in this case needs a backend endpoint that accepts the rotating refresh token as the credential, revokes its session, and is rate-limited and idempotent.
