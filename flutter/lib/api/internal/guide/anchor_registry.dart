import 'package:flutter/widgets.dart';

/// Maps a campaign `anchorKey` to the [FocusNode] of the host widget it targets.
///
/// This is the Flutter analogue of Android's `AnchorRegistry`. The guide engine
/// (onboarding_overlay) locates a target by its [FocusNode] — it reads the
/// node's attached element to measure the on-screen rect — so the SDK and the
/// host app must share the *same* node instance for a given key. [DigiaAnchor]
/// attaches `focusNodeFor(key)` to the wrapped widget; `OnboardingStepFactory`
/// hands the same node to the matching step.
///
/// Owned by `DigiaInstance` and lives for the app lifetime: the set of anchor
/// keys is bounded (declared by campaigns), so nodes are cached, not churned.
class AnchorRegistry {
  final Map<String, FocusNode> _nodes = <String, FocusNode>{};

  /// Keys whose [DigiaAnchor] is currently mounted in the tree. Used to decide
  /// whether a guide can be shown — a step whose anchor is absent cannot be
  /// positioned (mirrors React Native's `anchor_not_on_screen` guard).
  final Set<String> _mounted = <String>{};

  /// The stable [FocusNode] for [key], created on first request.
  FocusNode focusNodeFor(String key) =>
      _nodes.putIfAbsent(key, () => FocusNode(debugLabel: 'DigiaAnchor:$key'));

  /// Marks [key]'s anchor as present in the widget tree.
  void markMounted(String key) => _mounted.add(key);

  /// Marks [key]'s anchor as removed from the widget tree.
  void markUnmounted(String key) => _mounted.remove(key);

  /// Whether [key] has a mounted anchor that can currently be measured.
  bool isMounted(String key) => _mounted.contains(key);

  /// Disposes every cached node. Called on SDK teardown only.
  void disposeAll() {
    for (final node in _nodes.values) {
      node.dispose();
    }
    _nodes.clear();
    _mounted.clear();
  }
}
