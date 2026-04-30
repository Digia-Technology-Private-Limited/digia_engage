import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../src/framework/pip/pip_overlay.dart';
import '../../src/framework/pip/pip_types.dart';
import '../../src/framework/ui_factory.dart';
import '../../src/framework/utils/color_util.dart';
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
class DigiaHost extends StatefulWidget {
  final GlobalKey<NavigatorState>? navigatorKey;
  final Widget child;

  const DigiaHost({required this.child, this.navigatorKey, super.key});

  @override
  State<DigiaHost> createState() => _DigiaHostState();
}

class _DigiaHostState extends State<DigiaHost> {
  late final DigiaOverlayController _controller;

  @override
  void initState() {
    super.initState();
    _controller = DigiaInstance.instance.controller;
    _controller.addListener(_onControllerChanged);
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
    if (payload == null) return;

    final command =
        (payload.content['command'] as String?)?.trim().toUpperCase();

    if (command == 'SHOW_BOTTOM_SHEET' || command == 'SHOW_DIALOG') {
      WidgetsBinding.instance.ensureVisualUpdate();
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        _controller.onEvent?.call(const ExperienceImpressed(), payload);
        _presentModal(context, payload, command!);
      });
    } else if (command == 'SHOW_PIP') {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        _presentPip(context, payload);
      });
      _controller.dismiss();
    } else {
      // Inline is handled by DigiaSlot; host only renders overlay commands.
      _controller.dismiss();
    }
  }

  void _presentPip(BuildContext context, InAppPayload payload) {
    final content = payload.content;
    final viewId = content['viewId'] as String?;
    final args = _toStringDynamicMap(content['args']) ?? <String, dynamic>{};
    final allArgs = <String, dynamic>{...args};
    // Copy any top-level content fields into args for pip field parsing
    content.forEach((k, v) { if (!allArgs.containsKey(k)) allArgs[k] = v; });

    void fireAndDismiss(dynamic _) {
      _controller.onEvent?.call(const ExperienceDismissed(), payload);
    }

    final navigator = widget.navigatorKey?.currentState ??
        DigiaInstance.instance.navigator;
    if (navigator == null) {
      debugPrint('[Digia] DigiaHost: no navigator available for SHOW_PIP');
      return;
    }

    final request = PipRequest(
      componentId: viewId ?? '',
      args: args,
      videoUrl: allArgs['videoUrl'] as String?,
      position: PipPosition.fromString(allArgs['position'] as String?),
      startX: _toDouble(allArgs['startX']) ?? 0.7,
      startY: _toDouble(allArgs['startY']) ?? 0.1,
      widthDp: _toDouble(allArgs['width'] ?? allArgs['widthDp']) ?? 200,
      heightDp: _toDouble(allArgs['height'] ?? allArgs['heightDp']) ?? 120,
      cornerRadius: _toDouble(allArgs['cornerRadius']) ?? 12,
      backgroundColor: ColorUtil.tryFromHexString(
            allArgs['backgroundColor'] as String? ?? '#000000') ??
          Colors.black,
      showClose: allArgs['showClose'] as bool? ?? true,
      expandable: allArgs['expandable'] as bool? ?? true,
      autoPlay: allArgs['autoPlay'] as bool? ?? true,
      looping: allArgs['looping'] as bool? ?? false,
      muted: allArgs['muted'] as bool? ?? false,
      delayMs: _toInt(allArgs['delayMs']) ?? 0,
      autoDismissMs: _toInt(allArgs['autoDismissMs']) ?? 0,
      closeOnScreenChange: allArgs['closeOnScreenChange'] as bool? ?? false,
      dragBounds: _parseDragBounds(allArgs['dragBounds']),
      animationDurationMs: _toInt(allArgs['animationDurationMs']) ?? 300,
      onDismiss: fireAndDismiss,
    );

    PipManager.instance.show(navigator, request);
    _controller.onEvent?.call(const ExperienceImpressed(), payload);
  }

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
    return widget.child;
  }
}

Widget _buildView(
    BuildContext context, String viewId, Map<String, dynamic> args) {
  final factory = DUIFactory();
  if (factory.configProvider.isPage(viewId)) {
    return factory.createPage(viewId, args);
  }
  return factory.createComponent(viewId, args);
}

double? _toDouble(dynamic v) {
  if (v is double) return v;
  if (v is int) return v.toDouble();
  if (v is num) return v.toDouble();
  return null;
}

int? _toInt(dynamic v) {
  if (v is int) return v;
  if (v is double) return v.toInt();
  if (v is num) return v.toInt();
  return null;
}

PipDragBounds? _parseDragBounds(dynamic raw) {
  final map = raw as Map<String, dynamic>?;
  if (map == null) return null;
  return PipDragBounds(
    minXFraction: _toDouble(map['minX']) ?? 0,
    maxXFraction: _toDouble(map['maxX']) ?? 1,
    minYFraction: _toDouble(map['minY']) ?? 0,
    maxYFraction: _toDouble(map['maxY']) ?? 1,
  );
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
