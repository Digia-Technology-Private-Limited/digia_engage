import 'package:flutter/widgets.dart';

import '../internal/campaign/inline_carousel_config.dart';
import '../internal/digia_instance.dart';
import 'digia_inline_carousel.dart';

/// Renders inline campaign content (banners, cards, widgets) at a specific
/// placement position in scrolling content or screen layouts.
///
/// The [placementKey] is the link between developer code and the Digia
/// dashboard — the marketer selects the same key when creating inline content.
///
/// **Lifecycle:**
/// - Shows content as soon as a matching campaign is stored for [placementKey].
/// - Fires an impression event the first time each unique payload renders.
/// - Collapses to [SizedBox.shrink] when no campaign is active.
/// - The campaign **persists** in the controller when the page is disposed so
///   it reappears on the next visit. Only the server (via
///   [onExperienceInvalidated]) or an explicit user dismiss clears it.
///
/// ```dart
/// // Self-sizing (recommended)
/// DigiaSlot('hero_banner')
///
/// // With explicit height
/// SizedBox(
///   height: 200,
///   child: DigiaSlot('hero_banner'),
/// )
/// ```
///
/// Marketing name: "Inline Content" → [DigiaSlot]
class DigiaSlot extends StatefulWidget {
  /// The placement key that identifies this slot in the Digia dashboard.
  ///
  /// Must match the key the marketer selects when creating inline content.
  /// Convention: snake_case — e.g. "home_hero_banner", "pdp_mid_banner".
  final String placementKey;

  /// Optional widget to display when no campaign content is active for this
  /// slot. Defaults to [SizedBox.shrink] when null.
  final Widget? placeholder;

  const DigiaSlot(this.placementKey, {super.key, this.placeholder});

  @override
  State<DigiaSlot> createState() => _DigiaSlotState();
}

class _DigiaSlotState extends State<DigiaSlot> {
  /// The inline carousel config currently active for this slot, populated when
  /// a campaign-store-routed inline campaign is triggered.
  InlineCarouselConfig? _currentConfig;

  @override
  void initState() {
    super.initState();
    DigiaInstance.instance.controller.addListener(_onControllerChanged);
    _syncConfig();
  }

  @override
  void dispose() {
    DigiaInstance.instance.controller.removeListener(_onControllerChanged);
    // Do NOT remove the campaign on dispose. Inline campaigns are "sticky" —
    // they persist for when the user returns to this page. Only server
    // invalidation or an explicit user dismiss should clear them.
    super.dispose();
  }

  void _onControllerChanged() {
    if (_syncConfig()) setState(() {});
  }

  /// Pulls the latest carousel config for this slot from the controller.
  /// Returns `true` when it changed (a rebuild is needed).
  bool _syncConfig() {
    final config =
        DigiaInstance.instance.controller.getSlotConfig(widget.placementKey);
    if (identical(config, _currentConfig)) return false;
    _currentConfig = config;
    return true;
  }

  @override
  Widget build(BuildContext context) {
    final config = _currentConfig;
    if (config != null) return DigiaInlineCarousel(config: config);
    return widget.placeholder ?? const SizedBox.shrink();
  }
}
