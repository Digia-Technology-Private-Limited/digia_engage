## Android Engage + CleverTap Revamp Plan (Doc-Driven, Breaking)

### Summary
Refactor both Android repos to match:
- `API_DESIGN_AND_ARCH.md` for core SDK (`digia_engage/android`)
- `PLUGIN_INTERFACE.md` for plugin SDK (`digia_clevertap/android`)

Locked decisions:
- Public API becomes `Digia` facade (`com.digia.engage`)
- Old `DigiaUI` interface removed (breaking allowed)
- `DUIFactory` retained as internal rendering engine only
- Full phase-1 surface: `initialize/register/setCurrentScreen`, `DigiaHost`, `DigiaSlot`, `DigiaScreen`
- Plugin strict to new contract (`com.digia.engage.clevertap`)
- No CEP advanced inline routing in phase 1
- TDD strict vertical slices, JVM-first + minimal Compose UI tests
- Output PRD + refactor-plan markdown in each repo under `android/docs/prd.md` and `android/docs/refactor-plan.md`
- No GitHub commits/issues

### Public API / Interface Changes

Core SDK (`digia_engage/android`):
- Add public facade:
  - `Digia.initialize(context, DigiaConfig)` (sync API, async internal setup)
  - `Digia.register(plugin: DigiaCEPPlugin)`
  - `Digia.setCurrentScreen(name: String)`
- Add public UI composables:
  - `DigiaHost { ... }`
  - `DigiaSlot(placementKey, modifier?)`
  - `DigiaScreen(name)`
- Add public config/types:
  - `DigiaConfig(apiKey, logLevel, environment)`
  - `DigiaLogLevel`, `DigiaEnvironment`
- Add plugin contract types in core:
  - `DigiaCEPDelegate`
  - `DigiaCEPPlugin` (`identifier/setup/forwardScreen/notifyEvent/teardown`)
  - `InAppPayload(id, content, cepContext)`
  - `DigiaExperienceEvent` (`Impressed`, `Clicked(elementId?)`, `Dismissed`)
- Remove old public `DigiaUI` interface surface
- Keep `DUIFactory` internal

Plugin SDK (`digia_clevertap/android`):
- Replace singleton manager model with class-based plugin:
  - `CleverTapPlugin : DigiaCEPPlugin`
- Internal collaborators:
  - payload mapper
  - CEP event bridge
- Best-effort invalidation callback support (no-op + log when unavailable)
- Use strict lifecycle:
  - teardown on plugin replacement
  - teardown on process lifecycle destroy hook (as spec’d)

### Internal Architecture (Chosen Interface Design)
`Facade + Coordinator` model:
- `Digia` (public static facade)
- `DigiaInstance` (internal singleton)
- `PluginCoordinator` (register/replace/teardown)
- `ExperienceTranslator` (flat canonical map -> typed render instruction)
- `HostRenderer` (typed instruction -> `BottomSheetManager`/`DialogManager` + `DUIFactory`)
- `SlotResolver` (Digia config pipeline for `DigiaSlot`)

Canonical `InAppPayload.content` for MVP:
- Flat instruction schema (`type`, `componentId`, `args`, optional `presentation`)
- Typed model inside core before rendering

Pre-register behavior:
- Log + drop (no queue, no throw)

### Tiny Commit Plan (Working State Per Step)

