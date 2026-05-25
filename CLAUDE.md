# Digia Engage — Codebase Guide

Product name: **Digia Engage**. Always use this name in code, docs, and UI copy.

## What this repo is

Multi-platform SDK for server-driven in-app experiences (tooltips, spotlights, carousels, surveys). Four platform SDKs live here as a monorepo.

## Repo structure

```
android/        Native Android SDK (Kotlin + Jetpack Compose)
iOS/            Native iOS SDK (git submodule → digia_engage_iOS repo)
flutter/        Flutter package — DO NOT TOUCH (maintained separately)
react-native/   React Native bridge (JS + native Android/iOS bridges)
ai_docs/        Architecture docs, design specs, reference material for AI sessions
```

## Rules

- **Never commit or push without explicit approval** from the user. Make local changes freely, but always show what changed and ask before committing.
- **No GPT-invented URLs** — only use links that appear in the existing codebase.

## Campaign types

The SDK renders four campaign types, all server-configured:

| Type | Trigger | Renderer |
|------|---------|----------|
| `guide` | Overlay anchored to a UI element | `GuideRenderer` (Android), `DigiaProvider.tsx` (RN) |
| `nudge` | Full overlay dialog/sheet | `DigiaOverlayController` → Compose |
| `inline` | `DigiaSlot` in a scroll view | `VWCarousel` via `DigiaSlot` composable |
| `survey` | Full overlay, branching questionnaire | `SurveyRenderer` + `SurveyViewModel` (Android) |

Routing entry point on Android: `DigiaInstance.routeCampaign()`.

## Android SDK architecture

```
com.digia.engage/
  Digia.kt                    Public static facade (init, register, setCurrentScreen)
  DigiaViewApi.kt             Composable entry points (DigiaHost, DigiaScreen, DigiaSlot)
  DigiaComposableApi.kt       Composable widget exports (carousel, etc.)
  DigiaCEPPlugin.kt           Plugin interface (implemented by CEP integrations)
  DigiaCEPDelegate.kt         SDK → plugin callback interface

  internal/
    DigiaInstance.kt          Internal singleton; owns all SDK state
    DigiaOverlayController.kt Manages overlay/slot state flows
    DisplayCoordinator.kt     Routes events to CEP plugins + analytics
    CampaignFetcher.kt        Fetches campaign config from Digia backend
    CampaignStore.kt          In-memory campaign cache
    AnchorRegistry.kt         Tracks View positions for tooltip/spotlight anchoring
    GuideOrchestrator.kt      Manages active guide state (step index)
    SurveyOrchestrator.kt     Manages active survey state (one at a time)
    SurveyLogicHandler.kt     Branching logic (linear / by-condition / by-parent)
    AnalyticsClient.kt        Forwards events to registered CEP plugins
    PluginRegistry.kt         Manages DigiaCEPPlugin lifecycle

    model/                    Pure data classes (no Android imports)
      CampaignModel.kt
      SurveyConfigModel.kt    Full survey graph (blocks, nodes, branching)
      InlineCarouselConfig.kt
      GuideConfigModel.kt / GuideStepModel.kt

    ui/
      GuideRenderer.kt        Compose Popup rendering for tooltip + spotlight
      SurveyRenderer.kt       Survey overlay (modal or bottom sheet)
      SurveyViewModel.kt      Survey answer state
      SurveyQuestionWidgets.kt Per-question-type Compose widgets
```

The `digia-ui/` module (separate Gradle module) provides the `VWCarousel` and other
virtual-widget rendering primitives used by `DigiaComposableApi.kt`.

## iOS SDK (submodule)

`iOS/` is a git submodule pointing to `digia_engage_iOS`. Changes to iOS must be made
in that repo and pulled here as a submodule update. Do not edit files inside `iOS/`
directly in this repo.

iOS has surveys but they may not be complete. See `ai_docs/` for parity status.

## React Native bridge

```
react-native/src/
  Digia.ts              SDK lifecycle; routes guide → JS, survey/inline → native bridge
  DigiaProvider.tsx     JS-side guide/tooltip/spotlight renderer (uses @floating-ui/core)
  DigiaSlotView.tsx     Native slot view wrapper
  DigiaHostView.tsx     Native overlay host wrapper
  NativeDigiaEngage.ts  Codegen native module spec
  templateTypes.ts      TypeScript types for TooltipConfig, SpotlightConfig, CarouselConfig, SurveyTemplateConfig

react-native/android/   Android bridge (DigiaModule.kt, DigiaSlotViewManager.kt, etc.)
react-native/ios/       iOS bridge (DigiaModule.swift, etc.)
```

Survey and inline campaigns are forwarded to the native SDK via `nativeDigiaModule.triggerCampaign()`.
Guide/tooltip/spotlight campaigns are rendered entirely in JS by `DigiaProvider.tsx`.

## Feature status

| Feature | Android | iOS | React Native |
|---------|---------|-----|-------------|
| Tooltips / Guides | ✅ | ✅ | ✅ (JS renderer) |
| Spotlights | ✅ | ✅ | ✅ (JS renderer) |
| Inline Carousels | ✅ | ✅ | ✅ (native bridge) |
| Surveys | ✅ | partial | ✅ Android (native bridge) |

## ai_docs/ index

| File | Contents |
|------|----------|
| `API_DESIGN_AND_ARCH.md` | Full public API surface design spec |
| `PLUGIN_INTERFACE.md` | CEP plugin integration contract (comprehensive) |
| `prd.md` | Product requirements |
| `widget_parity_flutter_ios.md` | Widget parity investigation (iOS ↔ Flutter) |
| `bottom_sheet_dialog_findings.md` | Bottom sheet behavior research |
| `android_layout_widgets.md` | Android widget layout specifications |
| `android_renderpayload_fixes.md` | Render payload parsing fixes |
| `android_renderpayload_usage.md` | How to work with render payloads |
| `appConfig.json` | Example campaign config API response (reference) |

## Key invariants

- `internal/` packages are never part of the public API. Never expose internal types in public function signatures.
- `internal/model/` classes have zero Android/Compose imports — they are pure data.
- `DigiaInstance` is an `internal object` (singleton) — public callers only go through `Digia.kt`.
- The `digia-ui` Gradle module provides the widget rendering engine. It is a dependency of `digia-engage`, not the other way around.
- Survey campaigns block: only one survey can be active at a time (`SurveyOrchestrator.start()` returns false if one is already shown).
