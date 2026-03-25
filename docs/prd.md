# Digia Android Core Refactor PRD (Phase 1)

## Objective
Ship breaking Android core API refactor to new `com.digia.engage.Digia` facade with CEP plugin contract and Compose host/slot/screen primitives.

## Scope
- Public API: `Digia.initialize`, `Digia.register`, `Digia.setCurrentScreen`
- Public Compose: `DigiaHost`, `DigiaSlot`, `DigiaScreen`
- Core plugin contract: `DigiaCEPPlugin`, `DigiaCEPDelegate`, `InAppPayload`, `DigiaExperienceEvent`
- Degraded mode pre-register handling: accept calls, warning log, drop rendering/events
- Host rendering path: canonical map -> typed translator -> dialog/bottom-sheet managers + `DUIFactory`
- CTA click semantics: explicit only (`Clicked(elementId?)` path)
- Lifecycle teardown: plugin replacement and process lifecycle destroy hook

## Non-Goals
- Multi-plugin support
- Queueing before register
- Advanced inline routing outside canonical slot instruction

## Success Criteria
- Core contract compiles and is consumable by plugin repo
- JVM tests validate lifecycle and degraded mode behavior
- Compose surface composes with minimal instrumentation coverage
- Old `DigiaUI` entry surface no longer public API
