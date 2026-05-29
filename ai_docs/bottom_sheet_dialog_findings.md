# Bottom Sheet & Dialog Fix Findings

## Architecture Overview

### Flutter
- **Action**: `flutter/lib/src/framework/actions/showBottomSheet/processor.dart` → calls `presentBottomSheet()` in `navigation_util.dart`
- **Navigation util**: `flutter/lib/src/framework/utils/navigation_util.dart` — wraps `showModalBottomSheet`
- **Dialog**: `flutter/lib/src/framework/actions/showDialog/processor.dart` → calls `presentDialog()` in `navigation_util.dart`

### Android
- **State manager**: `android/digia-ui/.../framework/bottomsheet/BottomSheetManager.kt` — holds `MutableStateFlow<BottomSheetRequest?>`
- **Host composable**: `BottomSheetHost()` in same file — renders `ModalBottomSheet` when state is non-null
- **Processor**: `ShowBottomSheetProcessor` in `ShowBottomSheetAction.kt` — calls `DigiaUIManager.getInstance().bottomSheetManager.show(...)`
- **Integration point**: `DigiaHost` in `DigiaComposableApi.kt` — renders `BottomSheetHost` and `DialogHost` in a Box above content
- **Manager init**: `DigiaInstance.ensureRenderEngineInitialized()` creates the managers before `isRenderEngineReady = true`
- **Dialog**: `DialogManager.kt`, `DialogHost()`, `ShowDialogProcessor` (same pattern)

### iOS
- **State**: `DigiaOverlayController` (ObservableObject) holds `@Published activeBottomSheet`
- **Processor**: `show_bottom_sheet_action_processor.swift` → creates `DigiaBottomSheetPresentation`, calls `overlayController.showBottomSheet(...)`
- **Rendering**: `DigiaHost.swift` — ZStack overlay; OR `UIHostingController` with `.overFullScreen`
- **View**: `DigiaModalBottomSheetRootView` in `navigation_util.swift` — fully custom SwiftUI view
- **Animation**: `BottomSheetTransitionModel` — custom spring animations for show/dismiss
- **Dialog**: Same pattern with `DigiaDialogPresentation`, `DigiaDialogRouteRootView`

---

## Bug #1: iOS — maxHeightRatio always shows at the given ratio

### Root Cause
In `navigation_util.swift`, `DigiaModalBottomSheetRootView`:

```swift
content()
    .fixedSize(horizontal: false, vertical: true)  // INNER fixedSize
    .frame(maxWidth: .infinity, alignment: .topLeading)
```

and outer:

```swift
VStack(spacing: 0) { ... }
    .padding(...)
    .fixedSize(horizontal: false, vertical: true)  // OUTER fixedSize
    .frame(maxWidth: sheetMaxWidth, maxHeight: maxAllowedHeight, alignment: .top)
```

`.fixedSize(vertical: true)` passes **infinity** as the proposed height to the child. The server-driven `DigiaPresentationView` (which wraps scaffold/column layouts) sees infinity and fills it (acts as `mainAxisSize: max`). It returns infinity as its ideal height. This infinity is then capped at `maxAllowedHeight` by `frame(maxHeight:)`, so the sheet **always** shows at `maxAllowedHeight`.

### Fix
Remove **both** `.fixedSize(horizontal: false, vertical: true)` modifiers (inner and outer). Just use `frame(maxWidth: sheetMaxWidth, maxHeight: maxAllowedHeight, alignment: .top)` directly.

**Why this works:**
- `frame(maxHeight: maxAllowedHeight)` proposes `maxAllowedHeight` (not infinity) to VStack
- Server-driven content with `mainAxisSize: max` fills `maxAllowedHeight` → sheet is at cap ✓
- Server-driven content with `mainAxisSize: min` / small content → takes only what it needs → sheet is shorter than cap ✓

**File to edit**: `/Users/adityachoubey/Code/digia_engage_ios/Sources/DigiaEngage/api/internal/framework/utils/navigation_util.swift`

