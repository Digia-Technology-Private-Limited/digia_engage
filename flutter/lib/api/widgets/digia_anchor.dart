import 'package:flutter/material.dart';
import '../../src/vendor/showcaseview/showcaseview.dart';

import '../internal/digia_instance.dart';
import '../internal/guide/anchor_registry.dart';
import '../internal/guide/guide_orchestrator.dart';
import '../internal/guide/guide_showcase_manager.dart';

/// Marks a widget as a guide **anchor** — the target a tooltip or spotlight
/// step points at.
///
/// Wrap any widget a guide campaign may attach to and give it the `anchorKey`
/// configured on the dashboard. When a guide is active and a step targets this
/// key, the SDK shows a bubble (and, for spotlights, a highlight cutout) around
/// this widget's bounds; otherwise the child renders normally.
///
/// ```dart
/// DigiaAnchor(
///   anchorKey: 'signup_button',
///   child: ElevatedButton(onPressed: ..., child: const Text('Sign up')),
/// )
/// ```
///
/// Internally this wraps the child in a showcaseview `Showcase` keyed by the
/// anchor, so the guide engine can sequence across anchors. This is the Flutter
/// equivalent of React Native's `DigiaAnchorView`.
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
  GuideOrchestrator get _orchestrator =>
      DigiaInstance.instance.guideOrchestrator;
  GuideShowcaseManager get _manager => DigiaInstance.instance.guideManager;
  AnchorRegistry get _registry => DigiaInstance.instance.anchorRegistry;

  late GlobalKey _showcaseKey;

  @override
  void initState() {
    super.initState();
    _showcaseKey = _registry.keyFor(widget.anchorKey);
    _orchestrator.addListener(_onGuideChanged);
  }

  @override
  void didUpdateWidget(covariant DigiaAnchor oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.anchorKey != widget.anchorKey) {
      _showcaseKey = _registry.keyFor(widget.anchorKey);
    }
  }

  @override
  void dispose() {
    _orchestrator.removeListener(_onGuideChanged);
    super.dispose();
  }

  void _onGuideChanged() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final p = _manager.presentationFor(widget.anchorKey);
    // No active step for this anchor → render the child as-is.
    if (p == null) return widget.child;

    // Both tooltip and spotlight use our exact custom bubble (`container`); the
    // vendored, patched showcaseview draws the placement-aware arrow at the
    // resolved side, in `tooltipBackgroundColor` (the arrow colour).
    return Showcase.withWidget(
      key: _showcaseKey,
      scope: kDigiaGuideScope,
      container: p.container,
      showArrow: p.showArrow,
      tooltipBackgroundColor: p.arrowColor,
      overlayColor: p.overlayColor,
      overlayOpacity: p.overlayOpacity,
      targetShapeBorder: p.targetShapeBorder,
      targetPadding: p.targetPadding,
      tooltipPosition: p.tooltipPosition,
      // The bubble's own buttons drive the flow; don't hijack target taps.
      disableDefaultTargetGestures: true,
      child: widget.child,
    );
  }
}
