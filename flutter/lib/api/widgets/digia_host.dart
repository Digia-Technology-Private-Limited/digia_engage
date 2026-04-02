import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../src/framework/ui_factory.dart';
import '../../src/framework/utils/navigation_util.dart';
import '../internal/digia_instance.dart';
import '../internal/digia_overlay_controller.dart';
import '../models/digia_experience_event.dart';
import '../models/in_app_payload.dart';

/// Wraps the application root and renders in-app message overlays
/// (dialogs, bottom sheets, PIPs, fullscreen) above all app content.
///
/// Place this widget once, at the root of your application. The recommended
/// placement is in [MaterialApp.builder]:
///
/// ```dart
/// MaterialApp(
///   navigatorObservers: [DigiaNavigatorObserver()],
///   builder: (context, child) => DigiaHost(child: child!),
/// )
/// ```
///
/// Placing [DigiaHost] multiple times or below the navigation root
/// produces undefined behavior — the SDK logs a warning.
///
/// Marketing name: "In-App Messages" → [DigiaHost]
class DigiaHost extends StatefulWidget {
  /// Navigator key that must be passed to [MaterialApp.navigatorKey].
  ///
  /// [DigiaHost] sits in [MaterialApp.builder], which is above the app's
  /// [Navigator] in the widget tree. Dialogs and bottom sheets require a
  /// context that is a descendant of that navigator; this key provides it.
  ///
  /// ```dart
  /// MaterialApp(
  ///   navigatorKey: DigiaHost.navigatorKey,
  ///   navigatorObservers: [DigiaNavigatorObserver()],
  ///   builder: (context, child) => DigiaHost(child: child!),
  /// )
  /// ```
  final GlobalKey<NavigatorState>? navigatorKey;

  /// The application widget tree to render below the overlay layer.
  final Widget child;

  const DigiaHost({required this.child, this.navigatorKey, super.key});

  @override
  State<DigiaHost> createState() => _DigiaHostState();
}

class _DigiaHostState extends State<DigiaHost> {
  /// Reference to the shared controller. [DigiaInstance] owns it;
  /// [DigiaHost] only listens.
  late final DigiaOverlayController _controller;

  @override
  void initState() {
    super.initState();
    _controller = DigiaInstance.instance.controller;
    _controller.addListener(_onControllerChanged);

    // Notify SDK that DigiaHost is now mounted.
    DigiaInstance.instance.onHostMounted();
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    DigiaInstance.instance.onHostUnmounted();
    super.dispose();
  }

  void _onControllerChanged() {
    final payload = _controller.activePayload;

    if (payload == null) {
      return;
    }

    final command =
        (payload.content['command'] as String?)?.trim().toUpperCase();

    if (command == 'SHOW_BOTTOM_SHEET' || command == 'SHOW_DIALOG') {
      WidgetsBinding.instance.ensureVisualUpdate();
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        _controller.onEvent?.call(const ExperienceImpressed(), payload);
        _presentModal(context, payload, command!);
      });
    } else {
      // Inline is handled by DigiaSlot; host only renders overlay commands.
      _controller.dismiss();
    }
  }

  /// Presents a modal bottom sheet or dialog using the [DUIFactory] rendering
  /// engine. The controller is dismissed when the modal closes so the overlay
  /// state is cleared for both user-driven and programmatic dismissals.
  void _presentModal(
      BuildContext context, InAppPayload payload, String command) {
    final content = payload.content;
    final viewId = content['viewId'] as String?;
    final args = _toStringDynamicMap(content['args']) ?? <String, dynamic>{};

    void fireAndDismiss(_) {
      _controller.onEvent?.call(const ExperienceDismissed(), payload);
      _controller.dismiss();
    }

    if (viewId == null || viewId.isEmpty) {
      _controller.dismiss();
      return;
    }

    // Resolve a context that is a descendant of the app Navigator.
    // DigiaHost lives in MaterialApp.builder (above the Navigator), so
    // this.context cannot be used directly for Navigator-based APIs.
    final navContext = widget.navigatorKey?.currentContext ??
        DigiaInstance.instance.navigator?.context ??
        context;

    Widget safeView(BuildContext ctx) {
      try {
        return _buildView(ctx, viewId, args);
      } catch (e, stack) {
        debugPrint('[Digia] DigiaHost render error for "$viewId": $e\n$stack');
        if (kDebugMode) {
          return _DigiaModalError(viewId: viewId, error: e);
        }
        return const SizedBox.shrink();
      }
    }

    if (command == 'SHOW_BOTTOM_SHEET') {
      presentBottomSheet(
        context: navContext,
        builder: safeView,
        backgroundColor: Theme.of(navContext).bottomSheetTheme.backgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
        useSafeArea: true,
      ).then(fireAndDismiss);
    } else {
      presentDialog(
        context: navContext,
        builder: safeView,
        barrierDismissible: true,
      ).then(fireAndDismiss);
    }
  }

  @override
  Widget build(BuildContext context) {
    // DigiaHost only handles modal experiences (dialog/bottomsheet).
    // Inline/fullscreen experiences are handled by individual pages.
    return widget.child;
  }
}

/// Builds a server-driven view using [DUIFactory].
///
/// Routes to [DUIFactory.createPage] or [DUIFactory.createComponent]
/// based on whether [viewId] is registered as a page in the Digia config.
Widget _buildView(
    BuildContext context, String viewId, Map<String, dynamic> args) {
  final factory = DUIFactory();
  if (factory.configProvider.isPage(viewId)) {
    return factory.createPage(viewId, args);
  }
  return factory.createComponent(viewId, args);
}

class _DigiaModalError extends StatelessWidget {
  final String viewId;
  final Object error;

  const _DigiaModalError({required this.viewId, required this.error});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        border: Border.all(color: const Color(0xFFD32F2F)),
        borderRadius: BorderRadius.circular(8),
        color: const Color(0x1FD32F2F),
      ),
      padding: const EdgeInsets.all(12),
      child: Text(
        '[DigiaHost($viewId)] Render error:\n$error',
        style: const TextStyle(
          color: Color(0xFFD32F2F),
          fontSize: 12,
        ),
      ),
    );
  }
}

Map<String, dynamic>? _toStringDynamicMap(Object? raw) {
  final map = raw as Map<Object?, Object?>?;
  if (map == null) return null;
  final output = <String, dynamic>{};
  for (final entry in map.entries) {
    if (entry.key is String) {
      output[entry.key! as String] = entry.value;
    }
  }
  return output;
}