Lines ~190-200:
```swift
// BEFORE:
VStack(spacing: 0) {
    if presentation.showDragHandle {
        DigiaBottomSheetDragHandleView()
    }
    content()
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: .topLeading)
}
.padding(.bottom, presentation.useSafeArea ? geo.safeAreaInsets.bottom : 0)
.fixedSize(horizontal: false, vertical: true)
.frame(maxWidth: sheetMaxWidth, maxHeight: maxAllowedHeight, alignment: .top)

// AFTER:
VStack(spacing: 0) {
    if presentation.showDragHandle {
        DigiaBottomSheetDragHandleView()
    }
    content()
        .frame(maxWidth: .infinity, alignment: .topLeading)
}
.padding(.bottom, presentation.useSafeArea ? geo.safeAreaInsets.bottom : 0)
.frame(maxWidth: sheetMaxWidth, maxHeight: maxAllowedHeight, alignment: .top)
```

---

## Bug #2: Android — Bottom sheet "shown on page before" / clicking does nothing

### Root Cause (Hypothesis A — most likely)
The example `MainActivity.kt` uses `DigiaHost { DigiaInitialPage() }`. The "homepage" page has buttons that fire `Action.showBottomSheet`. These trigger `ShowBottomSheetProcessor` which calls `bottomSheetManager.show(...)`. The `BottomSheetHost` in `DigiaHost` should pick this up...

BUT `ModalBottomSheet` in Material3 Compose renders via a `Popup` window. The `Popup` creates a new Android Window (Dialog). If there is any issue with window ownership or z-ordering, it may render behind the current content.

**Confirmed potential issue**: The `BottomSheetManager.kt` `BottomSheetHost` composable uses:
```kotlin
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

LaunchedEffect(request.showToken) {
    sheetState.show()
}
```

The sheet starts as **Hidden** (default initial value) and needs `sheetState.show()` in a `LaunchedEffect` to animate visible. The `ModalBottomSheet` itself is in the composition (so the Dialog window is created), but it's invisible until `show()` is called.

If `show()` is being called but the animation isn't completing or is being interrupted, the sheet would be "invisible" or "behind" other content.

### Root Cause (Hypothesis B)
If the user navigates pages using `DigiaUINavigationActivity` (a separate Activity launched via `CreateNavHost()`), that Activity has **no** `DigiaHost`, `BottomSheetHost`, or `DialogHost`. Any `bottomSheetManager.show()` calls update the StateFlow, but `BottomSheetHost` is only in `MainActivity`. The ModalBottomSheet would then appear in `MainActivity`'s window — visible only after returning to it.

**Check**: Does the homepage have a "navigate" action to a different page? From `appConfig.json` — the homepage buttons fire `Action.showBottomSheet` directly (no navigate actions). So navigation to another Activity is NOT happening.

### Fix A — Remove `LaunchedEffect`; use `sheetState.show()` directly in composition
Instead of starting as Hidden and calling `show()` in LaunchedEffect, start the sheet as `SheetValue.Expanded`:

```kotlin
// In BottomSheetHost:
currentRequest?.let { request ->
    ModalBottomSheet(
        onDismissRequest = bottomSheetManager::dismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            initialValue = SheetValue.Expanded  // Start expanded immediately
        ),
        ...
    ) { ... }
}
```

Or alternatively, call `sheetState.expand()` if there's a visibility issue.

### Fix B — Check dragHandle parameter
```kotlin
dragHandle = { null },
```
This is a no-op composable (not null), so the drag handle space might still be allocated. Change to:
```kotlin
dragHandle = null,
```
(literal null, not a lambda returning null)

### File to edit
`/Users/adityachoubey/Code/digia_engage/android/digia-ui/src/main/java/com/digia/digiaui/framework/bottomsheet/BottomSheetManager.kt`

---

## Bug #3: borderWidth Inconsistency

