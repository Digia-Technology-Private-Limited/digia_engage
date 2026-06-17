import 'package:flutter/widgets.dart';

import '../internal/action/engage_action.dart';
import '../internal/action/engage_action_context.dart';
import '../internal/action/engage_action_handler.dart';
import '../internal/campaign/campaign_model.dart';
import '../internal/campaign/inline_carousel_config.dart';
import '../internal/digia_instance.dart';
import '../internal/event/engage_matrix.dart';
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

  void _onControllerChanged() {
    if (_syncConfig()) setState(() {});
  }

  /// Fires Digia's first-render impression once the slot has painted. CEP is
  /// impressed instantly at route time; Digia waits for an actual render.
  ///
  /// For inline campaigns the impression is the rich `Digia Experience Viewed`
  /// matrix event (display_style + item_total + screen/trigger context); the
  /// carousel additionally emits a `Digia Step Viewed` for the initially visible
  /// slide (index 0), since `onPageChanged` only fires on subsequent changes.
  void _scheduleDigiaImpressionIfNeeded() {
    final payload =
        DigiaInstance.instance.controller.getSlot(widget.placementKey);
    if (payload == null || identical(payload, _impressionScheduledFor)) return;
    _impressionScheduledFor = payload;
    final config = _currentConfig;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final instance = DigiaInstance.instance;
      final events = instance.events;
      switch (config) {
        case InlineCarouselCampaignConfig(:final inlineConfig):
          final total = inlineConfig.items.length;
          events.digiaImpressionOnce(
            payload,
            eventName: 'Digia Experience Viewed',
            properties: inlineViewedProperties(
              displayStyle: 'carousel',
              itemTotal: total,
              screenName: instance.currentScreen,
              triggerType: payload.triggerType,
              triggerEvent: payload.triggerEvent,
            ),
          );
          events.analytics(
            'Digia Step Viewed',
            payload,
            properties: inlineStepProperties(
              displayStyle: 'carousel',
              itemIndex: 0,
              itemTotal: total,
            ),
          );
        case InlineStoryCampaignConfig(:final storyConfig):
          events.digiaImpressionOnce(
            payload,
            eventName: 'Digia Experience Viewed',
            properties: inlineViewedProperties(
              displayStyle: 'story',
              itemTotal: storyConfig.items.length,
              screenName: instance.currentScreen,
              triggerType: payload.triggerType,
              triggerEvent: payload.triggerEvent,
            ),
          );
        case _:
          events.digiaImpressionOnce(payload);
      }
    });
  }

  /// Emits `Digia Step Viewed` when the carousel's visible slide changes.
  void _onCarouselPageChanged(int index) {
    final payload =
        DigiaInstance.instance.controller.getSlot(widget.placementKey);
    final config = _currentConfig;
    if (payload == null || config is! InlineCarouselCampaignConfig) return;
    DigiaInstance.instance.events.analytics(
      'Digia Step Viewed',
      payload,
      properties: inlineStepProperties(
        displayStyle: 'carousel',
        itemIndex: index,
        itemTotal: config.inlineConfig.items.length,
      ),
    );
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
  /// Also emits the `Digia Step Clicked` matrix event for the tapped slide.
  void _onCarouselItemTap(CarouselItem item, int index) {
    // The deeplink arrives already resolved against the slot's [VariableScope]
    // (see DigiaInlineCarousel), so it only needs to be opened here.
    final deepLink = item.deepLink;

    final payload =
        DigiaInstance.instance.controller.getSlot(widget.placementKey);
    final config = _currentConfig;
    if (payload != null && config is InlineCarouselCampaignConfig) {
      final hasLink = deepLink != null && deepLink.isNotEmpty;
      DigiaInstance.instance.events.analytics(
        'Digia Step Clicked',
        payload,
        properties: inlineStepProperties(
          displayStyle: 'carousel',
          itemIndex: index,
          itemTotal: config.inlineConfig.items.length,
          actionType: hasLink ? 'deeplink' : null,
          actionUrl: hasLink ? deepLink : null,
        ),
      );
    }

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

  /// Wraps inline content in the slot's [VariableScope], built from the trigger
  /// payload's variables, so descendants resolve `{{ placeholder }}` copy
  /// through [VariableScopeProvider] (same mechanism as nudges).
  Widget _scoped(Widget child) {
    final payload =
        DigiaInstance.instance.controller.getSlot(widget.placementKey);
    // Dashboard-declared defaults first, CEP trigger variables layered on top.
    return VariableScopeProvider(
      scope: VariableScope({
        ...?_currentConfig?.defaultVariables,
        ...?payload?.variables,
      }),
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
            onPageChanged: _onCarouselPageChanged,
          ),
        );
      case InlineStoryCampaignConfig(:final storyConfig):
        _scheduleDigiaImpressionIfNeeded();
        return _scoped(DigiaInlineStory(config: storyConfig));
      case _:
        return widget.placeholder ?? const SizedBox.shrink();
    }
  }
}
