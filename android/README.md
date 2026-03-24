# Android

Native Android SDK for Digia Engage. The SDK lives in `android/digia-ui`.

## Requirements

- Android Studio / Gradle
- JDK 17

## Usage

```kotlin
Digia.initialize(
    context = applicationContext,
    config = DigiaConfig(apiKey = "YOUR_API_KEY"),
)
```

Use `DigiaHost()` for Nudges and `DigiaSlot()` for inline widgets.

## Layout

- `digia-ui/` - SDK source
- `example/` - Android example app

---

Built with ❤️ by the Digia team