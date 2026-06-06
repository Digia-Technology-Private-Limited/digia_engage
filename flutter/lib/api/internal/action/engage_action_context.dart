import 'dart:async';

import 'engage_action.dart';

/// Which engage surface an action originated from.
enum EngageSurface { nudge, guide, inline, survey }

/// Context passed to the host's [EngageActionInterceptor] alongside the action —
/// enough to route, log, or conditionally handle it.
class EngageActionContext {
  final String campaignId;
  final String campaignKey;
  final EngageSurface surface;

  const EngageActionContext({
    required this.campaignId,
    required this.campaignKey,
    required this.surface,
  });

  /// Fallback when no context is in scope (an action place outside a campaign).
  static const unknown = EngageActionContext(
    campaignId: '',
    campaignKey: '',
    surface: EngageSurface.nudge,
  );
}

/// Host override for engage **navigation** actions ([LinkAction] — open URL /
/// deep link, with their target metadata). Hide/share are always handled by the
/// SDK and never reach this hook.
///
/// Return `true` to signal the app fully handled the link — the SDK then
/// **skips its default behaviour** (opening it). Returning `false` / `null` (or
/// nothing) lets the default run. Mirrors the React Native `onAction` hook.
typedef EngageActionInterceptor = FutureOr<bool?> Function(
  LinkAction action,
  EngageActionContext context,
);
