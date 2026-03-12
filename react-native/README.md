# @digia/engage-react-native

React Native bridge for the **Digia Engage SDK** – renders native Android
Jetpack Compose UI (dialogs, bottom-sheets) inside React Native applications.

> **Platform support**
> | Platform | Status |
> |---|---|
> | Android | ✅ Full support |
> | iOS | 🚧 Stub (no-op – coming soon) |

---

## How it works

The Digia Engage Android SDK is built entirely with Jetpack Compose. In a
pure-Android app you wrap your content with the `DigiaHost { }` composable; it
then manages campaign-driven overlays (dialogs, bottom sheets) on top of your
content.

In a React Native app we cannot embed a Composable directly in the JS tree, so
the bridge uses two complementary mechanisms:

```
┌─────────────────────────────────────────┐
│  Android Activity (ReactActivity)       │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  Android content FrameLayout    │    │
│  │                                 │    │
│  │  ┌─────────────────────────┐    │    │
│  │  │  React Native RootView  │    │    │
│  │  │  (your JS UI)           │    │    │
│  │  └─────────────────────────┘    │    │
│  │                                 │    │
│  │  ┌─────────────────────────┐    │    │
│  │  │  DigiaHostComposeView   │    │    │
│  │  │  (AbstractComposeView)  │    │    │
│  │  │  hosts DigiaHost { }   │    │    │
│  │  │  ← transparent, no     │    │    │
│  │  │    touch interception  │    │    │
│  │  └─────────────────────────┘    │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌──────────────────────────────────┐   │
│  │  Compose Dialog window (overlay) │   │
│  │  Triggered by CEP campaign       │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

1. **`Digia.initialize()`** initialises the SDK and calls `addContentView()`
   to attach a transparent `DigiaHostComposeView` on top of the React Native
   view hierarchy.  This is the anchor for the Compose composition.

2. **`DigiaHost { }`** inside the `ComposeView` manages `DialogManager` and
   `BottomSheetManager`.  When a CEP plugin triggers a campaign, Compose
   renders a `Dialog` or `ModalBottomSheet` – these are **separate Android
   windows** that appear on top of everything, including React Native content.

3. **`<DigiaHostView>`** is an optional React Native component you can place in
   your component tree (e.g. as `StyleSheet.absoluteFill`) if you prefer a
   declarative, component-based mount point instead of the auto-mount.

---

## Installation

```sh
# npm
npm install @digia/engage-react-native

# yarn
yarn add @digia/engage-react-native
```

React Native CLI auto-linking handles the rest.  Rebuild the native app:

```sh
npx react-native build-android
# or
cd android && ./gradlew assembleDebug
```

### Android – host app dependencies

Your app's `android/app/build.gradle` must declare the Digia Engage AAR
(or include it via your local Maven / private registry):

```groovy
dependencies {
    implementation 'com.digia:digia-ui:1.0.0-beta-1'
}
```

If you are working inside the monorepo and building locally, add the `:digia-ui`
project instead:

```groovy
implementation(project(':digia-ui'))
```

### iOS

iOS is a no-op stub.  All methods resolve immediately without error.

---

## Usage

### 1 – Initialise the SDK

Call `Digia.initialize()` once, as early as possible (e.g. the top of
`App.tsx`):

```tsx
import React, { useEffect } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { Digia } from '@digia/engage-react-native';

export default function App() {
  useEffect(() => {
    Digia.initialize({
      apiKey: 'YOUR_DIGIA_API_KEY',
      environment: 'production', // or 'sandbox'
      logLevel: 'error',
    });
  }, []);

  return <NavigationContainer>{/* … */}</NavigationContainer>;
}
```

`initialize()` returns a `Promise<void>` and automatically attaches the Compose
overlay host to the Activity.  You do **not** need to add `<DigiaHostView>`
unless you want an explicit component-based mount point.

### 2 – Track screen changes

Wire `setCurrentScreen()` to your navigation state so the SDK can trigger
campaigns based on the active screen:

```tsx
import { useNavigationContainerRef } from '@react-navigation/native';
import { Digia } from '@digia/engage-react-native';

