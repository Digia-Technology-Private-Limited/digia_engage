# Digia iOS SDUI SDK Plan

## Summary
- Build two public products in `digia_engage/iOS`: `DigiaEngage` and separate `DigiaExpr`.
- Match Flutter runtime as the behavioral/rendering oracle for the same Studio/AppConfig on the same device class.
- Keep v1 SwiftUI-first, iOS 16+, iPhone-first, core-SDK-first. No real CEP plugin in phase 1; use a fake/test plugin and demo harness.
- Use strict TDD vertical slices: one behavior, one failing test, minimal implementation, refactor only on green.
- Defer CocoaPods/SPM/Carthage release work until the SDK is stable, but design package boundaries/resources/test targets so later publishing is straightforward.

## Public API / Runtime Contracts
- Public core surface:
  - `Digia.initialize(_ config: DigiaConfig)`
  - `Digia.register(_ plugin: DigiaCEPPlugin)`
  - `Digia.setCurrentScreen(_ name: String)`
  - `DigiaHost { ... }`
  - `DigiaSlot("placement_key")`
  - `.digiaScreen("screen_name")`
  - `DigiaScreen("screen_name")` helper view
- Public config mirrors Flutter closely:
  - `DigiaConfig(apiKey, logLevel, environment, flavor, networkConfiguration?, developerConfig?)`
  - `DigiaLogLevel`, `DigiaEnvironment`
  - `Flavor.debug(branchName?)`, `.staging`, `.versioned(version)`, `.release(initStrategy, appConfigPath, functionsPath)`
  - release init strategies: `networkFirst(timeout)`, `cacheFirst`, `localFirst`
- Public plugin contract mirrors current cross-platform contract:
  - `DigiaCEPPlugin`
  - `DigiaCEPDelegate`
  - `InAppPayload`
  - `DigiaExperienceEvent`
- `DigiaExpr` is a separate public package/product with parser, AST, evaluator, scope context chain, interpolation, standard library, and parity fixtures against current Dart behavior.

## Implementation Changes
- Core architecture:
  - Static facade + internal singleton/coordinator.
  - Separate subsystems for config loading, state runtime, renderer registry, action execution, display coordination, diagnostics, analytics stub.
  - SwiftUI-first rendering; UIKit gets explicit screen forwarding only, no swizzling in v1.
- Config/runtime parity:
  - Reproduce Flutter config resolver behavior and backend paths/headers semantics.
  - Support multiple config sources exactly like Flutter release/debug/versioned/staging flow.
  - Make local fixtures a first-class source for development/tests/demo, not a separate fake architecture.
- State/layout semantics:
  - Support full Studio-config runtime state, including persisted/app-level state, page state, component state, widget-local state, defaults, and component/page state initialization.
  - Use a deterministic scope chain for expr evaluation:
    env/functions -> app/persisted state -> page args/state -> component args/state -> widget/repeat local scope.
  - Layout/render P0 includes the minimum subset needed for real parity:
    page/component composition, default/common props, visibility, container, row, column, stack, repeat/list-style data rendering, text, rich text, image, button, text input, lottie, carousel, dialog, bottom sheet, fullscreen, slot rendering.
  - Unsupported widgets/actions in v1 must emit diagnostics and clear dev-visible fallback behavior; they are not silent no-ops.
- Actions/navigation P0:
  - `navigateToPage`, `navigateBack`, `showDialog`, `dismissDialog`, `showBottomSheet`, `hideBottomSheet`, `showToast`, `copyToClipboard`, `share`, `setState`, `rebuildState`
  - page navigation targets Digia-defined pages only in v1
- Delivery order, TDD slices:
  1. `DigiaExpr` tracer bullet: parse/eval simple literals and variable lookup.
  2. `DigiaExpr` parity expansion: math, strings, null/error cases, nested lookup, interpolation, helper functions, JSON/object-path access, method calls.
  3. Config tracer bullet: load one known AppConfig fixture through the same resolver shape as Flutter.
  4. State tracer bullet: one page with args + page state + expr binding.
  5. Renderer tracer bullet: one `Text` page rendered from config in SwiftUI.
  6. Layout primitives: container/row/column/stack/repeat with parity-focused behavior tests.
  7. Actions/state mutation: button + text field + `setState`/`rebuildState`.
  8. Public API: `Digia.initialize/register/setCurrentScreen`, `DigiaHost`, `DigiaSlot`, screen tracking surfaces.
  9. Demo app + fake plugin + manual live smoke using project id `698b1b7979d23afa242dcc7d`.

## Test Plan
- Use [Swift Testing](https://developer.apple.com/documentation/testing) for package-heavy logic:
  - `DigiaExpr`
  - config resolver/strategy selection
  - parser/model decoding
  - state runtime and persistence
  - action execution
  - coordinator/plugin lifecycle
- Use XCTest/XCUITest for thin integration coverage:
  - SDK init/register lifecycle
  - `DigiaHost` overlay presentation
  - `DigiaSlot` inline rendering
  - screen tracking surfaces
  - page navigation/dialog/bottom sheet/text field flows
- Core tests:
  - init idempotency
  - register/replace/teardown semantics
  - degraded mode before plugin registration
  - config flavor behavior
  - persisted/app state restore across relaunch
  - diagnostics on unsupported/invalid config
- Widget tests:
  - one fixture-driven behavioral test per P0 widget
  - assert rendered text/content, visibility, action behavior, state mutation, and measurable layout semantics through public surfaces only
  - no tests against private storage or internal view tree details
- Release gate for each P0 feature:
  - deterministic fixture-based automated tests green
  - demo app smoke green
  - manual live-backend verification green on one canonical iPhone simulator/device using the sample project id

## Assumptions / Defaults
- Golden reference is current Flutter runtime, not Studio preview.
- v1 is a frozen P0 parity subset, not full Studio capability coverage.
- SwiftUI is first-class; UIKit UI wrappers are deferred.
- UIKit screen tracking is explicit only in v1.
- Manual visual QA is acceptable; no paid visual regression tooling.
- Canonical manual QA target defaults to one standard 6.1-inch iPhone simulator.
- Lint/format baseline:
  - [SwiftLint](https://github.com/realm/SwiftLint)
  - Apple [swift-format](https://github.com/swiftlang/swift-format)
- Distribution later:
  - keep source-first repo layout now
  - when packaging starts, use Apple’s XCFramework/SPM guidance, CocoaPods `test_spec`/lint flow, and Carthage XCFramework release artifacts:
    [Apple Swift Testing](https://developer.apple.com/documentation/testing)
    [Apple binary frameworks as Swift packages](https://developer.apple.com/documentation/xcode/distributing-binary-frameworks-as-swift-packages)
    [CocoaPods test specs](https://guides.cocoapods.org/using/test-specs.html)
    [CocoaPods podspec syntax](https://guides.cocoapods.org/syntax/podspec.html)
    [Carthage XCFramework guidance](https://github.com/Carthage/Carthage)

## Unresolved Questions
- None.
