import 'package:flutter/widgets.dart';

/// Maps a campaign `anchorKey` to the [GlobalKey] of the `Showcase` widget that
/// [DigiaAnchor] builds around its target.
///
/// showcaseview drives a sequence by a list of `GlobalKey`s, so the SDK and the
/// host must share one key per anchor: [DigiaAnchor] reads `keyFor(anchorKey)`
/// for its `Showcase`, and the guide manager passes the same keys (in step
/// order) to `ShowcaseView.startShowCase`.
///
/// Owned by `DigiaInstance` for the app lifetime; the set of anchor keys is
/// bounded (declared by campaigns), so keys are cached, not churned.
class AnchorRegistry {
  final Map<String, GlobalKey> _keys = <String, GlobalKey>{};

  /// The stable [GlobalKey] for [anchorKey], created on first request.
  ///
  /// Note: in showcaseview 5.x this key is a registry identifier and is *not*
  /// attached to a widget element, so `currentContext` cannot be used to detect
  /// mounting — use `ShowcaseView.isTargetRendered(key)` instead (the guide
  /// manager does this to mirror RN's `anchor_not_on_screen` guard).
  GlobalKey keyFor(String anchorKey) =>
      _keys.putIfAbsent(anchorKey, () => GlobalKey(debugLabel: 'DigiaAnchor:$anchorKey'));

  /// Drops the cached key for [anchorKey] (only when no anchor uses it anymore).
  void forget(String anchorKey) => _keys.remove(anchorKey);
}
