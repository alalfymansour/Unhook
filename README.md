# Unhook

Unhook is a lightweight Android digital friction app that interrupts infinite-scroll behavior by blocking vertical swipes inside selected social apps.

It does not block app access. It only blocks the addictive vertical gesture loop.

## Features

- Accessibility-driven, event-based behavior
- Blocks vertical swipe gestures only in selected target apps
- Allows taps and non-vertical interaction paths where possible
- User-configurable target app list (DataStore)
- Offline by design (no network, no analytics, no tracking)

## Target apps (defaults)

- `com.instagram.android`
- `com.facebook.katana`
- `com.google.android.youtube`
- `com.twitter.android`

## Architecture

- Kotlin only
- Settings UI: Jetpack Compose
- Gesture interception: AccessibilityService + TYPE_ACCESSIBILITY_OVERLAY + GestureDetector
- Storage: DataStore Preferences
- No Room, WorkManager, foreground service, polling, or remote calls

## How it works

1. `UnhookAccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED`.
2. If foreground app package is enabled, it adds a transparent full-screen overlay.
3. Overlay detects swipe direction and blocks only vertical swipes above threshold.
4. On app switch, overlay is removed immediately.

## Build requirements

- minSdk 26
- targetSdk 34
- Kotlin 1.9+
- JDK 17 (required)
- Android SDK + build-tools
- Android Studio (optional, recommended)

## Run locally

1. Open in Android Studio.
2. Sync Gradle.
3. Run app on device.
4. In the app, open Accessibility Settings and enable Unhook service.

## Build from command line (no Android Studio)

1. Ensure JDK 17 is installed and `JAVA_HOME` points to it.
2. Ensure Android SDK is installed and `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) is set.
3. Build debug APK:

```bash
./gradlew :app:assembleDebug
```

## Privacy

Unhook is fully offline.

- No analytics
- No telemetry
- No ads
- No network calls

## License

MIT. See [LICENSE](LICENSE).
