# Przepisy

Lokalna książka kucharska dla Androida 13+. Importuje pojedyncze przepisy z `mojewypieki.com`, `alaantkoweblw.pl`, `rozkoszny.pl` i `aniagotuje.pl`, zapisuje tekst oraz zdjęcie główne na urządzeniu i skaluje rozpoznane ilości składników.

## Konfiguracja tego komputera

Środowisko testowe jest już przygotowane:

- Android Studio i dołączony JBR 21 (projekt kompiluje kod do JVM 17),
- Android SDK 33 i 36, Build Tools 36 oraz Command-line Tools,
- obraz `system-images;android-33;google_apis;x86_64`,
- widoczny emulator `Przepisy_API_33` z Androidem 13, 4 GiB RAM, akceleracją WHPX i GPU hosta.

Uruchom emulator z PowerShella w katalogu projektu:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-emulator.ps1
```

Skrypt ustawia również DNS emulatora i wyłącza pozostawiony po testach tryb samolotowy.

## Testy automatyczne

Pełna bramka (JVM, Android Lint, debug APK, 26 testów na emulatorze, release APK i limit 25 MiB):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-tests.ps1
```

Testy urządzeniowe modyfikują dane debugowej aplikacji. Przed ich uruchomieniem wyeksportuj ważne przepisy albo traktuj emulator wyłącznie jako środowisko testowe.

## Uruchomienie aplikacji

1. Zainstaluj bieżące Android Studio z JDK 17+ oraz Android SDK 36.
2. Otwórz katalog projektu i pozwól Gradle pobrać zależności.
3. Uruchom konfigurację `app` na emulatorze lub telefonie z Androidem 13+.

Z linii poleceń w PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat assembleRelease verifyReleaseApkSize
```

Testy z `src/androidTest` wymagają uruchomionego emulatora lub telefonu:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Najprostsza instalacja aktualnego debug APK na emulatorze:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install-debug.ps1
```

## Test ręczny

1. Uruchom emulator i zainstaluj debug APK powyższymi skryptami.
2. Sprawdź import zgłoszonych układów stron:
   - `https://alaantkoweblw.pl/blog/przepis/bananowe-paczki-z-2-skladnikow/`,
   - `https://mojewypieki.com/przepis/migdalowa-tarta-z-agrestem`,
   - `https://alaantkoweblw.pl/blog/przepis/biszkopty-z-dwoch-skladnikow/`,
   - `https://mojewypieki.com/przepis/pieguski`,
   - `https://mojewypieki.com/przepis/sernik-z-jablkami-i-kruszonka#goog_rewarded`,
   - `https://alaantkoweblw.pl/blog/przepis/klopsiki-w-kokosowym-sosie-curry/`,
   - `https://alaantkoweblw.pl/blog/przepis/babeczki-z-kaszy-manny/`,
   - `https://alaantkoweblw.pl/blog/przepis/cukiniowe-penne-z-sosem-beszamelowym/`,
   - `https://alaantkoweblw.pl/blog/przepis/makaron-z-sosem-bolognese/`,
   - `https://alaantkoweblw.pl/blog/przepis/spaghetti-napoli-alaantkoweblw/`,
   - `https://alaantkoweblw.pl/blog/przepis/piers-z-indyka-w-curry-i-mleku-kokosowym/`,
   - `https://alaantkoweblw.pl/blog/przepis/szynka-gotowana/`,
   - `https://alaantkoweblw.pl/blog/przepis/baklazan-nadziewany-indykiem-i-kasza-jeczmienna/`,
   - `https://alaantkoweblw.pl/blog/przepis/pasta-z-awokado-i-gruszki/`,
   - `https://alaantkoweblw.pl/blog/przepis/pasta-kanapkowa-pomidorowy-hummus/`,
   - `https://www.rozkoszny.pl/po-prostu-ciasto-z-rabarbarem/`,
   - `https://www.rozkoszny.pl/listkujace-maslane-buleczki/`,
   - `https://www.rozkoszny.pl/kopytka-w-sosie-smietanowym-z-kurkami-i-wedzonym-twarogiem/`,
   - `https://aniagotuje.pl/przepis/ciasto-jogurtowe`,
   - `https://aniagotuje.pl/przepis/zupa-krem-buraczkowa`,
   - `https://aniagotuje.pl/przepis/proste-ciasto-dzien-i-noc`,
   - `https://aniagotuje.pl/przepis/leczo-z-miesem-mielonym`.
