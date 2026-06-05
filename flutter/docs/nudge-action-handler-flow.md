# Nudge Action Handler — Structure & Flow

> Status: **structure only.** Button taps are intentionally **not wired yet**.
> `NudgeButton.onClick` is parsed and carried on the model, and the renderer's
> `InkWell.onTap` is a documented no-op. This doc defines the contract the
> handler will implement so the wiring is a small, isolated change.

---

## 1. Where actions come from (the wire)

A nudge is authored in the dashboard and serialized to a native DUI tree. Each
button carries an `onClick` **action flow**: an ordered list of steps.

```jsonc
// digia/button → props.onClick
{
  "steps": [
    { "type": "Action.openUrl", "url": "app://upgrade", "launchMode": "externalApplication" },
    { "type": "Action.hideBottomSheet" }
  ],
  "inkWell": true
}
```

The dashboard only authors a **closed set** of nudge actions (see
`nudge.types.ts` → `NudgeActionType`):

| Dashboard action | Emitted step(s) |
|---|---|
| `dismiss`   | `Action.hideBottomSheet` *(sheet)* / `Action.dismissDialog` *(dialog)* |
| `open_url`  | `Action.openUrl` |
| `deep_link` | `Action.openUrl` **then** dismiss step |
| `none`      | `[]` (no steps) |

This closed set is the whole reason the nudge runtime does **not** pull in the
full DUI `ActionExecutor` / `ActionFlow` engine — see §5.

---

## 2. Layers (SRP)

```
templateConfig.layout
      │
      ▼  NudgeParser            (wire JSON → model; the only layer that knows JSON)
NudgeButton.onClick : Map       (raw flow kept verbatim on the model)
      │
      ▼  NudgeActionParser      (Map → NudgeAction value object)   ← TO BUILD
NudgeAction { kind, url, dismissAfter }
      │
      ▼  NudgeActionHandler     (NudgeAction + context → effect)   ← TO BUILD
effect: pop the nudge / launch a URL
```

Each layer has one reason to change:

- **NudgeParser** — wire format of the tree.
- **NudgeActionParser** — wire format of the `onClick` flow.
- **NudgeActionHandler** — what an action *does* at runtime.
- **NudgeView** — how a button *looks* and where the tap originates.

---

## 3. The model to add

```dart
enum NudgeActionKind { none, dismiss, openUrl }

/// Closed, render-agnostic description of a button's behaviour.
class NudgeAction {
  final NudgeActionKind kind;
  final String? url;          // openUrl only
  final bool dismissAfter;    // deep link: open, then close the nudge
  const NudgeAction(this.kind, {this.url, this.dismissAfter = false});
}
```

`NudgeButton` would expose a parsed `NudgeAction action` instead of (or in
addition to) the raw `onClick` map. Parsing distils the step list:

```
steps contain Action.openUrl  → kind = openUrl, url = step.url,
                                 dismissAfter = (a dismiss step is also present)
else steps contain a dismiss  → kind = dismiss
else                          → kind = none
```

The dismiss step type differs by surface (`Action.hideBottomSheet` vs
`Action.dismissDialog`); the parser treats **either** as "dismiss", so the
handler never needs to know which surface it is on.

---

## 4. The handler (Strategy)

```dart
/// One behaviour per NudgeActionKind. Pure dispatch — no JSON, no widgets.
abstract interface class NudgeActionHandler {
  Future<void> run(NudgeAction action, NudgeActionScope scope);
}

/// What the handler is allowed to touch — injected, not reached for (DIP).
class NudgeActionScope {
  final VoidCallback dismiss;                 // close THIS nudge
  final Future<bool> Function(Uri) openUrl;   // launch a link
}
```

Dispatch is a single exhaustive switch (`NudgeActionKind` is closed, so the
compiler enforces every case is handled):

```dart
switch (action.kind) {
  case NudgeActionKind.dismiss:  scope.dismiss();
  case NudgeActionKind.openUrl:
    await scope.openUrl(Uri.parse(action.url!));
    if (action.dismissAfter) scope.dismiss();
  case NudgeActionKind.none:     break;
}
```

`NudgeActionScope` is the seam (Dependency Inversion): the handler depends on
two callbacks, not on `Navigator`, `url_launcher`, or the presenter. That keeps
it trivially unit-testable (pass fakes, assert the calls).

---

## 5. Why not reuse the DUI `ActionExecutor`?

The framework's `ActionExecutor` / `ActionFlow` pipeline is built for arbitrary
server-driven flows (API calls, state mutation, navigation, callbacks) and needs
a `DefaultActionExecutor` ancestor, a `RenderPayload`, and an expression scope.
A nudge only ever runs **dismiss** or **openUrl**. Carrying the whole engine for
two leaf actions is unjustified coupling, so the nudge runtime stays standalone:
a tiny parser + a two-case handler. If a nudge ever needs richer flows, the
handler is the one place to delegate into the DUI engine.

---

## 6. Wiring (the deferred change)

Today, in `nudge_view.dart`:

```dart
InkWell(
  onTap: () {}, // TODO(nudge-actions): see this doc
  ...
)
```

When enabled, the tap calls the handler with a scope supplied by the presenter:

```
NudgeView(onAction)            // view emits the button's NudgeAction upward
   └ presentNudge(...)         // presenter builds NudgeActionScope:
        dismiss : () => Navigator.of(ctx).maybePop()
        openUrl : (uri) => launchUrl(uri, mode: externalApplication)
```

The presenter owns `dismiss`/`openUrl` because it owns the modal route and the
build context — the view and handler stay free of both. No other layer changes.

---

## 7. Analytics (later)

`ActionFlow` also carries `analyticsData`; the same handler is the natural choke
point to emit a "nudge action tapped" event before running the effect. Out of
scope for the initial wiring, noted here so it lands in the right layer.
