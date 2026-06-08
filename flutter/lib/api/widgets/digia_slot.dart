import 'package:flutter/widgets.dart';

import '../internal/action/engage_action.dart';
import '../internal/action/engage_action_context.dart';
import '../internal/action/engage_action_handler.dart';
import '../internal/campaign/campaign_model.dart';
import '../internal/campaign/inline_carousel_config.dart';
import '../internal/digia_instance.dart';
import 'digia_inline_carousel.dart';
import 'digia_inline_story.dart';

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
  /// The inline campaign config currently active for this slot (carousel or
  /// story), populated when a campaign-store-routed inline campaign is
  /// triggered. A slot key only ever holds one inline kind.
  CampaignConfigModel? _currentConfig;

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

  /// Pulls the latest inline config for this slot from the controller.
  /// Returns `true` when it changed (a rebuild is needed).
  bool _syncConfig() {
    final config =
        DigiaInstance.instance.controller.getInlineConfig(widget.placementKey);
    if (identical(config, _currentConfig)) return false;
    _currentConfig = config;
    return true;
  }

  /// Opens a carousel slide's `deepLink` through the engage action runner, so
  /// the host's `onAction` override is consulted before the SDK's default open.
  void _onCarouselItemTap(CarouselItem item) {
    final deepLink = item.deepLink;
    if (deepLink == null || deepLink.isEmpty) return;
    EngageActionRunner.shared.run(
      [OpenDeeplinkAction(deepLink)],
      EngageActionScope.fromContext(context),
      const EngageActionContext(
        campaignId: '',
        campaignKey: '',
        surface: EngageSurface.inline,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    switch (_currentConfig) {
      case InlineCarouselCampaignConfig(:final inlineConfig):
        return DigiaInlineCarousel(
          config: inlineConfig,
          onItemTap: _onCarouselItemTap,
        );
      case InlineStoryCampaignConfig(:final storyConfig):
        final payload =
            DigiaInstance.instance.controller.getSlot(widget.placementKey);
        return DigiaInlineStory(
          config: storyConfig,
          variables: payload?.variables,
        );
      case _:
        return widget.placeholder ?? const SizedBox.shrink();
    }
  }
}
