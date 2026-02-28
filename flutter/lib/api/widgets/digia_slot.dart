import 'package:flutter/widgets.dart';

import '../../src/framework/ui_factory.dart';
import '../internal/digia_instance.dart';
import '../internal/digia_overlay_controller.dart';
import '../internal/sdk_state.dart';

/// Renders inline campaign content at a specific placement key.
class DigiaSlot extends StatefulWidget {
  const DigiaSlot(this.placementKey, {super.key});

  final String placementKey;

  @override
  State<DigiaSlot> createState() => _DigiaSlotState();
}

class _DigiaSlotState extends State<DigiaSlot> {
  late final DigiaOverlayController _controller;

  @override
  void initState() {
    super.initState();
    _controller = DigiaInstance.instance.controller;
    _controller.addListener(_onControllerChanged);
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    super.dispose();
  }

  void _onControllerChanged() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    if (DigiaInstance.instance.sdkState != SDKState.ready) {
      return const SizedBox.shrink();
    }

    final payload = _controller.slotPayloads[widget.placementKey];
    if (payload == null) {
      return const SizedBox.shrink();
    }

    final componentId = payload.content['componentId'] as String?;
    if (componentId == null || componentId.isEmpty) {
      return const SizedBox.shrink();
    }

    final args = _toStringDynamicMap(payload.content['args']);
    final factory = DUIFactory();
    if (factory.configProvider.isPage(componentId)) {
      return factory.createPage(componentId, args);
    }
    return factory.createComponent(componentId, args);
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
}
