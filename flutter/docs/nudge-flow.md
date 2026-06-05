# Nudge Rendering — Architecture & Flow (Flutter SDK)

How a **nudge** campaign (a bottom-sheet / dialog overlay) travels from the
backend to pixels on screen, and why the pieces are split the way they are.

> Scope: rendering + presentation. Button **actions** are a separate concern —
> see [`nudge-action-handler-flow.md`](./nudge-action-handler-flow.md).

---

## 1. End-to-end flow

```
CEP trigger ──► DigiaInstance.onCampaignTriggered(payload)
                   │
                   ▼  resolve campaignKey, look up CampaignStore
              CampaignModel.config == NudgeCampaignConfig(NudgeConfig)
                   │
                   ▼  _routeStoredCampaign → _presentNudge
              presentNudge(context, config)            // nudge_presenter.dart
                   │   shows showModalBottomSheet / showDialog
                   ▼
              NudgeView(content)                        // nudge_view.dart
                   │   plain Flutter widgets, styled like the dashboard preview
                   ▼
              Text / Image / Button / Gap / Divider / Lottie
```

Fetch + parse happen once, at init:

```
DigiaInstance.initialize
   └ CampaignFetcher.fetch ─► CampaignModel.fromJson
         case 'nudge': NudgeParser().parse(templateConfig) ─► NudgeConfig
```

---

## 2. The wire payload

A nudge campaign's `templateConfig` is exactly what the dashboard's
`toWidgetConfig` emits:

```jsonc
{
  "templateType": "nudge",
  "container": { "displayType": "bottom_sheet", "backgroundColor": "#FFFFFF",
                 "cornerRadius": 18, "padding": 20, "showHandle": true, ... },
  "layout":    { "id": "...", "type": "digia/column",
                 "props": { "crossAxisAlignment": "start", "spacing": 12, ... },
                 "children": [ /* digia/text, digia/button, ... */ ] }
}
```

- **`container`** → the surface chrome (`NudgeSurface`).
- **`layout`** → a native `digia/column` tree (`NudgeColumn` + nodes).

---

## 3. Layers & responsibilities (SRP)

| File | Responsibility | Knows about |
|---|---|---|
| `nudge_content.dart` | **Model** — pure value objects (`NudgeColumn`, sealed `NudgeNode`, `NudgeBox`) | nothing (no JSON, no widgets) |
| `nudge_config.dart`  | **Model** — `NudgeSurface`, `NudgeConfig` | the model |
| `nudge_parser.dart`  | **Parser** — wire JSON → model | JSON shape only |
| `nudge_node_renderer.dart` | **Render strategies** — one renderer per node type + registry | Flutter + the model |
| `nudge_box_decorator.dart` | **Decorator** — wraps a node with its box (size/bg/border/margin/align) | Flutter + `NudgeBox` |
| `nudge_view.dart`    | **View** — column layout only; delegates content + box | renderer + decorator |
| `nudge_presentation.dart` | **Presentation strategies** — bottom-sheet / dialog + frames | Flutter + surface |
| `nudge_presenter.dart` | **Selector** — pick a presentation strategy, present | strategies + view |
| `campaign_model.dart` | **Routing glue** — `nudge` type → `NudgeParser` | parser + model |

Each box has a single reason to change: the wire format moves `nudge_parser`; a
new widget's *look* is a new renderer strategy; the box rule is in the decorator;
a new modal behaviour is a new presentation strategy. The model is the stable
contract in the middle that none of them can corrupt.

---

## 4. Design patterns in play

- **Factory Method + Strategy registry** (`NudgeParser._nodeParsers`):
  `Map<String, NodeParser>` keyed by native widget type — decode a new widget by
  registering one entry (OCP).
- **Strategy + Registry** for rendering (`NudgeNodeRenderer` +
  `NudgeNodeRendererRegistry`): one renderer class per node type, dispatched by
  type. Adding a widget = a new strategy class + one registration; no existing
  renderer or the view changes (OCP, SRP).
- **Decorator** (`NudgeBoxDecorator`): the shared box envelope (size / bg /
  border / margin / self-align) wraps any rendered node in one place, so no
  renderer repeats it (SRP, DRY).
- **Strategy + Factory** for presentation (`NudgePresentation`,
  `BottomSheetPresentation` / `DialogPresentation`, selected from a map): each
  display type owns its Flutter entry point and dismissal semantics; a new type
  (pip, fullscreen) is a new strategy in the map (OCP).
- **Dependency Inversion**: `NudgeView` is constructor-injected with its
  renderer registry and decorator (swappable, testable); the planned action
  handler depends on an injected `NudgeActionScope`, not `Navigator`/`url_launcher`.
- **Composition over inheritance**: frames are assembled from small private
  widgets (`_SheetFrame`, `_DialogFrame`, `_NudgeBody`, `_DragHandle`,
  `_CloseButton`) rather than a config-driven god-widget.

---

## 5. Two deliberate "we do NOT use the DUI engine" decisions

1. **Rendering** does not go through `DUIFactory.createComponent` / the widget
   registry / `RenderPayload` / `ResourceProvider`. A nudge is a closed set of
   six widgets, so each maps straight to a Flutter widget. This keeps the nudge
   runtime small and free of the SDUI engine's setup requirements.
2. **Child decoding** is tolerant: the dashboard serializes a bare
   `children: [...]` list, while the DUI `VWData` parser expects a child-group
   map. `NudgeParser._childNodes` accepts **either** form, so nudges render
   regardless of which the producer emits.

Trade-off: nudges can only contain the six supported widgets. That is exactly
the dashboard's authoring surface, so the constraint is real, not incidental —
if the dashboard adds a widget, add a parser entry (§4) and a render case.

---

## 6. Surface → Flutter mapping (`NudgeSurface`)

| Field | Bottom sheet | Dialog |
|---|---|---|
| `backgroundColor` / `cornerRadius` | sheet container | `Dialog` shape |
| `padding` | inner padding around content | inner padding around content |
| `backdropDismissible` | `isDismissible` | `barrierDismissible` |
| `showHandle` | drag-handle pill | — |
| `draggable` | `enableDrag` | — |
| `showCloseButton` | overlaid "×" | overlaid "×" |
| `widthFraction` | — | width = screen × fraction |

---

## 7. Prerequisites in the host app

Nudges are presented through the **root navigator**, resolved via
`DigiaInstance.navigator`. That is wired by `DigiaNavigatorObserver`, so the host
must register it:

```dart
MaterialApp(
  navigatorObservers: [DigiaNavigatorObserver()],
  builder: (context, child) => DigiaHost(child: child!),
)
```

Without it, a nudge logs a warning and is dropped (no context to present into).