### Flutter
In `navigation_util.dart` `presentBottomSheet`:
```dart
border: To.border((
    style: as$<String>(style['borderStyle']),
    width: as$<double>(style['borderWidth']),
    color: borderColor,
    strokeAlign: To.strokeAlign(as$<String>(style['strokeAlign'])) ?? StrokeAlign.center,
)),
```
Then applied as `BoxDecoration(border: border)` on a `Container`. This draws the border INSIDE the container.

### Android
`BottomSheetManager.kt`:
```kotlin
val resolvedBorderWidth = (request.borderWidth ?: 0f)

// Applied as:
Modifier.border(resolvedBorderWidth.dp, resolvedBorderColor, shape)
```
The `Modifier.border` draws OUTSIDE the clip shape by default.

### iOS
`navigation_util.swift`:
```swift
if presentation.shouldDrawBorder, let borderColor = presentation.borderColor {
    clipShape.stroke(borderColor, lineWidth: presentation.effectiveBorderWidth)
}
```
iOS applies border via `.overlay { clipShape.stroke(...) }`. This strokes along the edge (centered by default, half inside/half outside).

### Cross-Platform Alignment Needed
All three platforms draw the border differently:
- Flutter: inside `Container` decoration → border is inside the shape
- Android: `Modifier.border(...)` → depends on implementation (usually inside clip, outside visible)
- iOS: `.stroke(...)` → centered on the clip boundary

This may cause visual discrepancies. The fix should make them all use the same border positioning (recommend: **inside** the shape boundary, matching Material3 spec).

---

## Files That Need Changes

| Platform | File | Change |
|----------|------|--------|
| iOS | `Sources/DigiaEngage/api/internal/framework/utils/navigation_util.swift` | Remove `fixedSize` from VStack and content in `DigiaModalBottomSheetRootView` |
| Android | `framework/bottomsheet/BottomSheetManager.kt` | Fix sheet initial state (Hidden→Expanded) or remove LaunchedEffect; fix `dragHandle = null` |
| Android | `framework/dialog/DialogManager.kt` | (Review for similar issues) |
| Flutter | `framework/utils/navigation_util.dart` | (Generally working, minor borderWidth alignment) |

---

## Additional Notes

### Android `DigiaUINavigationActivity`
`DigiaUINavigationActivity.kt` (used when `CreateNavHost()` is called) has **no** `BottomSheetHost` or `DialogHost`. If navigation ever transitions to this Activity, bottom sheets/dialogs won't work. Fix: Add `BottomSheetHost` and `DialogHost` in `DigiaUINavigationContent`.

### Android — `BottomSheetHost` maxHeight
The current `BottomSheetManager.kt` default `maxHeightRatio = 1f` (not `9/16`), but `ShowBottomSheetProcessor` defaults to `9.0/16.0`. This inconsistency means if the style doesn't specify `maxHeight`, the processor sends `9/16` correctly. But `ShowUIAction()` in `DUIFactory.kt` hardcodes `0.7F`. These should all use the same default.

### Flutter — `navigation_util.dart` `SafeArea`
The Flutter `presentBottomSheet` wraps content in `SafeArea` always (not conditional on `useSafeArea`). The `useSafeArea` flag is only passed to `showModalBottomSheet`'s `useSafeArea` param (which affects Flutter's own safe area handling), but the inner `SafeArea()` widget always runs. This may cause double safe area insets in some cases.

---

## Testing appConfig.json
Test page: `homepage` (initial route) — has 9 buttons for bottom sheet tests:
1. Bottom Sheet (Default+Empty) — id: `blankcomponent-JcXena`, no style
2. Bottom Sheet (Config+Empty) — id: `blankcomponent-JcXena`, bgColor+barrierColor+border
3-9. Various configurations with `savingsstreak-XuZSzp`, `fixedsize-PB2vnU`, `productgrid-IERuPh` components

Dialog test page: `dialog-M7ebll`