#### Repo 1: `digia_engage/android`
1. Create new package scaffolding (`com.digia.engage`) and empty public `Digia` facade.
2. Add `DigiaConfig` + enums (`DigiaLogLevel`, `DigiaEnvironment`) and compile.
3. Add internal `DigiaInstance` state model (`NotInitialized/InitializedUnregistered/Active`).
4. Wire `Digia.initialize` -> instance bootstrap (sync facade + async internals).
5. Add plugin contract types (`DigiaCEPPlugin`, `DigiaCEPDelegate`, payload/event types).
6. Implement `Digia.register` with replacement teardown semantics.
7. Implement `Digia.setCurrentScreen` -> plugin `forwardScreen`; add degraded-mode logging.
8. Add typed `RenderInstruction` + `ExperienceTranslator` (flat schema parsing).
9. Add `DigiaHost` composable with manager wiring + warning semantics.
10. Add event emission loop (`Impressed`, explicit CTA `Clicked`, `Dismissed`) -> plugin `notifyEvent`.
11. Add `DigiaSlot` composable using Digia config pipeline slot resolution.
12. Add `DigiaScreen` composable for explicit screen tracking.
13. Move/limit `DUIFactory` visibility to internal integration path.
14. Remove old `DigiaUI` public interface entrypoints.
15. Update Android example app to new `Digia` + `DigiaHost` + `DigiaScreen` + `DigiaSlot`.
16. Add migration-focused docs for breaking change usage.
17. Add PRD and refactor-plan markdown under repo `android/docs`.
18. Run test/build validation.

#### Repo 2: `digia_clevertap/android` (escalated edit scope planned)
1. Create new package scaffolding (`com.digia.engage.clevertap`).
2. Add `CleverTapPlugin` class implementing core `DigiaCEPPlugin`.
3. Implement `setup(delegate)` + CEP callback registration.
4. Implement payload mapping to `InAppPayload` (canonical flat content + `cepContext`).
5. Implement `forwardScreen(name)` mapping to CleverTap screen API.
6. Implement `notifyEvent(event,payload)` mapping to CleverTap impression/click/dismiss APIs.
7. Implement `teardown()` callback deregistration + delegate clear.
8. Add best-effort invalidation hook (if CEP signal available) else log no-op.
9. Remove/retire old singleton manager + old nudge composable path.
10. Add README usage updates for new plugin contract.
11. Add PRD and refactor-plan markdown under repo `android/docs`.
12. Run test/build validation.

### Test Cases and Scenarios (TDD, Vertical Slices)

Core JVM tests:
1. `initialize` transitions state correctly.
2. `register` stores plugin and calls `setup(delegate)`.
3. re-`register` calls previous `teardown` before new setup.
4. `setCurrentScreen` forwards only when plugin active.
5. pre-register calls are accepted but dropped with warning logs.
6. translator maps valid flat content to typed instruction.
7. translator rejects invalid payload shape safely.

Core Compose instrumentation (minimal):
1. `DigiaHost` renders dialog instruction through manager.
2. `DigiaHost` renders bottom sheet instruction through manager.
3. dismissal emits `ExperienceDismissed`.
4. explicit CTA path emits `ExperienceClicked`.

Slot tests:
1. `DigiaSlot` resolves placement from Digia config pipeline.
2. missing placement path behavior (empty/fallback).
3. sizing via Compose modifier honored.

Plugin contract tests (fake delegate/fake CleverTap bridge):
1. setup registers callbacks + stores delegate.
2. incoming CEP payload maps and calls `onCampaignTriggered`.
3. `forwardScreen` calls CEP API.
4. `notifyEvent` routes event mappings correctly.
5. `teardown` deregisters and clears delegate.
6. plugin replacement safety (no stale callback invocation).

### Out of Scope (Phase 1)
- Advanced CEP-driven inline routing extensions
- Multi-plugin simultaneous registration
- Plugin health-check API
- Versioning decision/final release semantics

### Assumptions and Defaults
- Breaking change accepted.
- `DigiaUI` old interface removed.
- `DUIFactory` remains internal engine.
- Package direction:
  - core: `com.digia.engage`
  - plugin: `com.digia.engage.clevertap`
- Compose screen tracking is explicit via `DigiaScreen` (no auto nav hook in phase 1).
- Teardown lifecycle follows documented `ProcessLifecycleOwner` destroy hook + replacement trigger.
- PRD/refactor docs produced as local markdown only (no GitHub issue creation).
- Cross-repo plugin edits require escalated permission when implementation starts.

### Unresolved Questions
1. Versioning/release-number strategy for the breaking rollout.
