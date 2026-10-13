# Idempotencja mutacji kolejki w Androidzie

## Kontrakt

`QueueMutationCoordinator.run` generuje UUID raz dla logicznej operacji, wewnątrz
istniejącej blokady guilda. Repozytoria przekazują go do Retrofit jako
`Idempotency-Key`. Klucz nie powstaje w interceptorze ani przy każdej próbie HTTP.
Nowe kliknięcie, również dodające ten sam utwór lub wykonujące kolejny skip, otrzymuje
nowy UUID. `ExpectedQueueVersion` nadal chroni kolejkę przed konfliktami użytkowników.

Nagłówek jest wymaganym argumentem wszystkich 11 dostępnych metod API:
enqueue utworów/playlist, enqueue ulubionych, skip, stop, repeat, remove, move, swap,
clear oraz włączenie/wyłączenie radia. `PlayerRepository` i `FavoritesRepository`
przekazują ten sam przechwycony klucz w całej operacji. Interfejs ma metodę move;
aktualny `PlayerRepository` nie udostępnia jej jeszcze jako akcji. Klient nie ma
endpointu enqueue Jellyfin. Odczyty GET i modyfikacje samej listy ulubionych nie
otrzymują nagłówka mutacji kolejki.

Istniejące ponowienie callbacku przez `SessionManager` po 401 i odświeżeniu sesji
zachowuje UUID, payload i wcześniejsze `ExpectedQueueVersion`; zmienia się token
Authorization. Powtórzenie tego samego żądania przez transport również zachowuje
nagłówek już utworzonego żądania. Backend wiąże operację z autoryzowaną tożsamością,
guildem i rodzajem mutacji, a nie z krótkotrwałym access tokenem.

## Błędy i granice

Po błędzie HTTP lub transportu koordynator uzgadnia bieżący stan przez GET i zwraca
błąd do UI. Nie dodano automatycznego retry mutacji po timeout, 503 ani
`idempotency_in_progress`. Udany GET nie dowodzi, że wcześniejsza operacja nie może
jeszcze zakończyć się w Workerze. Zachowano istniejącą serializację operacji guilda
oraz sprawdzanie wersji przy przyjmowaniu snapshotów.

| Kod | Zachowanie UI |
| --- | --- |
| `idempotency_in_progress` | Prośba o poczekanie na aktualizację kolejki. |
| `idempotency_key_conflict` | Informacja o zmienionej operacji i konieczności odświeżenia kolejki. |
| `worker_ownership_lost`, `worker_not_ready` | Dotychczasowy komunikat niedostępności serwera. |
| `queue_version_conflict` | Dotychczasowy komunikat konfliktu kolejki. |

Komunikaty mają zasoby polskie i angielskie. Nie zmienia się UUID i payloadu w trakcie
retry, aby ukryć konflikt klucza. UUID nie jest sekretem; zmiana nie dodaje logowania
kluczy ani danych uwierzytelniających. Przechowywanie tokenów w Android Keystore
pozostaje bez zmian.

Klucz żyje w pamięci aktualnej operacji. Nie dodano trwałej kolejki pending operations
ani odtwarzania niepewnego żądania po restarcie aplikacji. Przyszła funkcja takiego
retry musi zapisywać razem klucz, tożsamość, trasę i oryginalny payload oraz przestrzegać
retencji receiptów serwera (domyślnie 7 dni). Nowa akcja po błędzie jest nową decyzją,
a nie automatycznym odtworzeniem starej operacji.

## Wdrożenie i weryfikacja

Najpierw wdroż zgodny Control API, wszystkie Workery i migrację PostgreSQL rejestru
operacji; następnie klienta. Starszy backend przyjmuje dodatkowy nagłówek, lecz nie
zapewnia deduplikacji. Naprawa opóźnienia sprawdzania uprawnień po bezczynności jest
zmianą backendu i nie zależy od tej aktualizacji Androida.

Weryfikacja lokalna 13.10.2026:

```powershell
.\gradlew.bat --offline :api:test :app:testDebugUnitTest :app:assembleDebug
```

Build zakończył się sukcesem: 22 testy modułu API i 171 testów aplikacji,
0 błędów i 0 pominiętych. MockWebServer sprawdza nagłówek wszystkich 11 metod,
13 wywołań repozytoriów z różnymi UUID (w tym celowe powtórzenie utworu/skip),
retry 401 z identycznym kluczem i body oraz GET po 503 bez ponownego POST.
Test komunikatów sprawdza cztery nowe kody błędów.

Raporty: `api/build/reports/tests/test/index.html` oraz
`app/build/reports/tests/testDebugUnitTest/index.html`. APK testowy:
`app/build/outputs/apk/debug/app-debug.apk`. Nie uruchamiano testów na urządzeniu ani
produkcji i nie przygotowywano podpisanej wersji do dystrybucji. Gradle zgłasza
ostrzeżenie o przestarzałym użyciu `sun.misc.Unsafe` przez zależność DataStore.
