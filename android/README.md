<!-- Local asset image -->

[![Banner](docs/images/readme_header.png)](https://www.digia.tech)

[![Maven Central](https://img.shields.io/maven-central/v/tech.digia/engage.svg)](https://central.sonatype.com/artifact/tech.digia/engage)
[![License](https://img.shields.io/badge/license-BSL%201.1-green.svg)](LICENSE)

**Digia Engage** is the Jetpack Compose-based rendering engine for [Digia Studio](https://app.digia.tech), a low-code mobile application platform. Built on Server-Driven UI (SDUI), it dynamically renders native Compose widgets from server configurations.

## Installation

### 1. Add Maven Central repository

Maven Central is included by default in Android projects. No extra repository needed.

### 2. Add dependency

```kotlin
dependencies {
    implementation("tech.digia:engage:<version>")
}
```

Replace `<version>` with the [latest release](https://central.sonatype.com/artifact/tech.digia/engage).

## Quick start

```kotlin
Digia.initialize(applicationContext, DigiaConfig(apiKey = "YOUR_KEY"))
```

See [Digia Studio documentation](https://docs.digia.tech) for full integration.

## License

BSL 1.1 - see [LICENSE](LICENSE).

---

Built with ❤️ by the Digia team