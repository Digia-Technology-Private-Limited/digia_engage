# Showing Native UI in React Native — Overlay vs Inline

The Digia React Native bridge exposes two distinct ways to render native Jetpack
Compose UI inside a React Native app. Both are driven by campaign payloads
delivered to the SDK; the difference is *where* the UI appears and *who
controls its size*.

---

## The two modes

| | **Overlay (In-App Nudge)** | **Inline Slot** |
|---|---|---|
| **Component** | `<DigiaHostView>` | `<DigiaSlotView placementKey="…">` |
| **Compose entry point** | `DigiaHost { }` | `DigiaSlot(placementKey)` |
| **Position** | Above all React Native content — separate Android `Window` | Embedded in the normal RN view tree |
| **Sizing** | SDK controls size | You control size via `style` |
| **Campaign command** | `SHOW_DIALOG` or `SHOW_BOTTOM_SHEET` | `SHOW_INLINE` |
| **Dashboard key** | — | `placementKey` |
| **Number of instances** | One per app (root level) | One per slot, anywhere in the tree |
| **Survives screen navigation** | No — dismissed per campaign | Yes — persists until server invalidates |

---

## 1. Overlay UI — `<DigiaHostView>`

### What it is

A **transparent, full-screen, touch-pass-through view** mounted at the root of
your component tree. When the SDK receives a campaign with command
`SHOW_DIALOG` or `SHOW_BOTTOM_SHEET`, the native Compose layer renders a
Dialog or BottomSheet into a **separate Android Window** that sits above the
entire React Native view hierarchy — no z-index management needed.

```
Android Activity
│
├── React Native root view          ← your normal app
│   ├── NavigationContainer
│   │   └── … screens …
│   └── <DigiaHostView />           ← transparent anchor (0 visual footprint)
│
└── [Compose Dialog window]         ← appears when campaign fires
    └── YourDialogComponent
```

### Setup

Mount `<DigiaHostView>` **once**, at the root of your app, using
`StyleSheet.absoluteFillObject` so it covers the full screen without affecting
layout:

```tsx
import React, { useEffect } from 'react';
import { StyleSheet } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { Digia, DigiaHostView } from '@digia/engage-react-native';

export default function App() {
  useEffect(() => {
    Digia.initialize({ apiKey: 'YOUR_API_KEY' });
  }, []);

  return (
    <NavigationContainer>
      {/* Mount once at root — transparent, no visual footprint */}
      <DigiaHostView style={StyleSheet.absoluteFillObject} />

      <Stack.Navigator>
        <Stack.Screen name="Home" component={HomeScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
```

### Campaign payload

The `command` field in the campaign payload controls which Compose UI is shown:

```json
// Dialog
{
  "command": "SHOW_DIALOG",
  "viewId": "my_promo_dialog",
  "args": {
    "title": "Limited Offer",
    "body": "Get 20% off today only."
  }
}
```

```json
// Bottom sheet
{
  "command": "SHOW_BOTTOM_SHEET",
  "viewId": "my_promo_sheet",
  "args": {
    "title": "Your cart is waiting",
    "cta": "Complete purchase"
  }
}
```

`viewId` must match a component registered in your Digia DSL config.

### How it works under the hood

```
Campaign payload arrives (via CEP plugin)
        │
        ▼
Digia.triggerCampaign(id, content, cepContext)
        │
        ▼
DigiaModule.kt  →  DigiaInstance.onCampaignTriggered(payload)
        │
        ▼
DisplayCoordinator.routeNudge(payload)
        │
        ▼
OverlayController.show(payload)           ← StateFlow update
        │
        ▼
DigiaHost { } recomposes                  ← observes activePayload
        │
        ├─ SHOW_DIALOG        → DialogManager.show(viewId, args)
        └─ SHOW_BOTTOM_SHEET  → BottomSheetManager.show(viewId, args)
                                    │
                                    ▼
                            Compose Dialog / ModalBottomSheet
                            (separate Android Window — above all RN views)
```

### Touch behaviour

When no overlay is active the `DigiaHostComposeView` is a zero-visual-footprint
transparent surface. It explicitly returns `false` from `onTouchEvent` and
`onInterceptTouchEvent`, so **all touches fall through to React Native** with no
interference. When a Dialog or BottomSheet is showing, those create their own
Android `Window` objects with their own touch handling — the host view still
does not intercept anything.

---

## 2. Inline UI — `<DigiaSlotView>`

### What it is

A **React Native view** placed directly inside your screen layout. Its size is
fully controlled by your `style` prop. When the SDK receives a campaign with
command `SHOW_INLINE` for a matching `placementKey`, the native Compose layer
renders the corresponding component **inside the space you allocated**.

```
HomeScreen (ScrollView)
│
├── <ProductHero />
├── <DigiaSlotView placementKey="home_top_banner" style={{ height: 180 }} />
│       └── [Compose renders promo_card here when campaign arrives]
├── <ProductGrid />
├── <DigiaSlotView placementKey="home_mid_banner" style={{ height: 120 }} />
│       └── [renders nothing until a campaign targets this slot]
└── <Footer />
```

### Setup

Place `<DigiaSlotView>` anywhere inside a screen. Give it an explicit `height`
(or use flex) so that Compose has allocated space to render into:

```tsx
import { ScrollView } from 'react-native';
import { DigiaSlotView } from '@digia/engage-react-native';

export function HomeScreen() {
  return (
    <ScrollView>
      <HeroBanner />

      {/* Top inline slot */}
      <DigiaSlotView
        placementKey="home_top_banner"
        style={{ width: '100%', height: 180 }}
      />

      <ProductGrid />

      {/* Mid-page slot */}
      <DigiaSlotView
        placementKey="home_mid_banner"
        style={{ width: '100%', height: 120 }}
      />

      <Footer />
    </ScrollView>
  );
}
```

