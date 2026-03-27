# Contributing to Unhook

Thanks for helping improve Unhook.

## Development setup

1. Open the project in Android Studio (latest stable).
2. Let Gradle sync.
3. Build and run on a physical device (Accessibility behavior is device-specific).

## Code guidelines

- Keep the app fully offline.
- Keep behavior event-driven (no polling, no background loops).
- Avoid adding analytics or tracking libraries.
- Prefer simple, readable Kotlin with small focused classes.

## Pull requests

- Describe what changed and why.
- Include manual test steps.
- Keep PRs focused and small when possible.
