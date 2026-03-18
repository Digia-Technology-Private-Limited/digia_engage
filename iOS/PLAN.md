## iOS SDK PRD + Build Plan

### Summary
- Build `digia_engage/ios` as a SwiftUI-first, iPhone-only, iOS 16+ SDK.
- Mirror public Digia API: `Digia.initialize`, `Digia.register`, `Digia.setCurrentScreen`, `DigiaHost`, `DigiaSlot`, `DigiaScreen`.
- Use live Flutter-style AppConfig fetching/resolver contracts, not future `id_key -> CampaignStore` backend.
- Use current live CEP payload model for v1: direct payload fields like `command`, `viewId`/`componentId`, `placementKey`, `args`, `screenId`.
- No state-container/state-tree module in v1.
- `DigiaExpr` is a separate Swift package with parity-focused tests against Dart behavior.

### Public / Private Modules
- Public: `Digia`, `DigiaConfig`, `DigiaHost`, `DigiaSlot`, `DigiaScreen`, `DigiaCEPPlugin`, `DigiaCEPDelegate`, `DigiaError`, `DigiaLogLevel`, `DigiaEnvironment`.
- Internal orchestration: `SDKInstance`, `SDKResolver`, `SDKState`.
- Internal domain: `PluginRegistry`, `AppConfigStore`, `DisplayCoordinator`, `OverlayController`, `ScreenTracker`, `DiagnosticsReporter`, `AnalyticsClient`.
- Internal infra: `NetworkClient`, `ConfigResolver`, config strategy/source abstractions, `SDKLogger`.
- Internal renderer: page/component models, registry, render payload/context, widget renderers, action processors, page navigation/presentation adapters.
- Separate package: `DigiaExpr` parser/evaluator/context/std-lib.
- Internal demo target: SwiftUI demo app with local fixtures first, remote config next.

### Big Milestones
1. **Foundation / Repo Shape**
- Create `digia_engage/ios`.
- Create sibling `DigiaExpr` Swift package.
- Create demo app target.
- Add package boundaries, naming rules, one-type-per-file rule, iPhone-only guardrails, unsupported-device no-op logging.
- Checkpoint: empty SDK compiles, demo app boots, package graph stable.

2. **Config Boot Path**
- Port Flutter config resolver/provider contracts.
- Implement local fixture loading first.
- Implement same strategy vocabulary as Flutter where needed: debug/staging/versioned/release-local-first enough for iOS v1.
- Parse `DUIConfig`, page defs, component defs, REST config, env/functions metadata.
- Replace future `CampaignStore` with `AppConfigStore` backed by loaded AppConfig.
- Checkpoint: app config loads from local JSON; page/component lookup works; bad config errors/logging sane.

3. **DigiaExpr Core**
- Build scanner, parser, AST, evaluator, context chaining, interpolation.
- Port only std-lib needed by renderer P0 + current Flutter-equivalent must-haves; start with parity fixtures from Dart.
- Add heavy parity tests for math, strings, nested object access, JSON path access, interpolation, method calls, `isoFormat`, number/int helpers, null/error cases.
- Checkpoint: Swift `DigiaExpr` passes agreed parity suite against Dart examples.

4. **Renderer Core**
- Build page/component models, registry, render payload, resource/color/text helpers, expression evaluation hooks.
- Support P0 widgets: `Text`, `RichText`, `Container`, `Image`, `Button`, `Stack`, `Row`, `Column`, `Lottie`, `Carousel`, `TextFormField`.
- Unsupported widgets render dev-visible placeholder or loggable fallback per build mode.
- No state-container module; only page/component-local state needed for rerender + form/input behavior.
- Checkpoint: simple text page renders; component renders via registry; sample templates start rendering.