You can also use it on a Product Detail page:

```tsx
export function ProductScreen() {
  return (
    <ScrollView>
      <ProductImages />
      <ProductDetails />

      <DigiaSlotView
        placementKey="pdp_upsell_banner"
        style={{ width: '100%', height: 100 }}
      />

      <RelatedProducts />
    </ScrollView>
  );
}
```

Multiple slots with **different** `placementKey` values can be mounted
simultaneously across different screens — each reacts independently.

### Campaign payload

```json
{
  "command": "SHOW_INLINE",
  "placementKey": "home_top_banner",
  "componentId": "promo_card",
  "args": {
    "title": "Flash Sale",
    "discount": "20%",
    "cta": "Shop now"
  }
}
```

| Field | Description |
|---|---|
| `command` | Must be `"SHOW_INLINE"` |
| `placementKey` | Must match the `placementKey` prop on the target `<DigiaSlotView>` |
| `componentId` | Compose component registered in your Digia DSL config |
| `args` | Passed directly to the component as named parameters |

### How it works under the hood

```
Campaign payload arrives (via CEP plugin)
        │
        ▼
Digia.triggerCampaign(id, content, cepContext)
        │
        ▼
DigiaModule.kt  →  DigiaInstance.onCampaignTriggered(payload)
        │
        ▼ command == "SHOW_INLINE"
DisplayCoordinator.routeInline(placementKey, payload)
        │
        ▼
OverlayController.slotPayloads[placementKey] = payload   ← StateFlow update
        │
        ▼
DigiaSlot(placementKey) recomposes                        ← observes slotPayloads
        │
        ▼
DUIFactory.CreateComponent(componentId, args)
        │
        ▼
Compose component renders inside the <DigiaSlotView> bounds
```

### Sizing guidance

| Scenario | Recommended approach |
|---|---|
| Fixed banner (always same height) | `style={{ height: 180 }}` |
| Full-width card | `style={{ width: '100%', height: 200 }}` |
| Flex column | `style={{ flex: 1 }}` inside a flex container |
| No campaign active | Compose renders nothing — the JS-allocated space remains |

> **Note:** The Compose layer renders inside whatever bounds React Native
> allocates. If a campaign is not active the view holds its allocated space
> (invisible). If you want zero space when inactive, wrap `<DigiaSlotView>` in
> a conditional that checks your own campaign state, or use a `height: 0` style
> by default and animate in.

### Slot persistence

Inline slots are **sticky** — a campaign stored for a `placementKey` persists
in the SDK's `slotPayloads` state across screen navigations. The content
reappears whenever the same `<DigiaSlotView placementKey="…">` is mounted
again (e.g., when the user returns to the screen). The slot is only cleared
when the server sends a campaign invalidation event or the SDK is torn down.

---

## Choosing between the two modes

| Use case | Mode |
|---|---|
| Promotional dialog after login | **Overlay** (`SHOW_DIALOG`) |
| Onboarding bottom sheet | **Overlay** (`SHOW_BOTTOM_SHEET`) |
| Homepage hero banner | **Inline** (`SHOW_INLINE`) |
| Mid-feed sponsored card | **Inline** |
| Product page upsell strip | **Inline** |
| Full-screen interstitial | **Overlay** (`SHOW_DIALOG` with full-screen component) |
| Cart abandonment reminder | **Overlay** (`SHOW_BOTTOM_SHEET`) |

Both modes can be active at once — a campaign can target a `<DigiaSlotView>`
while a Dialog overlay is also showing for a separate campaign.

---

## Complete minimal example

```tsx
// App.tsx
import React, { useEffect } from 'react';
import { StyleSheet, ScrollView, View, Text } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { Digia, DigiaHostView, DigiaSlotView } from '@digia/engage-react-native';

// ── Root: mount DigiaHostView once ───────────────────────────────────────────
export default function App() {
  useEffect(() => {
    Digia.initialize({ apiKey: 'YOUR_API_KEY' });
  }, []);

  return (
    <NavigationContainer>
      {/* Overlay host — transparent, covers full screen, passes all touches through */}
      <DigiaHostView style={StyleSheet.absoluteFillObject} />

      <Stack.Navigator>
        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen name="Product" component={ProductScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}

// ── HomeScreen: inline slots ─────────────────────────────────────────────────
function HomeScreen() {
  return (
    <ScrollView>
      <Text style={styles.heading}>For You</Text>

      {/* Inline slot — Compose renders here when a SHOW_INLINE campaign arrives */}
      <DigiaSlotView
        placementKey="home_hero"
        style={{ width: '100%', height: 200 }}
      />

      <ProductList />

      <DigiaSlotView
        placementKey="home_mid"
        style={{ width: '100%', height: 120 }}
      />
    </ScrollView>
  );
}

// ── ProductScreen: inline slot on PDP ────────────────────────────────────────
function ProductScreen() {
  return (
    <ScrollView>
      <ProductImages />
      <ProductInfo />

      <DigiaSlotView
        placementKey="pdp_upsell"
        style={{ width: '100%', height: 100, marginVertical: 12 }}
      />

      <RelatedProducts />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  heading: { fontSize: 22, fontWeight: '700', margin: 16 },
});
```

---

## iOS note

On iOS, both `<DigiaHostView>` and `<DigiaSlotView>` render as transparent
`<View>` placeholders. The Digia overlay system is built on Jetpack Compose
which is Android-only. When iOS support is added, only the native layer changes
— no TypeScript or JSX changes are required.
