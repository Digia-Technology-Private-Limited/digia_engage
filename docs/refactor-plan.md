# Digia Android Core Refactor Plan

## Problem
Legacy public surface (`DigiaUI` + app builder pattern) leaks implementation details and blocks new cross-CEP plugin contract.

## Solution
Introduce `Digia` static facade and internal runtime/coordinator, while keeping `DUIFactory` internal for rendering only.

## Implemented Slices (TDD Vertical)
1. Core contract + coordinator: register/setup/replace teardown, screen forwarding, pre-register warnings.
2. Canonical translator: flat content map to typed `RenderInstruction`.
3. Host rendering: `DigiaHost` routes typed instructions to `DialogManager`/`BottomSheetManager` + `DUIFactory`.
4. Slot and screen APIs: `DigiaSlot` by placement key; `DigiaScreen` explicit screen tracking.
5. Lifecycle teardown: plugin teardown on replacement and `ProcessLifecycleOwner` destroy hook.

## Test Decisions
- JVM-first tests for lifecycle/plugin/degraded-mode and translation behavior.
- Minimal Compose instrumentation smoke test for `DigiaHost`/`DigiaSlot`/`DigiaScreen` composition.

## Out of Scope
- CEP-specific advanced inline routing
- Additional facade namespaces beyond phase-1 methods
