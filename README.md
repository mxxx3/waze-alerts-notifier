# Traffic Alerts Notifier

Android app for nearby road-alert notifications while driving.

Repository name is still `waze-alerts-notifier`, but the current app identity in code and UI is **Traffic Alerts Notifier**.

Current version:
- `versionName`: `0.9.34`
- `versionCode`: `44`
- application ID: `com.mg.trafficalerts`

## What it does

The app runs a foreground location service, fetches nearby traffic alerts from one or more providers, keeps a local cache while you move, and posts live notifications on the phone and in Android Auto.

Supported alert categories include:
- police
- cameras
- accidents
- traffic jams
- hazards
- roadworks / closures

## Main features

- Background monitoring with a foreground location service.
- Main dashboard with radius, refresh interval, active alerts, navigation, and per-alert mute controls.
- Live distance and relative direction updates as the device moves.
- Reverse-geocoded addresses in the phone UI and notifications.
- Provider health diagnostics in Settings.
- In-app log viewer with Clear / Copy / Export actions.
- Appearance modes: System, Light, Dark.
- Notification actions including `Reply` and `Mark as read`.
- Alert notifications open the dashboard, and per-alert navigation can open the selected alert in Google Maps or Waze.

## Alert sources

The app uses a replaceable provider boundary via `AlertProvider`.

### Waze Live Map

`WazeLiveMapAlertProvider` queries Waze Live Map `georss` data for a bounding box around the current location.

Notes:
- This source is **experimental**.
- Waze does not provide a stable public read API for nearby user reports.
- The implementation uses an embedded `WebView` fetch/interception flow to obtain the Waze data needed by the app.
- Waze may change behavior at any time, and this source can fail with HTTP 403 or other breakage.

Mapped categories currently include Waze police, cameras, accidents, hazards, jams, and road closures.

### OpenStreetMap cameras

`OpenStreetMapCameraProvider` uses Overpass for fixed camera data, including speed cameras and red-light cameras.

Notes:
- enabled by default
- capped to a 25 km Overpass query radius
- coverage depends on local OpenStreetMap quality
- this is fixed-camera data, not temporary/mobile police reports

### TomTom traffic incidents

`TomTomTrafficAlertProvider` uses TomTom Traffic API v5 `incidentDetails` when a TomTom API key is saved in Settings.

This is the more stable global provider for:
- accidents
- jams
- closures
- roadworks
- hazards

### Demo provider

`DemoAlertProvider` still exists in code for testing, but it is not exposed in the normal Settings UI.

## How monitoring works

- The app listens for live device location updates.
- It fetches alerts using a **wider cache radius** than the visible radius.
- Cached alerts are re-filtered against the live position while driving.
- When a provider refresh succeeds and an old alert disappears, stale alerts are expired quickly instead of lingering for the full cache TTL.
- Nearby duplicate alerts from different providers are deduplicated.
- Notification cleanup is reconciled against persisted state so stale notifications can be removed even after process restarts.

## Android Auto behavior

The app uses **notification-based Android Auto integration**.

It intentionally does **not** expose:
- an Android Auto launcher surface
- a template app UI
- a media browser / media player service

Instead, it posts car-compatible alert notifications so Google Maps or Waze can remain the main Android Auto screen.

Behavior:
- the closest urgent alert is shown as an individual car notification
- additional nearby alerts are grouped into a summary notification
- distance and direction continue updating from live location data
- `Mark as read` dismisses the related alert(s)

When notification access is granted, `MapsNavigationListener` can detect active Google Maps navigation notifications and help the app keep route-adjacent alerts relevant to the current drive.

## Reliability / keep-alive

While monitoring is enabled, the app tries to stay alive using:
- foreground service
- `AlarmManager` heartbeat
  - every 60 s when Android Auto is connected
  - every 5 min otherwise
- `WorkManager` periodic backup worker
- auto-start after boot and app replacement
- restart scheduling on crash or unexpected service destruction

On startup and shutdown, stale road-alert notifications are swept so they do not linger after process death or when monitoring stops.

## Permissions and settings

The Settings screen includes a dedicated Permissions panel with per-permission status and shortcuts for:
- Fine location
- Background location
- Notifications
- Notification listener access
- Exact alarms
- Unrestricted battery / battery optimization settings

Other configurable settings include:
- monitoring on/off
- notifications on/off
- visible alert radius
- poll interval
- appearance mode
- alert type filters
- navigation app
- cache TTL
- min / max cache radius
- cache radius multiplier
- visible alert limit
- enabled sources
- TomTom API key

## Project structure

Key files:
- `app/src/main/java/com/mg/trafficalerts/monitor/AlertMonitorService.kt` - foreground monitoring, location updates, notifications
- `app/src/main/java/com/mg/trafficalerts/source/AlertRepository.kt` - provider orchestration, filtering, dedup, address resolution
- `app/src/main/java/com/mg/trafficalerts/source/WazeLiveMapAlertProvider.kt` - Waze source mapping and fetch selection
- `app/src/main/java/com/mg/trafficalerts/source/WazeWebViewFetcher.kt` - Waze `WebView` interception flow
- `app/src/main/java/com/mg/trafficalerts/SettingsActivity.kt` - settings, diagnostics, permissions UI
- `app/src/main/java/com/mg/trafficalerts/MainActivity.kt` - main dashboard
- `app/src/main/java/com/mg/trafficalerts/LogActivity.kt` - in-app log viewer

## Build

### Windows

```powershell
.\gradlew.bat assembleDebug
```

### Linux / macOS

If `gradlew` is not executable in your checkout, run:

```bash
chmod +x ./gradlew
./gradlew assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install for local testing

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then:
- open the app
- grant the required permissions
- enable background monitoring
- optionally add a TomTom API key for broader incident coverage

On Android 11+, background location may need to be granted from the system app settings screen.

## Known limitations

- The Waze source is unofficial and may break at any time.
- OpenStreetMap camera coverage varies by region.
- TomTom incidents require a user-provided API key.
- Google Maps route geometry is not exposed to third-party apps, so route awareness depends on notification/state signals rather than full route access.

## Release notes

Release tags use `v<versionName>`.

For the current version, the debug release asset name should be:

```text
TrafficAlertsNotifier-debug-v0.9.34.apk
```