// Inside your App component:
const navRef = useNavigationContainerRef();

<NavigationContainer
  ref={navRef}
  onStateChange={() => {
    const currentRoute = navRef.getCurrentRoute();
    if (currentRoute) {
      Digia.setCurrentScreen(currentRoute.name);
    }
  }}
>
```

### 3 – Open the Digia UI navigation flow (Android)

Launch the full-screen Compose navigation activity managed by the Digia DSL
configuration:

```tsx
import { Digia } from '@digia/engage-react-native';

function MyScreen() {
  return (
    <Button
      title="Open Digia Experience"
      onPress={() =>
        Digia.openNavigation({
          startPageId: 'onboarding',
          pageArgs: { userId: '123' },
        })
      }
    />
  );
}
```

### 4 – (Optional) Declarative overlay mount via `<DigiaHostView>`

If you prefer an explicit React component instead of the auto-mount, skip the
`initialize()` auto-mount and place `<DigiaHostView>` at the root of your
component tree:

```tsx
import React from 'react';
import { StyleSheet, View } from 'react-native';
import { DigiaHostView } from '@digia/engage-react-native';

export default function App() {
  return (
    <View style={styles.root}>
      {/* DigiaHostView must be above all other content in z-order */}
      <DigiaHostView style={StyleSheet.absoluteFill} />

      {/* Your navigation / content here */}
    </View>
  );
}

const styles = StyleSheet.create({ root: { flex: 1 } });
```

---

## API Reference

### `Digia`

| Method | Signature | Description |
|---|---|---|
| `initialize` | `(config: DigiaConfig) => Promise<void>` | Initialise the SDK and mount the Compose overlay host. |
| `setCurrentScreen` | `(name: string) => void` | Notify the SDK of the current screen. |
| `openNavigation` | `(options?: DigiaNavigationOptions) => void` | Launch the full-screen Digia UI navigation activity. |

### `DigiaConfig`

| Prop | Type | Default | Description |
|---|---|---|---|
| `apiKey` | `string` | — | **Required.** Your Digia project API key. |
| `environment` | `'production' \| 'sandbox'` | `'production'` | Target environment. |
| `logLevel` | `'none' \| 'error' \| 'verbose'` | `'error'` | Log verbosity. |

### `DigiaNavigationOptions`

| Prop | Type | Description |
|---|---|---|
| `startPageId` | `string?` | DSL page ID to start from. |
| `pageArgs` | `Record<string, string>?` | Key/value args forwarded to the start page. |

### `<DigiaHostView>`

A transparent React Native view that hosts the Compose overlay anchor.

| Prop | Type | Description |
|---|---|---|
| `style` | `ViewStyle?` | Custom style (defaults to `absoluteFill` behaviour). |

---

## Architecture overview

```
react-native/
├── src/
│   ├── index.ts                  ← Public API exports
│   ├── types.ts                  ← TypeScript interfaces
│   ├── Digia.ts                  ← High-level JS SDK wrapper
│   ├── NativeDigiaModule.ts      ← Low-level native module binding
│   └── DigiaHostView.tsx         ← <DigiaHostView> React component
│
├── android/
│   ├── build.gradle              ← Android library build config
│   └── src/main/java/com/digia/engage/rn/
│       ├── DigiaPackage.kt       ← ReactPackage (registers module + view)
│       ├── DigiaModule.kt        ← NativeModule (initialize, setCurrentScreen, openNavigation)
│       ├── DigiaViewManager.kt   ← ViewManager for <DigiaHostView>
│       └── DigiaHostComposeView.kt ← AbstractComposeView hosting DigiaHost { }
│
├── ios/
│   └── DigiaEngageModule.m       ← iOS no-op stub
│
├── DigiaEngageReactNative.podspec
├── react-native.config.js        ← Auto-linking config
└── package.json
```

---

## License

MIT © Digia Technology Private Limited
