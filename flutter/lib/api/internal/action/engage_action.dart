/// A single engage action. An "action place" (e.g. a nudge button's tap) carries
/// an ordered `List<EngageAction>` — every place can run several in sequence.
///
/// Centralised (not nudge-specific): any engage surface can author and run these.
/// Pure model — parsing lives in `engage_action_parser.dart`, execution in
/// `engage_action_handler.dart`. The sealed hierarchy lets the runner dispatch
/// exhaustively.
sealed class EngageAction {
  const EngageAction();
}

/// A navigation action that opens a [target] (a URL or deep link). These are the
/// only actions the host `onAction` override is consulted for — hide/share are
/// always handled by the SDK.
sealed class LinkAction extends EngageAction {
  const LinkAction();

  /// The URL or deep-link target.
  String get target;
}

/// Open a web URL in an external browser.
class OpenUrlAction extends LinkAction {
  final String url;
  const OpenUrlAction(this.url);

  @override
  String get target => url;
}

/// Open a deep link, routed by the OS / host app (in-app navigation).
class OpenDeeplinkAction extends LinkAction {
  final String uri;
  const OpenDeeplinkAction(this.uri);

  @override
  String get target => uri;
}

/// Hide / dismiss the current engage surface (close the sheet / dialog / overlay).
class HideAction extends EngageAction {
  const HideAction();
}

/// Open the native share sheet with [text].
class ShareAction extends EngageAction {
  final String text;
  const ShareAction(this.text);
}

/// Copy [text] to the system clipboard.
class CopyToClipboardAction extends EngageAction {
  final String text;
  const CopyToClipboardAction(this.text);
}
