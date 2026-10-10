# SpacerLogger

Prosta aplikacja Android do rejestrowania spaceru przy użyciu GPS i map OpenStreetMap.

## Funkcje

- rejestrowanie trasy w usłudze pierwszoplanowej,
- pomiar dystansu i czasu,
- szacowanie spalonych kalorii na podstawie masy użytkownika,
- prezentacja trasy na mapie osmdroid,
- zapis podsumowania spaceru w SharedPreferences,
- automatyczne budowanie debug APK przez GitHub Actions.

## Wymagania

- Android Studio z JDK 17,
- Android SDK 36,
- minimalny Android 8.0, API 26.

## Uruchomienie

1. Otwórz katalog projektu w Android Studio.
2. Poczekaj na synchronizację Gradle.
3. Uruchom aplikację na urządzeniu z GPS lub emulatorze z symulowaną lokalizacją.
4. Przyznaj uprawnienia lokalizacji i powiadomień.

## Budowanie APK lokalnie

```bash
gradle assembleDebug --no-daemon
```

APK zostanie utworzone w:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Workflow `.github/workflows/android-build.yml` buduje APK po każdym pushu do gałęzi `main` oraz po ręcznym uruchomieniu. Artefakt jest publikowany jako `SpacerLogger-apk`.
