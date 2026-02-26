<!-- Local asset image -->

[![Banner](docs/images/readme_header.png)](https://www.digia.tech)

[![JitPack](https://jitpack.io/v/Digia-Technology-Private-Limited/digia_engage.svg)](https://jitpack.io/#Digia-Technology-Private-Limited/digia_engage)
[![License](https://img.shields.io/badge/license-BSL%201.1-green.svg)](LICENSE)

**Digia Engage** is the Jetpack Compose-based rendering engine for [Digia Studio](https://app.digia.tech), a low-code mobile application platform. Built on Server-Driven UI (SDUI), it dynamically renders native Compose widgets from server configurations.

## Installation

### 1. Add JitPack repository

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add dependency

```kotlin
dependencies {
    implementation("com.digia:digia_engage:1.0.0-beta-1")
}
```

> **Note:** Artifacts are built by JitPack from [Digia-Technology-Private-Limited/digia_engage](https://github.com/Digia-Technology-Private-Limited/digia_engage). Create a release tag (e.g. `1.0.0-beta-1`) to publish.

## Quick start

```kotlin
Digia.initialize(applicationContext, DigiaConfig(apiKey = "YOUR_KEY"))
```

See [Digia Studio documentation](https://docs.digia.tech) for full integration.

## License

BSL 1.1 - see [LICENSE](LICENSE).

---

Built with ❤️ by the Digia team