[![pub.dev](https://img.shields.io/pub/v/digia_engage.svg)](https://pub.dev/packages/digia_engage)
[![Flutter](https://img.shields.io/badge/Flutter-3.0+-blue.svg)](https://flutter.dev)
[![License](https://img.shields.io/badge/license-BSL%201.1-green.svg)](LICENSE)
[![Documentation](https://img.shields.io/badge/docs-digia.tech-blue.svg)](https://docs.digia.tech)

**Digia Engage** is the Flutter SDK for displaying in-app campaigns powered by [Digia Studio](https://app.digia.tech). It connects to your Customer Engagement Platform (MoEngage, CleverTap, etc.) and renders server-driven in-app experiences — dialogs, bottom sheets, and inline widgets — without shipping a new app build.

---

## How It Works

1. Your CEP (e.g. MoEngage) sends a campaign trigger to the device.
2. The `DigiaCEPPlugin` adapter receives the payload and hands it to the SDK.
3. The SDK routes it to the right renderer:
   - **Modal** (dialog / bottom sheet) → rendered by `DigiaHost`
   - **Inline** (banner / card) → rendered by `DigiaSlot`
4. The component is built from a server-driven layout defined in Digia Studio — no client-side code changes needed.

---

## Installation

```yaml
dependencies:
  digia_engage: ^1.1.0
```

---

## Quick Start

### 1. Initialize

Call once in `main()` before `runApp()`.

```dart
await Digia.initialize(
  DigiaConfig(
    apiKey: 'YOUR_API_KEY',
    flavor: Flavor.release(),
    strategy: NetworkFirstStrategy(timeoutInMs: 2000),
  ),
);
```

### 2. Register your CEP plugin

After your CEP SDK is ready, register its adapter:

```dart
Digia.register(MoEngagePlugin(instance: moEngageInstance));
```

### 3. Add `DigiaHost` and `DigiaNavigatorObserver`

Wrap your `MaterialApp` so the SDK can show modal overlays and track screens:

```dart
MaterialApp(
  navigatorKey: DigiaHost.navigatorKey,
  navigatorObservers: [DigiaNavigatorObserver()],
  builder: (context, child) => DigiaHost(child: child!),
  home: const MyHomePage(),
)
```

> `navigatorKey: DigiaHost.navigatorKey` is required. `DigiaHost` sits in
> `MaterialApp.builder` which is above the `Navigator` in the widget tree.
> Without this key the SDK cannot resolve a navigator context to show modals.

### 4. Add `DigiaSlot` where inline content should appear

Place a slot anywhere in your page layout. Use the same `placementKey` that was configured in Digia Studio.

```dart
Column(
  children: [
    DigiaSlot('home_hero_banner'),
    // ... rest of your page
  ],
)
```

> **1.1.0+** — Inline payloads must include `"type": "inline"` and `"placementKey": "<key>"`. Payloads missing both `type` and `command` are dropped with a warning.

---

## API Reference

### `Digia`

Static facade — the single entry point for all SDK calls.

| Method | Description |
|---|---|
| `Digia.initialize(config)` | Boot the SDK. Call once in `main()`, await before `runApp()`. |
| `Digia.register(plugin)` | Attach a CEP plugin adapter (MoEngage, CleverTap, etc.). |
| `Digia.setCurrentScreen(name)` | Manually report the current screen name to the CEP. |

---

### `DigiaConfig`

| Property | Type | Description |
|---|---|---|
| `apiKey` | `String` | Your Digia project API key. |
| `flavor` | `Flavor` | `Flavor.release()` or `Flavor.debug()`. |
| `strategy` | `InitStrategy` | `NetworkFirstStrategy` or `CacheFirstStrategy`. |
| `environment` | `Environment` | Optional — defaults to production. |

---

### `DigiaHost`

Mount once in `MaterialApp.builder`. Automatically renders modal campaigns (dialog / bottom sheet) when triggered by the CEP.

```dart
MaterialApp(
  navigatorKey: DigiaHost.navigatorKey,   // required
  navigatorObservers: [DigiaNavigatorObserver()],
  builder: (context, child) => DigiaHost(child: child!),
)
```

**Behavior:**

| Campaign type | Handled by |
|---|---|
| `dialog` | `DigiaHost` — shows as a dialog |
| `bottomsheet` | `DigiaHost` — shows as a modal bottom sheet |
| `inline` | `DigiaSlot` — not handled by `DigiaHost` |

---

### `DigiaSlot`

Renders inline campaign content at a named placement position. Collapses to nothing when no campaign is active.

```dart
DigiaSlot('placement_key')
```

**Lifecycle:**

| Event | Behavior |
|---|---|
| Slot mounts, campaign already exists | Renders immediately and fires an impression |
| New campaign arrives for this placement | Rebuilds and fires impression for the new payload |
| Server invalidates the campaign | Slot collapses to `SizedBox.shrink()` |
| User dismisses via a close CTA | Fires dismiss event, slot collapses |
| Page navigates away / disposes | Campaign stays in memory — reappears on return |

---

### `DigiaNavigatorObserver`

Automatically reports the current route name to the CEP as a screen-change event. Add it to `navigatorObservers` and no manual `setCurrentScreen` calls are needed.

```dart
navigatorObservers: [DigiaNavigatorObserver()]
```

---

## CEP Plugin Interface

To connect a new CEP, implement `DigiaCEPPlugin`:

```dart
class MyCEPPlugin implements DigiaCEPPlugin {
  @override
  String get identifier => 'my_cep';

  @override
  void setup(DigiaCEPDelegate delegate) {
    // Subscribe to in-app events from your CEP SDK and pass payloads
    // to delegate.onExperienceReady(payload)
  }

  @override
  void teardown() { /* clean up subscriptions */ }

  @override
  void notifyEvent(DigiaExperienceEvent event, InAppPayload payload) {
    // Forward impression/dismiss events back to your CEP
  }

  @override
  void forwardScreen(String screenName) {
    // Forward screen name to your CEP
  }
}
```

Then register it:

```dart
Digia.register(MyCEPPlugin());
```

---

## Experience Events

The SDK fires two events during a campaign lifecycle, forwarded to your CEP plugin via `notifyEvent`:

| Event | When |
|---|---|
| `ExperienceImpressed` | The first time a campaign renders (modal shown / slot built) |
| `ExperienceDismissed` | The user explicitly closes the campaign |

---

## License

This project is licensed under the Business Source License 1.1 (BSL 1.1) - see the LICENSE file for details. The BSL 1.1 allows personal and commercial use with certain restrictions around competing platforms. On August 5, 2029, the license will automatically convert to Apache License 2.0.

For commercial licensing inquiries or exceptions, please contact admin@digia.tech.

## Support

- 📚 [Documentation](https://docs.digia.tech)
- 📧 [Contact Support](mailto:admin@digia.tech)
