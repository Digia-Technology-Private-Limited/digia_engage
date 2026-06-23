import '../campaign/json_util.dart';
import 'engage_action.dart';

/// Decodes an `onClick` flow (`{ steps: [...] }`) into an ordered list of
/// [EngageAction]s. The only place that knows the action wire format.
///
/// Step types mirror the DUI action vocabulary so the same payload could later
/// be consumed by the native action engine:
///   • `Action.openUrl`        → url (external) or deeplink, by `launchMode`
///   • `Action.share`          → share
///   • `Action.copyToClipBoard` → copy to clipboard
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
        final text = _text(data);
        return text.isEmpty ? null : ShareAction(text);
      case 'Action.copyToClipBoard':
        final text = _text(data);
        return text.isEmpty ? null : CopyToClipboardAction(text);
      case 'Action.hideBottomSheet':
      case 'Action.dismissDialog':
        return const HideAction();
      default:
        return null;
    }
  }

  /// The action's text payload — canonical `message`, with `text`/`value` fallbacks.
  String _text(Map<String, dynamic> data) {
    for (final key in const ['message', 'text', 'value']) {
      final value = optString(data, key);
      if (value.isNotEmpty) return value;
    }
    return '';
  }
}
