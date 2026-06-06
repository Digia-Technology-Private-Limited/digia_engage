import '../campaign/json_util.dart';
import 'engage_action.dart';

/// Decodes an `onClick` flow (`{ steps: [...] }`) into an ordered list of
/// [EngageAction]s. The only place that knows the action wire format.
///
/// Step types mirror the DUI action vocabulary so the same payload could later
/// be consumed by the native action engine:
///   • `Action.openUrl`        → url (external) or deeplink, by `launchMode`
///   • `Action.share`          → share
///   • `Action.hideBottomSheet` / `Action.dismissDialog` → dismiss
class EngageActionParser {
  const EngageActionParser();

  List<EngageAction> parse(Map<String, dynamic>? onClick) {
    final steps = optList(onClick ?? const {}, 'steps') ?? const [];
    return steps
        .whereType<Map>()
        .map((s) => _step(s.cast<String, dynamic>()))
        .whereType<EngageAction>()
        .toList(growable: false);
  }

  EngageAction? _step(Map<String, dynamic> step) {
    final data = optMap(step, 'data') ?? const <String, dynamic>{};
    switch (optString(step, 'type')) {
      case 'Action.openUrl':
        final url = optString(data, 'url');
        if (url.isEmpty) return null;
        return optString(data, 'launchMode') == 'externalApplication'
            ? OpenUrlAction(url)
            : OpenDeeplinkAction(url);
      case 'Action.share':
        return ShareAction(optString(data, 'message'));
      case 'Action.hideBottomSheet':
      case 'Action.dismissDialog':
        return const HideAction();
      default:
        return null;
    }
  }
}