5. **Actions + Navigation**
- Implement P0 actions: `navigateToPage`, `showToast`, `copyToClipboard`, `navigateBack`, `rebuildState`, `setState`, `share`, `showDialog`, `showBottomSheet`, `hideBottomSheet`, `dismissDialog`.
- Page navigation targets Digia-defined pages only.
- `DigiaScreen` is public SwiftUI helper/view-equivalent for screen forwarding.
- Fullscreen Digia pages supported; PiP deferred.
- Checkpoint: page push/pop works; dialog/bottom-sheet presentation works; stateful form demo works.

6. **Digia Surface API**
- Implement `Digia.initialize`, `register`, `setCurrentScreen`.
- Implement `DigiaHost` with `ZStack` overlay observation.
- Implement `DigiaSlot` pull-based inline rendering from `AppConfigStore`/live payload routing model.
- Implement plugin contract parity: `setup`, `forwardScreen`, `notifyEvent`, `healthCheck`, `teardown`, delegate invalidation.
- Checkpoint: demo app can render host overlay, inline slot, and screen tracking through public API only.

7. **CEP Routing + Live Payload Handling**
- Use current payload contract: route by `command`, `viewId`/`componentId`, `placementKey`, `args`, `screenId`.
- Nudge flow: dialog/bottom-sheet/fullscreen.
- Inline flow: slot by placement key.
- Pending payload during init only for nudge.
- Screen mismatch filtering same as Android behavior.
- Checkpoint: mock plugin fires payloads; host/slot react correctly; invalidation dismisses active experience.

8. **Remote Config + Diagnostics + Analytics Stub**
- Add live remote config fetch using Flutter-equivalent resolver path/contracts.
- Diagnostics reporter logs and emits structured health info.
- `AnalyticsClient` contract and event model real; network implementation can start stubbed/gated.
- CEP analytics fanout live from `DisplayCoordinator`.
- Checkpoint: local-first then remote config works; diagnostics visible; CEP event fanout verified.

9. **Template Validation + Hardening**
- Run your existing templates through demo app.
- Bucket failures by widget gap, action gap, expr gap, config contract gap.
- Fix top blockers for P0 ship list only.
- Freeze P1 backlog: `ConditionalBuilder`, `Avatar`, progress indicators, video player, scratch card, story widget, `callRestApi`, fire events, others.
- Checkpoint: agreed P0 template set green.

### Small Checkpoints
- Config milestone: parse initial route; parse pages; parse components; parse env; parse rest; fixture switching; remote switching.
- Renderer milestone: render `Text`; render `Container`; render child layout; render remote/local image; render button tap; render rich text; render text input; render lottie; render carousel.
- Action milestone: each action has one focused demo + one unit/integration test.
- Host/slot milestone: overlay payload renders component by `viewId`; inline payload renders component by `placementKey`.
- Expr milestone: every Dart fixture added in Swift parity suite before renderer depends on it.

### Testing Decisions
- `DigiaExpr` gets the heaviest tests; goal is behavioral parity with Dart package, not implementation similarity.
- Core good tests = external behavior only: parse/eval outputs, routing outputs, rendered surface chosen, action side effects, public API lifecycle, no direct testing of private storage/layout internals.
- Heavy unit tests: `DigiaExpr`, config parsing/resolver, `AppConfigStore`, `PluginRegistry`, `SDKInstance`, `DisplayCoordinator`, action processors.
- Light UI/integration smoke: `DigiaHost`, `DigiaSlot`, `DigiaScreen`, page navigation, dialog, bottom sheet, text field.
- Demo app is required validation artifact at every big milestone.

### Assumptions / Defaults
- iOS 16+, iPhone only, unsupported idioms = graceful no-op + warning.
- SwiftUI only; UIKit support only via internal wrappers where needed, not as first-class public surface.
- `DigiaScreen` helper/view is public; no `.digiaScreen()` modifier planned.
- `AppConfigStore` replaces future `CampaignStore` in v1 naming/behavior.
- Current live payload contract wins over future `id_key` architecture for v1.
- `AnalyticsClient` interface ships now; backend posting can begin stubbed.
- No global state/runtime surface beyond what config boot and page/component-local state require.
- P0 widgets/actions only; everything else explicitly deferred.

### Unresolved Questions
- None.
