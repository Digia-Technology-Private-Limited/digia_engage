# @digia/engage-react-native

React Native bridge for the **Digia Engage SDK** — renders in-app experiences
(tooltips, spotlights, carousels, surveys) inside React Native applications.

> **Platform support**
> | Platform | Status |
> |---|---|
> | Android | ✅ Full support |
> | iOS | ✅ Guide overlays (JS renderer); native bridge (surveys, inline) |

---

## How it works

Guide campaigns (tooltip / spotlight) are rendered entirely in **JavaScript** by
`DigiaProvider.tsx` using `@floating-ui/core` for anchor positioning. Surveys
and inline carousels are forwarded to the **native Android/iOS SDK** via the
bridge for Compose/SwiftUI rendering.

```
CEP plugin (e.g. CleverTap)
  │
  ▼
Digia.onCampaignTriggered(payload)
  │
  ├─ campaign_type === 'guide'   → DigiaGuideController → DigiaProvider.tsx (JS)
  │                                                        TooltipOverlay / SpotlightOverlay
  │
  ├─ campaign_type === 'inline'  → nativeDigiaModule.triggerCampaign()
  │                                Android: DigiaSlot composable → VWCarousel
  │
  └─ campaign_type === 'survey'  → nativeDigiaModule.triggerCampaign()
                                   Android: SurveyRenderer composable
```

---

## Installation

```sh
npm install @digia/engage-react-native
# or
yarn add @digia/engage-react-native
```

React Native CLI auto-linking handles the rest. Rebuild the native app:

```sh
cd android && ./gradlew assembleDebug
```

---

## Usage

### 1 — Initialize the SDK

Call `Digia.initialize()` once, as early as possible (top of `App.tsx`):

```tsx
import { Digia } from '@digia/engage-react-native';

await Digia.initialize({
  projectId: 'digia_YOUR_PROJECT_ID',
  environment: 'production', // or 'sandbox'
  logLevel: 'error',
});
```

### 2 — Mount `<DigiaHost />`

Place `<DigiaHost />` at the root of your component tree. It renders the
JS-side guide/tooltip/spotlight overlays via a `Modal`.

```tsx
// app/_layout.tsx (Expo Router) or App.tsx
import { DigiaHost } from '@digia/engage-react-native';

export default function RootLayout() {
  return (
    <>
      <Stack />
      <DigiaHost />
    </>
  );
}
```

### 3 — Track screen changes

```tsx
import { Digia } from '@digia/engage-react-native';

<NavigationContainer
  onStateChange={() => {
    const route = navRef.getCurrentRoute();
    if (route) Digia.setCurrentScreen(route.name);
  }}
>
```

### 4 — Register anchors for guide campaigns

Wrap any UI element you want a tooltip or spotlight to point at:

```tsx
import { DigiaAnchorView } from '@digia/engage-react-native';

<DigiaAnchorView anchorKey="home_banner_btn">
  <Button title="Banner" />
</DigiaAnchorView>
```

### 5 — Add slots for inline campaigns

```tsx
import { DigiaSlotView } from '@digia/engage-react-native';

// Auto-sizes to native content height
<DigiaSlotView placementKey="home_banner" />
```

### 6 — Register a CEP plugin

```tsx
import { DigiaCleverTapPlugin, createCleverTapClient } from '@digia/clevertap-plugin';
import CleverTap from 'clevertap-react-native';

Digia.register(new DigiaCleverTapPlugin({
  cleverTap: createCleverTapClient(CleverTap),
}));
```

### 7 — (Optional) Handle actions

Override or observe every action the SDK fires:

```tsx
await Digia.initialize({
  projectId: '...',
  onAction: (action, context) => {
    if (action.type === 'deep_link') {
      // return true to suppress SDK default; false/void to let SDK handle it
    }
  },
  linking: {
    routeViaSystemLinking: true,
    inAppBrowser: defaultInAppBrowser, // requires react-native-inappbrowser-reborn
  },
});
```

---

## API Reference

### `Digia`

| Method | Signature | Description |
|---|---|---|
| `initialize` | `(config: DigiaConfig) => Promise<void>` | Initialize the SDK. Call once before anything else. |
| `register` | `(plugin: DigiaPlugin) => void` | Register a CEP plugin (CleverTap, MoEngage, etc.). |
| `unregister` | `(plugin: DigiaPlugin \| string) => void` | Remove a previously registered plugin. |
| `setCurrentScreen` | `(name: string) => void` | Notify the SDK of the active screen. |
| `registerAnchor` | `(key: string, screen?: string) => void` | Manually register an anchor key. |
| `unregisterAnchor` | `(key: string) => void` | Remove an anchor registration. |

