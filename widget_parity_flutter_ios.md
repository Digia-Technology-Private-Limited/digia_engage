# Flutter/iOS Widget Parity Notes

## Scope covered in this chat

- Investigated `digia/button` parity between:
  - Flutter: `flutter/lib/src/framework/widgets/button.dart`
  - iOS: `iOS/Sources/DigiaEngage/api/internal/framework/widgets/button.swift`
- Also traced an apparent `Column.spacing` mismatch that turned out to be mostly a button height mismatch.

## Main learnings

### 1. Do not assume the mismatch is in the obvious widget

The first visible mismatch looked like:
- button width/padding mismatch
- column spacing mismatch

But the actual causes were spread across:
- button layout
- icon slot behavior
- typography token resolution
- line height handling
- Material tap-target height

The visible symptom was not enough to identify the real cause.

### 2. Flutter `ElevatedButton` behavior is not just “padding + background + border radius”

Relevant Flutter defaults and behavior found during investigation:
- `ElevatedButton` applies Material defaults beyond user-supplied style.
- Default constraints/tap target matter.
- Material 3 defaults differ from hand-rolled SwiftUI assumptions.
- Flutter logical pixels are not the issue here; DPR was a red herring.

Important practical takeaway:
- parity work should compare actual rendered size and actual child layout, not just declared props.

### 3. Empty icon objects still affect Flutter layout

This was the most important width bug.

The JSON had:
- `leadingIcon: { iconData: {}, iconSize: 16, ... }`
- `trailingIcon: { iconData: {}, iconSize: 16, ... }`

What happened:
- Flutter still built icon widgets for these props.
- Even when `iconData` resolved to `null`, the icon widget still consumed layout width.
- iOS originally dropped those icons entirely.

Result:
- Flutter button content width was much wider.
- iOS button looked like it had less horizontal padding, even when explicit padding was `0`.

Fix:
- reserve empty icon slots on iOS when icon props exist, even if no concrete icon resolves.

### 4. Typography token resolution matters more than inline font overrides

The JSON used:
- `textStyle.fontToken.value = "headingSmall"`
- and also inline `font.size = 11`

Flutter behavior:
- when `fontToken.value` exists, Flutter uses the token style
- inline overrides in `fontToken.font` are effectively ignored in that path

Token used:

```json
"headingSmall": {
  "size": 20,
  "weight": "medium",
  "font-family": "Inter",
  "height": 1.25,
  "style": "normal"
}
```

iOS originally:
- was not decoding `theme.fonts`
- was not honoring `fontToken.value`
- was using inline font descriptor data instead

Fix:
- decode `theme.fonts`
- resolve `fontToken.value` first
- use token descriptor for text/button/rich text rendering

### 5. Line height changes visible widget spacing

What looked like wrong `Column.spacing = 12` was mostly:
- each iOS button being shorter than Flutter

Cause:
- Flutter text style honored token line height (`height = 1.25`)
- iOS text path initially ignored line height

Effect:
- every row became shorter on iOS
- more rows fit on screen
- it looked like vertical spacing was smaller

Fix:
- apply resolved line height in the shared SwiftUI text path
- ensure button label respects the same text metrics

### 6. Material tap target height can look like spacing

Another major source of “column spacing” mismatch:
- Flutter rows effectively occupied `48pt`
- iOS button was only `40pt`

This was not the explicit `Column.spacing` value.

Fix:
- keep Material-like visual button height
- also reserve a `48pt` minimum tappable height on iOS so the list consumes the same vertical space as Flutter

### 7. Sample app setup parity matters

There was a period where Flutter and iOS sample setups were not equivalent.

Important lesson:
- before doing visual parity work, make sure both sample apps are using equivalent resource/font setup paths
- otherwise the screenshot comparison can be misleading

## Approaches tried

### Approach 1: Match button visually by hand

Changes attempted:
- remove forced full-width behavior on iOS
- adjust shape
- adjust default padding
- adjust elevation/shadow

Why it was insufficient:
- the largest deltas were not just button padding
- typography and icon-slot behavior still made the widget measure differently

### Approach 2: Read Flutter framework internals

Investigated:
- Flutter repo widget code
- Flutter SDK `ElevatedButton`
- `ButtonStyleButton`
- Material defaults

Why this helped:
- ruled out bad assumptions
- clarified which differences were Material defaults vs app/widget JSON behavior

### Approach 3: Compare actual runtime measurements with debug probes

This was the most useful technique.

Added temporary logging in Flutter and iOS to measure:
- content size
- final button size
- resolved width/height
- resolved padding

Critical finding from logs:
- Flutter content width for `Actions`: about `105.24`
- iOS content width for `Actions`: about `68`

That proved the mismatch existed before outer layout and before column spacing.

### Approach 4: Validate hypotheses against the exact JSON

The exact payload mattered:
- empty icon objects
- tokenized text style
- `padding: "0,0,0,0"`
- `borderStyle: "none"`

This avoided chasing generic `ElevatedButton` behavior when the real difference came from how this specific payload interacted with the framework.

## Concrete fixes that were made

### Typography/token fixes

- iOS now decodes `theme.fonts`
- iOS resolves `fontToken.value` before inline descriptor data
- iOS accepts token shapes like:
  - `font-family`
  - `style: "normal"` / `"italic"`
  - object-valued `font-family` with `primary` / `secondary`

### Font fixes

- bundled official Inter TTF files into iOS package resources
- updated iOS font factory to resolve Inter properly

### Button fixes

- removed some incorrect early SwiftUI assumptions
- aligned button shape handling with Flutter data
- reserved empty icon slots when icon props exist
- added line-height-aware text sizing
- added `48pt` minimum row/tap height to match Flutter’s effective row height

## What turned out to be red herrings

- device pixel ratio / px-to-pt conversion
- assuming `Column.spacing` itself was wrong
- assuming explicit button padding alone explained the width mismatch
- assuming font-family was already equivalent just because the text looked similar

## Recommended parity workflow for future chats

1. Start from the exact JSON for the widget.
2. Inspect Flutter widget implementation.
3. Inspect any Flutter SDK widget the framework wraps (`ElevatedButton`, etc.).
4. Compare:
   - content size
   - final widget size
   - text style token resolution
   - icon slot behavior
   - min size / tap target
5. Add temporary logs early when screenshots are ambiguous.
6. Remove logs after the root cause is proven.

## Specific checks to reuse in future chats

When a widget looks wrong, check these first:

- Is Flutter reserving space for optional children that iOS drops?
- Is `fontToken.value` winning over inline font descriptor?
- Is line height being applied?
- Is min interactive size/tap target affecting total row height?
- Are theme/resource setups in the sample apps actually equivalent?
- Is the mismatch in the widget itself, or in a child’s measurement?

## Temporary debug technique that worked well

For parity debugging, instrument both runtimes to log:
- label / identifier
- inner content size
- final widget size
- resolved padding
- resolved width / height
- resolved font size / line height if relevant

This was much more effective than iterating off screenshots alone.

## Current status at end of this chat

- Button visual width/padding issue was fixed via icon-slot parity.
- The apparent column spacing mismatch was traced to per-row height mismatch and addressed by matching Flutter’s effective minimum row/tap height.
- Debug instrumentation for button size was still present at the end of the chat and should be removed once parity is confirmed.
