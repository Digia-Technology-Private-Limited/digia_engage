import 'dart:async';

import 'package:flutter/services.dart' show Clipboard, ClipboardData;
import 'package:flutter/widgets.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import 'engage_action.dart';
import 'engage_action_context.dart';

/// The capabilities the handlers need, injected rather than reached for (DIP).
///
/// Keeping `Navigator` / `url_launcher` / `share_plus` behind this seam means the
/// handlers are pure dispatch and trivially testable with fakes; the one
/// concrete binding lives in [EngageActionScope.fromContext].
class EngageActionScope {
  /// Dismiss the current surface (pop its modal route).
  final FutureOr<void> Function() dismiss;

  /// Open a URI — externally (browser) or via the OS/app (deep link).
  final Future<void> Function(Uri uri, {required bool external}) openUri;

  /// Present the native share sheet.
  final Future<void> Function(String text) share;

  /// Copy text to the system clipboard.
  final Future<void> Function(String text) copy;

  const EngageActionScope({
    required this.dismiss,
    required this.openUri,
    required this.share,
    required this.copy,
  });

  /// Default binding from a tap's [context] (inside the surface's modal route).
  factory EngageActionScope.fromContext(BuildContext context) {
    final navigator = Navigator.of(context);
    // Capture the tapped element's rect now (the context is mounted at tap time)
    // — required by iPad's share popover (`sharePositionOrigin`).
    final shareOrigin = _shareOrigin(context);
    return EngageActionScope(
      dismiss: navigator.maybePop,
      openUri: (uri, {required external}) => launchUrl(
        uri,
        mode: external ? LaunchMode.externalApplication : LaunchMode.platformDefault,
      ),
      share: (text) => SharePlus.instance.share(
        ShareParams(text: text, sharePositionOrigin: shareOrigin),
      ),
      copy: (text) => Clipboard.setData(ClipboardData(text: text)),
    );
  }

  /// The global rect of the tapped widget, used to anchor the iPad share popover.
  /// Falls back to a 1×1 rect at the screen centre when the box is unavailable,
  /// so the origin is always non-zero and inside the source view.
  static Rect _shareOrigin(BuildContext context) {
    final box = context.findRenderObject();
    if (box is RenderBox && box.hasSize) {
      return box.localToGlobal(Offset.zero) & box.size;
    }
    final size = MediaQuery.maybeOf(context)?.size ?? const Size(400, 800);
    return Rect.fromCenter(
      center: Offset(size.width / 2, size.height / 2),
      width: 1,
      height: 1,
    );
  }
}

/// Strategy (GoF): executes one kind of [EngageAction]. `T` types each concrete
/// handler; [runDynamic] is the type-erased entry the runner calls.
abstract base class EngageActionHandler<T extends EngageAction> {
  const EngageActionHandler();

  Type get actionType => T;

  Future<void> run(T action, EngageActionScope scope);

  Future<void> runDynamic(EngageAction action, EngageActionScope scope) =>
      run(action as T, scope);
}

/// Runs an ordered list of actions, each dispatched to its handler. Open/closed:
/// support a new action by adding a handler to [_defaults]; nothing else changes.
class EngageActionRunner {
  final Map<Type, EngageActionHandler> _byType;

  /// Host override, consulted before each action's default handler. Returning
  /// `true` marks the action handled and skips the default. Set from
  /// `DigiaConfig.onAction` at init.
  EngageActionInterceptor? interceptor;

  EngageActionRunner([List<EngageActionHandler>? handlers])
      : _byType = {for (final h in handlers ?? _defaults) h.actionType: h};

  static const List<EngageActionHandler> _defaults = [
    OpenUrlHandler(),
    OpenDeeplinkHandler(),
    HideHandler(),
    ShareHandler(),
    CopyToClipboardHandler(),
  ];

  /// The default runner — handlers are stateless, so one instance is shared.
  static final EngageActionRunner shared = EngageActionRunner();

  /// Runs [actions] in author order, awaiting each (so e.g. "open then dismiss"
  /// behaves predictably). For a navigation action ([LinkAction]) the host
  /// [interceptor] runs first; if it returns `true` the default (opening it) is
  /// skipped. Hide/share never reach the interceptor. Unknown actions are
  /// skipped.
  Future<void> run(
    List<EngageAction> actions,
    EngageActionScope scope, [
    EngageActionContext context = EngageActionContext.unknown,
  ]) async {
    for (final action in actions) {
      if (await _handledByHost(action, context)) continue;
      await _byType[action.runtimeType]?.runDynamic(action, scope);
    }
  }

  /// The host override only governs navigation ([LinkAction]); other actions
  /// always run their default.
  Future<bool> _handledByHost(EngageAction action, EngageActionContext context) async {
    final hook = interceptor;
    if (hook == null || action is! LinkAction) return false;
    final result = hook(action, context);
    return (result is Future<bool?> ? await result : result) == true;
  }
}

/// Carries the [EngageActionContext] down to action places (e.g. nudge buttons),
/// so a tap can hand the host both the action and its campaign context.
class EngageActionContextScope extends InheritedWidget {
  final EngageActionContext actionContext;

  const EngageActionContextScope({
    required this.actionContext,
    required super.child,
    super.key,
  });

  /// Read (without subscribing) — callers use this inside tap callbacks.
  static EngageActionContext? of(BuildContext context) =>
      context.getInheritedWidgetOfExactType<EngageActionContextScope>()?.actionContext;

  @override
  bool updateShouldNotify(EngageActionContextScope oldWidget) =>
      !identical(actionContext, oldWidget.actionContext);
}

// ─── concrete handlers ─────────────────────────────────────────────────────────

final class OpenUrlHandler extends EngageActionHandler<OpenUrlAction> {
  const OpenUrlHandler();

  @override
  Future<void> run(OpenUrlAction action, EngageActionScope scope) async {
    final uri = Uri.tryParse(action.url);
    if (uri != null) await scope.openUri(uri, external: true);
  }
}

final class OpenDeeplinkHandler extends EngageActionHandler<OpenDeeplinkAction> {
  const OpenDeeplinkHandler();

  @override
  Future<void> run(OpenDeeplinkAction action, EngageActionScope scope) async {
    final uri = Uri.tryParse(action.uri);
    if (uri != null) await scope.openUri(uri, external: false);
  }
}

final class HideHandler extends EngageActionHandler<HideAction> {
  const HideHandler();

  @override
  Future<void> run(HideAction action, EngageActionScope scope) async {
    await scope.dismiss();
  }
}

final class ShareHandler extends EngageActionHandler<ShareAction> {
  const ShareHandler();

  @override
  Future<void> run(ShareAction action, EngageActionScope scope) async {
    if (action.text.isNotEmpty) await scope.share(action.text);
  }
}

final class CopyToClipboardHandler extends EngageActionHandler<CopyToClipboardAction> {
  const CopyToClipboardHandler();

  @override
  Future<void> run(CopyToClipboardAction action, EngageActionScope scope) async {
    if (action.text.isNotEmpty) await scope.copy(action.text);
  }
}