### `DigiaConfig`

| Prop | Type | Default | Description |
|---|---|---|---|
| `projectId` | `string` | — | **Required.** Your Digia project ID (format: `digia_…`). Sent as `x-digia-project-id` on all SDK requests. |
| `environment` | `'production' \| 'sandbox'` | `'production'` | Target environment. |
| `logLevel` | `'none' \| 'error' \| 'verbose'` | `'error'` | Log verbosity. |
| `onAction` | `OnAction` | — | Override hook for all actions. Return `true` to suppress SDK default. |
| `linking.routeViaSystemLinking` | `boolean` | `true` | Use `Linking.openURL` for URL actions. |
| `linking.inAppBrowser` | `InAppBrowserAdapter` | — | Required for `open_url` with `presentation: 'in_app'`. |
| `baseUrl` | `string` | — | Override the Digia API base URL. |

### `<DigiaHost />`

No props. Renders the JS guide overlay runtime (`TooltipOverlay`, `SpotlightOverlay`).
Place it once, anywhere in the root view — `Modal` handles z-ordering.

### `<DigiaAnchorView>`

| Prop | Type | Description |
|---|---|---|
| `anchorKey` | `string` | **Required.** Must match the step's `anchorKey` in the campaign config. |
| `...ViewProps` | — | All standard React Native `View` props are forwarded. |

### `<DigiaSlotView>`

| Prop | Type | Description |
|---|---|---|
| `placementKey` | `string` | **Required.** Must match the campaign's `slotKey`. |
| `style` | `ViewStyle?` | Pass an explicit `height` to fix size; otherwise auto-sizes. |

### `<DigiaHostView>`

Low-level transparent native overlay view (Android/iOS). Use `<DigiaHost />` instead
unless you need the native Compose/SwiftUI overlay host explicitly.

---

## Campaign types

| Type | Trigger | JS or Native |
|---|---|---|
| `guide` (tooltip) | `DigiaGuideController` → `DigiaHost` | **JS** — `TooltipOverlay` via `Modal` |
| `guide` (spotlight) | `DigiaGuideController` → `DigiaHost` | **JS** — `SpotlightOverlay` via `Modal` |
| `inline` | `nativeDigiaModule.triggerCampaign()` | **Native** — Android Compose `VWCarousel` |
| `survey` | `nativeDigiaModule.triggerCampaign()` | **Native** — Android Compose `SurveyRenderer` |
| `nudge` | `nativeDigiaModule.triggerCampaign()` | **Native** — Android Compose dialog / bottom-sheet |

---

## Architecture

```
react-native/src/
  index.ts                  Public API exports
  Digia.ts                  SDK singleton — initialize, register, setCurrentScreen,
                            campaign routing, campaign store (fetched from backend)
  DigiaProvider.tsx         JS guide renderer — TooltipOverlay + SpotlightOverlay + DigiaHost
  DigiaGuideController.ts   Event bus for guide start/cancel; queues if DigiaHost not mounted
  digiaAnchorRegistry.ts    In-memory anchor position store with subscriber pattern
  DigiaAnchorView.tsx       Wraps UI elements; measures position via ref.measure
  DigiaSlotView.tsx         Native slot view wrapper; auto-sizes to content height
  DigiaHostView.tsx         Low-level native overlay host (transparent, pointer-events none)
  NativeDigiaEngage.ts      Codegen native module spec (TurboModule)
  actionHandler.ts          Action execution — deep link, open URL, next/prev/dismiss,
                            fire_event; onAction override; cold-start queue
  defaultInAppBrowser.ts    Lazily loads react-native-inappbrowser-reborn
  templateTypes.ts          TypeScript types for TooltipConfig, SpotlightConfig,
                            CarouselConfig, SurveyTemplateConfig
  types.ts                  DigiaConfig, DigiaPlugin, DigiaDelegate, InAppPayload,
                            DigiaAction, ActionContext, DigiaExperienceEvent

react-native/android/       Android bridge
  DigiaModule.kt            initialize, registerBridge, triggerCampaign, registerAnchor, …
  DigiaSlotViewManager.kt   DigiaSlotView native view manager
  DigiaAnchorViewManager.kt DigiaAnchorView native view manager
  DigiaViewManager.kt       DigiaHostView native view manager

react-native/ios/           iOS bridge
  DigiaModule.swift         initialize, registerBridge, triggerCampaign, …
  DigiaHostViewManager.swift
  DigiaAnchorViewManager.swift
```

---

## License

MIT © Digia Technology Private Limited
