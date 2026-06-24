import 'package:flutter/widgets.dart';

import '../internal/action/engage_action.dart';
import '../internal/action/engage_action_context.dart';
import '../internal/action/engage_action_handler.dart';
import '../internal/campaign/campaign_model.dart';
import '../internal/campaign/inline_carousel_config.dart';
import '../internal/digia_instance.dart';
import '../internal/variable_scope.dart';
import '../models/cep_trigger_payload.dart';
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

  /// The payload we've already scheduled a Digia impression for, so rebuilds
  /// don't schedule duplicates. The controller holds the authoritative
  /// per-campaign dedup that survives this widget's disposal.
  CEPTriggerPayload? _impressionScheduledFor;

  /// The currently-visible carousel item index (0-based), tracked from page
  /// changes so a slide tap reports the right `item_index`.
  int _carouselIndex = 0;

  /// The active trigger payload for this slot, or null if none.
  CEPTriggerPayload? get _slotPayload =>
      DigiaInstance.instance.controller.getSlot(widget.placementKey);

  void _onControllerChanged() {
    if (_syncConfig()) setState(() {});
  }

  /// Fires Digia's first-render impression once the slot has painted. CEP is
  /// impressed instantly at route time; Digia waits for an actual render.
  void _scheduleDigiaImpressionIfNeeded() {
    final payload =
        DigiaInstance.instance.controller.getSlot(widget.placementKey);
    if (payload == null || identical(payload, _impressionScheduledFor)) return;
    _impressionScheduledFor = payload;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      DigiaInstance.instance.reportSlotFirstRender(payload);
    });
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
  /// Campaign context for this slot's actions, resolved from the active trigger
  /// payload (the same id analytics reports). Shared by the carousel deeplink
  /// and the story CTA so both attribute to the right campaign.
  EngageActionContext _actionContext() {
    final payload =
        DigiaInstance.instance.controller.getSlot(widget.placementKey);
    final campaign = payload == null
        ? null
        : DigiaInstance.instance.campaignForKey(payload.campaignKey);
    return EngageActionContext(
      campaignId: campaign?.id ?? '',
      campaignKey: payload?.campaignKey ?? '',
      surface: EngageSurface.inline,
    );
  }

  /// Reports a carousel page settling into view (step viewed). [auto] is true
  /// for autoplay advances, false for manual swipes.
  void _onCarouselPageChanged(int index, bool auto, int itemTotal) {
    _carouselIndex = index;
    final payload = _slotPayload;
    if (payload == null) return;
    DigiaInstance.instance.reportCarouselStepViewed(
      payload,
      itemIndex: index + 1,
      itemTotal: itemTotal,
      auto: auto,
    );
  }

  void _onCarouselItemTap(CarouselItem item) async {
    // No deeplink → no action to run → nothing to report.
    final deepLink = item.deepLink;
    if (deepLink == null || deepLink.isEmpty) return;
    // Snapshot the payload + scope before running; report only on success.
    final payload = _slotPayload;
    final scope = EngageActionScope.fromContext(context);
    final actionContext = _actionContext();
    try {
      // The deeplink arrives already resolved against the slot's [VariableScope]
      // (see DigiaInlineCarousel), so it only needs to be opened here.
      await EngageActionRunner.shared.run(
        [OpenDeeplinkAction(deepLink)],
        scope,
        actionContext,
      );
    } catch (_) {
      return; // action failed → no click reported
    }
    if (payload != null) {
      DigiaInstance.instance.reportCarouselStepClicked(
        payload,
        itemIndex: _carouselIndex + 1,
        actionUrl: deepLink,
      );
    }
  }

  /// Wraps inline content in the slot's [VariableScope], built from the trigger
  /// payload's variables, so descendants resolve `{{ placeholder }}` copy
  /// through [VariableScopeProvider] (same mechanism as nudges).
  Widget _scoped(Widget child) {
    return VariableScopeProvider(
      scope: VariableScope.fromSchemas(
        _currentConfig?.defaultVariables ?? const [],
        _slotPayload?.variables,
      ),
      child: child,
    );
  }

  @override
  Widget build(BuildContext context) {
    switch (_currentConfig) {
      case InlineCarouselCampaignConfig(:final inlineConfig):
        _scheduleDigiaImpressionIfNeeded();
        return _scoped(
          DigiaInlineCarousel(
            config: inlineConfig,
            onItemTap: _onCarouselItemTap,
            onPageChanged: (index, auto) =>
                _onCarouselPageChanged(index, auto, inlineConfig.items.length),
          ),
        );
      case InlineStoryCampaignConfig(:final storyConfig):
        _scheduleDigiaImpressionIfNeeded();
        return _scoped(DigiaInlineStory(
          config: storyConfig,
          actionContext: _actionContext(),
        ));
      case _:
        return widget.placeholder ?? const SizedBox.shrink();
    }
  }
}
