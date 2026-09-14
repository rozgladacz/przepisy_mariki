# Historia wersji

## 1.3.2

- Naprawiono rozpoznawanie i skalowanie ilości zapisanych po nazwie składnika w nowszym układzie `aniagotuje.pl`.
- Pierwszy nagłówek sekcji wewnątrz głównej karty `rozkoszny.pl` nie jest już dodawany jako składnik.
- Dodano testy regresyjne oparte na układach przepisów „Leczo z mięsem mielonym” oraz „Kopytka w sosie śmietanowym z kurkami i wędzonym twarogiem”.

## 1.3.1

- Naprawiono import starszych wpisów Alaantkoweblw, w których zwykły akapit `Składniki:` poprzedza listę punktowaną.
- Dodano test regresyjny oparty na układzie przepisu „Pasta kanapkowa pomidorowy hummus”.

## 1.3.0

- Dodano import pojedynczych przepisów z `aniagotuje.pl`.
- Obsłużono nowszy układ z osobnymi krokami oraz starszy układ z instrukcją w jednym bloku.
- Import zachowuje sekcje składników, dodatkowe składniki, wydajność, zdjęcie i rozpoznany rozmiar formy.
- Dodano niezależne od sieci testy regresyjne obu wariantów strony.

## 1.2.2

- Naprawiono import starszych wpisów Alaantkoweblw z krótką instrukcją zapisaną w formie „mieszamy”, „nakładamy” i „posypujemy”.
- Dodano test regresyjny oparty na układzie przepisu „Pasta z awokado i gruszki”.

## 1.2.1

- Naprawiono ucinanie przepisów Moje Wypieki, gdy instrukcja zawierała pogrubiony fragment tekstu.
- Wyróżnienie jest teraz traktowane jako nagłówek tylko wtedy, gdy obejmuje cały akapit.
- Dodano test regresyjny oparty na układzie przepisu „Sernik z jabłkami i kruszonką”.

## 1.2.0

- Dodano import przepisów z `rozkoszny.pl`, wraz z dodatkowymi sekcjami składników i wykrywaniem formy.
- Naprawiono import starszych wpisów Alaantkoweblw zapisanych w kilku niepoprawnych wariantach HTML.
- Import Alaantkoweblw pomija dopiski blogowe rozpoczynające się od `Ps.` po właściwej instrukcji.
- Dodano niezależne od sieci testy regresyjne obu nowych wariantów parsera.

## 1.1.2

- W podsumowaniu importu można utworzyć nowy tag i od razu przypisać go do zapisanego przepisu.
- Pole dodawania tagu jest wspólne dla podsumowania importu i zwykłej edycji tagów.

## 1.1.1

- Przycisk zapisu w podglądzie importu jest stale widoczny podczas przewijania.
- Po zapisaniu importu można od razu przypisać istniejące tagi pod przyciskiem otwarcia przepisu.
- Tagi nieprzypisane do żadnego przepisu są automatycznie usuwane.

## 1.1.0

- Dodano własne tagi przypisywane do przepisów.
- Dodano edycję tagów obok źródła oraz chmurę tagów filtrującą bibliotekę.
- Gramy, dekagramy i mililitry po skalowaniu są zaokrąglane do pełnych wartości.
- Pozostałe ilości są pokazywane jako zwykłe ułamki mieszane i zaokrąglane do wielokrotności 1/8.
- Dodano migrację lokalnej bazy z wersji 1.0.0 bez utraty przepisów, zdjęć ani ulubionych.

## 1.0.0

- Pierwsza zachowana wersja aplikacji: import z Moje Wypieki i Alaantkoweblw, praca offline, wyszukiwanie, ulubione, edycja, kopie zapasowe oraz skalowanie składników i form.
- Instalacyjny APK i archiwum źródeł tej wersji znajdują się w katalogu `dist`.
