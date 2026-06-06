import 'package:flutter/widgets.dart';

import '../action/engage_action_context.dart';
import '../action/engage_action_handler.dart';
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
  EngageActionContext actionContext = EngageActionContext.unknown,
}) {
  final presentation =
      _presentations[config.surface.displayType] ?? const BottomSheetPresentation();

  // Wrap the content so button taps can hand the host the campaign context.
  return presentation.present(
    context: context,
    surface: config.surface,
    content: EngageActionContextScope(
      actionContext: actionContext,
      child: NudgeView(content: config.content),
    ),
  );
}
