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
