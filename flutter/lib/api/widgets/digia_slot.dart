import 'package:flutter/widgets.dart';

/// Renders inline campaign content (banners, cards, widgets) at a specific
/// placement position in scrolling content or screen layouts.
///
/// The [placementKey] is the link between developer code and the Digia
/// dashboard — the marketer selects the same key when creating inline content.
///
/// [DigiaSlot] is self-sizing by default: dimensions come from the campaign
/// artifact configured in the Digia dashboard. Use Flutter's native sizing
/// mechanisms to constrain when needed:
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
class DigiaSlot extends StatelessWidget {
  /// The placement key that identifies this slot in the Digia dashboard.
  ///
  /// Must match the key the marketer selects when creating inline content.
  /// Convention: snake_case — e.g. "home_hero_banner", "pdp_mid_banner".
  final String placementKey;

  const DigiaSlot(this.placementKey, {super.key});

  @override
  Widget build(BuildContext context) {
    // TODO(digia): Route to Digia's server-driven inline rendering engine
    // using placementKey. Returns an empty zero-height box when no active
    // inline content exists for this placement, so layout is unaffected.
    return _DigiaSlotContent(placementKey: placementKey);
  }
}

/// Internal widget that renders the active inline campaign content for a
/// given [placementKey], or collapses to nothing when no content is active.
class _DigiaSlotContent extends StatelessWidget {
  final String placementKey;

  const _DigiaSlotContent({required this.placementKey});

  @override
  Widget build(BuildContext context) {
    // Placeholder until server-driven inline rendering engine is integrated.
    // Collapses to zero size so it has no layout impact when empty.
    return const SizedBox.shrink();
  }
}
