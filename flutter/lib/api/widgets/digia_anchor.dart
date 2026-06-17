import 'package:flutter/widgets.dart';

import '../internal/digia_instance.dart';
import '../internal/guide/anchor_registry.dart';

/// Marks a widget as a guide **anchor** — the target a tooltip or spotlight
/// step points at.
///
/// Wrap any widget you want a guide campaign to be able to attach to and give
/// it the `anchorKey` configured on the dashboard. The SDK then positions the
/// bubble (and, for spotlights, the highlight cutout) around this widget's
/// on-screen bounds.
///
/// ```dart
/// DigiaAnchor(
///   anchorKey: 'signup_button',
///   child: ElevatedButton(onPressed: ..., child: const Text('Sign up')),
/// )
/// ```
///
/// The anchor must be mounted (visible in the tree) when its guide is
/// triggered; if it is not, the guide is skipped and a warning is logged —
/// mirroring the React Native renderer's behaviour.
///
/// This is the Flutter equivalent of React Native's `DigiaAnchorView`.
class DigiaAnchor extends StatefulWidget {
  /// Identifier matching the campaign step's `anchorKey`.
  final String anchorKey;

  /// The widget to anchor the guide to.
  final Widget child;

  const DigiaAnchor({
    required this.anchorKey,
    required this.child,
    super.key,
  });

  @override
  State<DigiaAnchor> createState() => _DigiaAnchorState();
}

class _DigiaAnchorState extends State<DigiaAnchor> {
  AnchorRegistry get _registry => DigiaInstance.instance.anchorRegistry;
  late FocusNode _node;

  @override
  void initState() {
    super.initState();
    _node = _registry.focusNodeFor(widget.anchorKey);
    _registry.markMounted(widget.anchorKey);
  }

  @override
  void didUpdateWidget(covariant DigiaAnchor oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.anchorKey != widget.anchorKey) {
      _registry.markUnmounted(oldWidget.anchorKey);
      _node = _registry.focusNodeFor(widget.anchorKey);
      _registry.markMounted(widget.anchorKey);
    }
  }

  @override
  void dispose() {
    _registry.markUnmounted(widget.anchorKey);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // The node only needs an attached element so the guide engine can measure
    // the target rect — it must not participate in focus traversal or steal
    // focus from real inputs, hence canRequestFocus/skipTraversal.
    return Focus(
      focusNode: _node,
      canRequestFocus: false,
      skipTraversal: true,
      child: widget.child,
    );
  }
}