3. Przed zapisem sprawdź tytuł, osobne sekcje składników i ostatni krok wykonania. W Biszkoptach składniki nie mogą występować ponownie jako kroki. W Leczo wszystkie ilości mają znajdować się przed nazwami i reagować na mnożnik. W Kopytkach pierwsza sekcja ma nazywać się `Kopytka`, a jej nazwa nie może być składnikiem. Podczas przewijania przycisk `Zapisz offline` ma pozostawać stale widoczny na dole.
4. Zapisz przynajmniej dwa przepisy. Oznacz starszy gwiazdką i sprawdź, czy przechodzi na początek listy; wyłącz i włącz aplikację, aby potwierdzić trwałość oznaczenia.
5. Otwórz szczegóły: tytuł ma być tylko w górnym pasku, `Opis` ma być domyślnie zwinięty, a pole własnego mnożnika widoczne dopiero po wybraniu `Inne`.
6. W Biszkoptach wybierz `2×`: `3 jajka`, `4 łyżki` i opcjonalna `1 łyżka` powinny zmienić się odpowiednio na `6 jajek`, `8 łyżek` i `2 łyżki`.
7. Dla przepisu z wykrytą formą wybierz `Przelicz na inną formę`. Forma bazowa ma być jednym, nieedytowalnym wierszem, np. `Forma bazowa: ⌀ 24 cm`.
8. Wybierz `Lista`, następnie `Nowa`, podaj nazwę i wymiary oraz zapisz formę. Po zapisie panel edycji ma się zwinąć. Wybierz istniejący wpis: sam wybór nie otwiera pól; dopiero `Dostosuj` obok `Anuluj` pokazuje `Zapisz formę` i `Usuń formę`. Obie akcje ponownie zwijają panel.
9. Sprawdź wyszukiwanie po nazwie oraz składniku, np. `jajka`.
10. Po zapisaniu importu zaznacz istniejący tag pod przyciskiem `Otwórz przepis`, a polem `Nowy tag` utwórz kolejny. W szczegółach wybierz `Zmień` obok źródła, dodaj tagi `Obiad` i `Do zrobienia`, zapisz je i sprawdź, czy są widoczne jako etykiety obok źródła. Po usunięciu ostatniego przypisania tag ma zniknąć z listy dostępnych tagów.
11. Wróć do biblioteki. Pod wyszukiwaniem wybieraj tagi z chmury i sprawdź, czy lista zawiera tylko przepisy mające wszystkie zaznaczone tagi.
12. Ustaw mnożnik dający niecałkowite wyniki. Gramy, dekagramy i mililitry mają być pełnymi liczbami; jajka, łyżki i inne miary mają być zwykłymi ułamkami mieszanymi zaokrąglonymi do 1/8, np. `1 3/8`.
13. Udostępnij aplikacji ten sam URL ponownie; na dole ma pojawić się `Zastąp zapisany przepis`, a oznaczenie ulubionego i tagi mają zostać zachowane.
14. Na ekranie `Kopia` wyeksportuj `.przepisy.zip`, a następnie zaimportuj ten sam plik; aplikacja ma zgłosić liczbę scalonych przepisów, a tagi mają pozostać przypisane.
15. Włącz tryb samolotowy, zamknij aplikację i uruchom ją ponownie. Lista może potrzebować kilku sekund na pierwszy odczyt bazy; następnie mają działać zdjęcia, wyszukiwanie, szczegóły, edycja, tagi i skalowanie.
16. Po próbie offline ponownie uruchom skrypt startowy podany wyżej albo wyłącz tryb samolotowy w emulatorze.

## Prywatne podpisywanie release APK

Klucza nie wolno zapisywać w repozytorium. Utwórz go narzędziem `keytool` w prywatnym katalogu poza projektem, na przykład:

```powershell
keytool -genkeypair -v -keystore D:\private\przepisy-release.jks -alias przepisy -keyalg RSA -keysize 4096 -validity 10000
```

Skopiuj `keystore.properties.example` jako ignorowany `keystore.properties`, wpisz bezwzględną ścieżkę do JKS i hasła. Plik JKS oraz dane dostępowe przechowuj osobno w co najmniej jednej zaszyfrowanej kopii; utrata klucza uniemożliwi podpisanie aktualizacji tą samą tożsamością. Bez konfiguracji `assembleRelease` tworzy niepodpisany APK, który nadal można sprawdzić zadaniem `verifyReleaseApkSize`.

Artefakty trafiają do `app/build/outputs/apk/debug/` i `app/build/outputs/apk/release/`.

## Architektura

