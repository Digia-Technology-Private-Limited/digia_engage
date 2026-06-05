import 'package:flutter/widgets.dart';

import 'nudge_config.dart';
import 'nudge_presentation.dart';
import 'nudge_view.dart';

/// Selects the [NudgePresentation] strategy for a display type. Adding a display
/// type is an open/closed change: register a strategy here.
const Map<NudgeDisplayType, NudgePresentation> _presentations = {
  NudgeDisplayType.bottomSheet: BottomSheetPresentation(),
  NudgeDisplayType.dialog: DialogPresentation(),
};

/// Presents a [NudgeConfig] over the app, choosing the bottom-sheet or dialog
/// strategy by its surface. Returns a future that completes when the nudge is
/// dismissed (button action, close affordance, scrim, or drag-down).
Future<void> presentNudge({
  required BuildContext context,
  required NudgeConfig config,
}) {
  final presentation =
      _presentations[config.surface.displayType] ?? const BottomSheetPresentation();

  return presentation.present(
    context: context,
    surface: config.surface,
    content: NudgeView(content: config.content),
  );
}
