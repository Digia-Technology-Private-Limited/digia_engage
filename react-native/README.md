# @digia-engage/core

React Native SDK for **Digia Engage** – renders native Android (Jetpack Compose) and iOS campaign UI (bottom sheets, dialogs, inline banners, tooltips, spotlights) inside React Native applications.

> **Platform support**
> | Platform | Status |
> |---|---|
> | Android | ✅ Full support |
> | iOS | ✅ Full support |

---

## Installation

```sh
npm install @digia-engage/core
```

React Native CLI auto-linking handles the rest. Rebuild the native app after installing:

```sh
npx react-native run-android
# or
cd android && ./gradlew assembleDebug
```

### Android – host app dependency

Add the Digia Engage Android SDK to `android/app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("tech.digia:engage:1.1.0")
}
```

---

## Usage

### 1 – Initialise the SDK

Call `Digia.initialize()` once at app startup, before registering any CEP plugin:

```tsx
import { useEffect } from 'react';
import { Digia } from '@digia-engage/core';

export function RootApp() {
  useEffect(() => {
    (async () => {
      await Digia.initialize({ apiKey: 'YOUR_ACCESS_KEY' });
      // Register your CEP plugin here (e.g. DigiaCleverTapPlugin)
    })().catch(console.error);
  }, []);

  return <AppNavigator />;
}
```

### 2 – Mount the overlay host

Place `<DigiaHostView>` at the app root so nudge campaigns (bottom sheets, dialogs, tooltips, spotlights) render above all content:

```tsx
import { StyleSheet, View } from 'react-native';
import { DigiaHostView } from '@digia-engage/core';
import { Stack } from 'expo-router';

export default function RootLayout() {
  return (
    <View style={styles.root}>
      <DigiaHostView style={StyleSheet.absoluteFill} />
      <Stack />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
});
```

### 3 – Track screen changes

Wire `setCurrentScreen()` to your navigation library so the SDK can trigger screen-scoped campaigns:

```tsx
import { Digia } from '@digia-engage/core';

// React Navigation example:
<NavigationContainer
  onStateChange={() => {
    const route = navRef.getCurrentRoute();
    if (route) Digia.setCurrentScreen(route.name);
  }}
>
```

### 4 – Add inline slots

Place `<DigiaSlotView>` wherever you want inline campaign content (banners, cards):

```tsx
import { DigiaSlotView } from '@digia-engage/core';

<DigiaSlotView
  placementKey="home_hero_banner"
  style={{ width: '100%', height: 180 }}
/>
```

---

## API Reference

### `Digia`

| Method | Signature | Description |
|---|---|---|
| `initialize` | `(config: DigiaConfig) => Promise<void>` | Initialise the SDK. Call once at app startup. |
| `register` | `(plugin: DigiaPlugin) => void` | Register a CEP plugin (e.g. `DigiaCleverTapPlugin`). |
| `unregister` | `(plugin: DigiaPlugin \| string) => void` | Unregister a plugin and call its `teardown()`. |
| `setCurrentScreen` | `(name: string) => void` | Notify the SDK of the current screen name. |

### `DigiaConfig`

| Prop | Type | Default | Description |
|---|---|---|---|
| `apiKey` | `string` | — | **Required.** Your Digia project API key. |
| `environment` | `'production' \| 'sandbox'` | `'production'` | Target environment. |
| `logLevel` | `'none' \| 'error' \| 'verbose'` | `'error'` | Log verbosity. |

### `<DigiaHostView>`

Transparent overlay that hosts Digia-rendered campaign UI (dialogs, bottom sheets, tooltips, spotlights) above app content. Mount once at the app root.

| Prop | Type | Description |
|---|---|---|
| `style` | `ViewStyle?` | Style for the overlay view. Typically `StyleSheet.absoluteFill`. |

### `<DigiaSlotView>`

Renders inline campaign content (banners, cards) at a named placement. Collapses to zero height when no campaign is active for the slot.

| Prop | Type | Description |
|---|---|---|
| `placementKey` | `string` | Must match the placement key in your CEP campaign. |
| `style` | `ViewStyle?` | An explicit height is required — the slot is not visible without one. |

---

## License

MIT © Digia Technology Private Limited
