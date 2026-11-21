# Lift Log

An offline workout tracker: workout logging, routines, history, progress and body measurements, with no social features. Built with **Kotlin Multiplatform + Compose Multiplatform**, so the same UI and logic ship to Android now and iOS later.

It's the native companion to the Lift Log web app. Both use the same data format, so a backup made in one restores in the other.

## Features

- **Logging:** previous values, weight suggestions (double progression), PR detection, warm-up/drop/failure sets, RPE, supersets, rest timer with background alerts, a timer for timed sets, pinned exercise notes, auto warm-up sets, undo.
- **Routines:** routines with folders. Routines can be rebuilt automatically from your history, or created by the AI coach.
- **History:** weekly chart, calendar and streaks, muscle split with a body heat map, monthly and yearly reports, and a volume comparison ("about the weight of 2 grand pianos").
- **Exercises:** a 111-exercise library with Hevy-compatible names and form cues, plus custom exercises. Each exercise has progress charts (with two-exercise compare), PRs, best weight by rep count and stall warnings.
- **Measurements:** weight, body fat and tape measurements, with charts and progress photos.
- **Tools:** plate calculator, 1RM calculator, kg/lb switch, bodyweight-in-volume option, keep-screen-on.
- **Import and export:** imports Hevy CSV (workouts and measurements) and Lift Log JSON backups; exports Hevy-format CSV and JSON backups.
- **Coach:** answers questions using your own Anthropic API key, stored only on the phone.

## Build

- **Android:** open the folder in Android Studio and run `composeApp` on a device, or run `./gradlew :composeApp:installDebug`.
- **iOS** (later):
  ```
  brew install xcodegen
  cd iosApp && xcodegen
  open LiftLog.xcodeproj
  ```
  `iosMain/IosPlatform.kt` lists the remaining iOS hooks to wire up: file picker, photo picker and notifications.

## Layout

```
composeApp/src/commonMain   shared: model, SQLDelight storage, logic (stats, import/export, coach), Compose UI
composeApp/src/androidMain  Activity, file/photo pickers, rest alarm notification, keep-screen-on
composeApp/src/iosMain      UIViewController entry point and iOS platform starter
iosApp/                     XcodeGen spec and SwiftUI host
```

Data is stored as JSON documents in a single SQLite table. Each document is a workout, routine, custom exercise, measurement, setting, pin or the active workout, in the same shape the web app uses, so cloud sync can be added later without migrating anything.