- Jedna aktywność i UI Jetpack Compose z Navigation 3.
- `ViewModel` + `StateFlow`; lokalne repozytorium jest jedynym źródłem danych.
- Room przechowuje przepisy, sekcje, tagi i indeks FTS; zdjęcia trafiają do prywatnego katalogu aplikacji.
- Nazwane formy użytkownika są przechowywane lokalnie jako wersjonowane ustawienie aplikacji.
- Import preferuje `Recipe` JSON-LD, a następnie używa osobnego parsera DOM dla każdej domeny.
- Kopia `.przepisy.zip` zawiera wersjonowany JSON i zdjęcia, nigdy surową bazę SQLite.

Import wymaga sieci. Po zapisie lista, wyszukiwanie, zdjęcie, edycja, instrukcja i skalowanie działają offline. Aplikacja nie ma kont, backendu, analityki ani automatycznej synchronizacji.

## Aktualizacje i wydania

Na ekranie **Kopia**, pod eksportem/importem, przycisk **Sprawdź aktualizacje**
sprawdza najnowsze wydanie. **Pobierz aktualizację** otwiera APK w przeglądarce.
Otwórz pobrany plik i potwierdź instalację; Android może poprosić o zezwolenie
na instalowanie aplikacji z tej przeglądarki. Nie odinstalowuj poprzedniej wersji.
Pierwszą wersję z tym mechanizmem trzeba zainstalować ręcznie.

Repozytorium: https://github.com/rozgladacz/przepisy_mariki
Najnowszy APK: https://github.com/rozgladacz/przepisy_mariki/releases/latest/download/Przepisy.apk

Workflow `.github/workflows/release.yml` po pushu na `main` uruchamia testy JVM,
Lint, budowanie APK, kontrolę 25 MiB i testy na emulatorze Androida 13.
Dopiero potem osobny job otrzymuje sekrety podpisywania, buduje APK i sprawdza
jego identyfikator, wersję oraz odcisk certyfikatu. Publikuje szkic z plikami
`Przepisy.apk`, `update.json` i `SHA256SUMS`, następnie oznacza wydanie jako latest.
Zmiany tylko README, docs lub LICENSE nie uruchamiają procesu. Można też
uruchomić workflow ręcznie na main. Pull requesty przechodzą testy bez publikacji
i bez dostępu do klucza.

Numer wydania jest wyliczany z `version.properties` i `github.run_number`:
do ostatniego członu versionName i versionCode dodajemy numer uruchomienia minus 1.
Pierwsze uruchomienie to 1.3.3 (11); kolejne numery mogą mieć przerwy po PR-ach
lub nieudanych buildach. Ponowienie tego samego uruchomienia używa tej samej wersji.
Nie resetuj licznika przez tworzenie workflow od nowa ani nie zmniejszaj bazy wersji.
Publikacja dodatkowo odrzuca versionCode nie większy od już opublikowanych.
Przy zmianie głównej/pobocznej wersji ustaw odpowiednią bazę w version.properties.
Opis bieżących zmian znajduje się w `release-notes.md` i należy go aktualizować
razem ze zmianami aplikacji.

Procesy publikacji są kolejkowane. Jeśli main przesunie się przed publikacją,
starszy build pomija wydanie (lub pozostawia szkic), aby latest wskazywało aktualny kod.
Przy serii szybkich pushy GitHub może zastąpić oczekujące uruchomienie nowszym.
Poprawka wadliwego wydania powinna otrzymać nowy versionCode; cofnięcie samego latest
nie obniży wersji już zainstalowanej na telefonach.

Sekrety repozytorium: SIGNING_KEY_BASE64, SIGNING_STORE_PASSWORD,
SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD. Klucz i właściwości nie należą do repo.
Obecne wydania 1.3.2 i wcześniejsze używają historycznego lokalnego klucza Android Debug.
Dla zachowania istniejących instalacji CI używa dokładnie tego samego certyfikatu:
`51b3d764ea5e63e0b94d410adef716ba803526a4f629a32f04ce0d41f387893c`.
To nie jest klucz debugowy generowany na runnerze. Jego utrata uniemożliwi dalsze
aktualizacje istniejących instalacji; zachowaj prywatną kopię zapasową.
Wersja debug ma osobny identyfikator i nie może zostać zastąpiona publicznym APK.

Weryfikacja skryptu wersjonowania:
`python -m unittest discover -s scripts -p "test_*.py"`.

## Licencja

Kod aplikacji jest udostępniany na licencji GNU GPL-3.0-only. Pełny tekst znajduje się w pliku LICENSE.
