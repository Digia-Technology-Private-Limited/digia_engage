import 'package:digia_inspector_core/digia_inspector_core.dart';
import 'package:flutter/material.dart';

import '../../expr/scope_context.dart';
import '../../pip/pip_manager.dart';
import '../../pip/pip_types.dart';
import '../../resource_provider.dart';
import '../../utils/color_util.dart' show ColorUtil;
import '../base/processor.dart';
import 'action.dart';

class ShowPipProcessor extends ActionProcessor<ShowPipAction> {
  @override
  Future<Object?>? execute(
    BuildContext context,
    ShowPipAction action,
    ScopeContext? scopeContext, {
    required String id,
    String? parentActionId,
    ObservabilityContext? observabilityContext,
  }) async {
    final d = action.data;

    String? s(String key) => d[key] as String?;
    bool b(String key, bool def) => (d[key] as bool?) ?? def;
    double dbl(String key, double def) {
      final v = d[key];
      if (v is double) return v;
      if (v is int) return v.toDouble();
      if (v is num) return v.toDouble();
      return def;
    }
    int intVal(String key, int def) {
      final v = d[key];
      if (v is int) return v;
      if (v is double) return v.toInt();
      if (v is num) return v.toInt();
      return def;
    }

    final componentId = s('componentId') ?? '';
    final videoUrl    = s('videoUrl');
    final position    = PipPosition.fromString(s('position'));

    final args = d['args'] as Map<String, dynamic>?;

    final backgroundColor =
        ColorUtil.tryFromHexString(s('backgroundColor') ?? '#000000') ??
            Colors.black;

    final dragBoundsRaw = d['dragBounds'] as Map<String, dynamic>?;
    final dragBounds = dragBoundsRaw != null
        ? PipDragBounds(
            minXFraction: (dragBoundsRaw['minX'] as num?)?.toDouble() ?? 0,
            maxXFraction: (dragBoundsRaw['maxX'] as num?)?.toDouble() ?? 1,
            minYFraction: (dragBoundsRaw['minY'] as num?)?.toDouble() ?? 0,
            maxYFraction: (dragBoundsRaw['maxY'] as num?)?.toDouble() ?? 1,
          )
        : null;

    final screenFilterRaw = d['screenFilter'] as Map<String, dynamic>?;
    final screenFilter = screenFilterRaw != null
        ? PipScreenFilter(
            isWhitelist: (screenFilterRaw['type'] as String?)?.toLowerCase() == 'whitelist',
            screenNames: ((screenFilterRaw['screens'] as List?)?.cast<String>() ?? []).toSet(),
          )
        : null;

    final request = PipRequest(
      componentId: componentId,
      args: args,
      videoUrl: videoUrl,
      position: position,
      startX: dbl('startX', 0.7),
      startY: dbl('startY', 0.1),
      widthDp: dbl('width', dbl('widthDp', 200)),
      heightDp: dbl('height', dbl('heightDp', 120)),
      cornerRadius: dbl('cornerRadius', 12),
      backgroundColor: backgroundColor,
      showClose: b('showClose', true),
      expandable: b('expandable', true),
      autoPlay: b('autoPlay', true),
      looping: b('looping', false),
      muted: b('muted', false),
      delayMs: intVal('delayMs', 0),
      autoDismissMs: intVal('autoDismissMs', 0),
      screenFilter: screenFilter,
      closeOnScreenChange: b('closeOnScreenChange', false),
      dragBounds: dragBounds,
      animationDurationMs: intVal('animationDurationMs', 300),
    );

    final navigatorKey = ResourceProvider.maybeOf(context)?.navigatorKey;
    final navigator = navigatorKey?.currentState ?? Navigator.of(context);
    PipManager.instance.show(navigator, request);

    return null;
  }
}
