# Lokalna głośność Qumi

Okrągły przycisk Tabler Volume obok odtwarzacza otwiera Material 3 ModalBottomSheet z poziomym suwakiem 0–200%, krok 1. Przycisk jest widoczny wyłącznie dla sesji Discord z potwierdzoną rejestracją helpera (`installed=true`); stan nieznany, brak instalacji i gość ukrywają przycisk. Offline nie oznacza braku instalacji.

Arkusz korzysta z nieprzezroczystego koloru surfaceContainerLow motywu. Odtwarzacz nie jest rozmywany ani zaciemniany (przezroczysty scrim); suwak korzysta z koloru primary i pozostałych kolorów Material. Otwarcie nie zmienia stosu nawigacji ani konfiguracji dolnego paska. Przycisk otwierający suwak ma wysokość ButtonDefaults.MinHeight, obramowanie i kolor jak pozostałe przyciski obrysowane.

Arkusz używa standardowych animacji i gestów ModalBottomSheet: przeciągnięcie w dół, Wstecz lub dotknięcie poza arkuszem go zamyka. Nie ma tytułu ani podtytułu; opis zakresu funkcji pozostaje dostępny dla TalkBack. Procent znajduje się pod suwakiem, a wskaźnik Material 3 Expressive obok procentu. Wskaźnik pojawia się dopiero po 100 ms oczekiwania na completion bieżącej komendy; szybki sukces go nie pokazuje. Każda kolejna wysłana intencja ma własne opóźnienie. Completion, błąd i rozłączenie anulują opóźnienie i usuwają wskaźnik; event nie zastępuje completion. Przy problemie pojawia się jeden krótki komunikat. Stałe miejsca na wskaźnik i komunikat zapobiegają przesuwaniu suwaka i procentu podczas wysyłania.

Suwak steruje odsłuchem zalogowanego użytkownika w Discord Desktop na komputerze z Windows 11 i helperem. Jest niezależny od wybranego serwera oraz globalnej głośności odtwarzania.

Android korzysta z istniejącego połączenia PlaybackHub i konfiguracji `API_BASE_URL`. `GetLocalVolumeState()` pobiera snapshot przy każdym połączeniu; `LocalVolumeStateChanged(state)` dostarcza dalsze aktualizacje, bez pollingu. `SetLocalVolume(Int)` czeka na completion, z timeoutem klienta 10 s. Samo completion nie aktualizuje obserwowanej wartości. Token sesji Qumi trafia do nagłówka Bearer przy negotiate i WebSocket; reconnect pobiera aktualny token przez SessionManager.

LocalVolumeController należy do ViewModelu sesji i nie tworzy połączeń. Lokalny licznik eventów chroni snapshot przed nadpisaniem nowszego eventu, a generacja połączenia i tożsamość sesji odrzucają stare wyniki. Draft nie zmienia się pod wpływem eventów podczas gestu. Po zakończeniu gestu wysyłana jest jedna komenda; w czasie jej trwania przechowywana jest najwyżej ostatnia zatwierdzona intencja. Po błędzie następuje pojedynczy odczyt stanu bez retry komendy. Reconnect, utrata dostępności i logout usuwają oczekujące intencje. Rozłączenie zachowuje ostatnią obserwację jako nieaktualną, bez udawania braku instalacji.

Nie ma nowych endpointów HTTP, credentials helpera, połączeń z Discord RPC ani telemetrii. Repozytorium nie zawiera zweryfikowanego linku pobrania helpera, więc UI pokazuje instrukcję instalacji bez linku.

## Walidacja automatyczna

```powershell
.\gradlew.bat :api:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin
```

Testy jednostkowe obejmują stany helpera, gościa, granice wartości, coalescing, race snapshot/event, event przed completion, brak eventu przy niezmienionej wartości, timeout, reconnect, utratę dostępności, zmianę konta, stare API i mapowanie błędów. MockWebServer sprawdza rzeczywisty protokół JSON SignalR, brak argumentów Get, integer Set, Bearer dla obu żądań i oczekiwanie na completion. Testy PlayerRealtime sprawdzają współdzielenie połączenia, odbiór eventów podczas startu, niezależność od guild oraz nowy token i snapshot po reconnectcie.

Na urządzeniu lub emulatorze:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tryniecki.qumi.LocalVolumeSheetTest'
```

Testy UI sprawdzają otwarcie arkusza z przycisku, zamknięcie gestem i Wstecz, poziomy suwak bez nagłówka, procent pod suwakiem oraz stabilną pozycję elementów przy włączaniu i wyłączaniu wskaźnika. Pozostałe scenariusze obejmują brak sztucznej wartości dla gościa, blokadę nieaktualnego stanu, gest poziomy oraz dostępnościowe SetProgress i końcowe zatwierdzenie gestu. Testy kontrolera z czasem wirtualnym sprawdzają brak wskaźnika przy completion przed 100 ms, dokładny próg 100 ms, odrębne opóźnienia kolejnych komend oraz anulowanie wskaźnika przy błędzie i rozłączeniu.

## Sprawdzenie ręczne z API, Windows helperem i Discord Desktop

1. Zaloguj Androida, helper i Discord tym samym kontem. Uruchom helper i Discord Desktop, dołącz do kanału z Qumi. Sprawdź snapshot oraz ustawienia 0%, 100%, 200% i wartości pośredniej; procent powinien odpowiadać obserwacji Discorda.
2. Zmień głośność w Discord Desktop i na drugim kliencie tego konta. Android powinien się zaktualizować. W trakcie przeciągania event powinien zmieniać obserwację bez przestawiania uchwytu; po completion ma być widoczna rzeczywista wartość.
3. Zmień serwer w Androidzie. Stan lokalnej głośności powinien pozostać przypisany do konta. Drugie konto nie powinno otrzymać jego eventów.
4. Wyłącz helper, zamknij Discord i sprawdź przypadek helpera online bez dostępnego sterowania, także przy niezgodnym koncie/kanałach. UI powinno rozróżniać rejestrację, offline i niedostępność oraz pokazywać „—” przy null.
5. Odłącz sieć Androida podczas wysyłania i ustawiania kolejnej intencji. Suwak ma się wyłączyć; po reconnectcie ma być świeży snapshot, bez odtworzenia komend. Sprawdź też reconnect po wygaśnięciu JWT.
6. Wywołaj helper_busy z innego urządzenia oraz timeout helpera/RPC. Sprawdź pojedynczy komunikat, koniec stanu wysyłania i pojedynczą synchronizację bez retry Set.
7. Wyloguj się podczas snapshotu/komendy i zaloguj innym kontem. Stary stan, draft i opóźnione callbacki nie mogą przejść do nowej sesji. Sprawdź też informację dla gościa i starszego API bez metod local volume.
8. Sprawdź TalkBack (opis i procent, standardowe zwiększanie/zmniejszanie), poziome gesty, mały ekran i dużą czcionkę. Sprawdź otwieranie i zamykanie arkusza oraz opóźniony wskaźnik obok procentu podczas rzeczywistej komendy. Globalne sterowanie playbackiem i heartbeat powinny działać jak wcześniej.
