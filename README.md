# Unhook

Unhook is a lightweight Android digital-friction app that interrupts infinite-scroll behavior inside selected social apps.

Core idea: Unhook yourself from corporations' chains.

Unhook pushes back against algorithmic compulsion by adding deliberate friction where addictive loops usually win.

## Philosophy

- You own your attention.
- Products should serve users, not trap them.
- Friction can be a feature when it protects autonomy.
- Offline-first design protects privacy and trust.

## Features

- Accessibility-driven, event-based behavior
- Blocks vertical swipe gestures in short-video contexts for selected apps
- Allows taps and non-vertical interaction paths where possible
- Optional hard block for TikTok app opening (when TikTok is enabled in target apps)
- User-configurable target app list (DataStore)
- Offline by design (no network, no analytics, no tracking)

## Target apps (defaults)

- `com.instagram.android`
- `com.facebook.katana`
- `com.google.android.youtube`
- `com.twitter.android`
- `com.zhiliaoapp.musically`

## Architecture

- Kotlin only
- Settings UI: Jetpack Compose
- Gesture interception: AccessibilityService + TYPE_ACCESSIBILITY_OVERLAY + GestureDetector
- Storage: DataStore Preferences
- No Room, WorkManager, foreground service, polling, or remote calls

## How it works

1. `UnhookAccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED`.
2. If a supported foreground app is enabled, the service evaluates screen context.
3. In detected short-video contexts, a transparent full-screen overlay blocks vertical swipes.
4. Non-target contexts keep normal interaction flow.
5. For TikTok, if enabled, Unhook can immediately exit the app to Home.

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

Non-commercial only. Any commercial use is prohibited without prior written permission.
See [LICENSE](LICENSE).
