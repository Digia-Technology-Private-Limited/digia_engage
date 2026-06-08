import 'package:flutter/material.dart';

import '../internal/action/engage_action_context.dart';
import '../internal/campaign/campaign_model.dart';
import '../internal/digia_instance.dart';
import '../internal/digia_overlay_controller.dart';
import '../internal/nudge/nudge_config.dart';
import '../internal/nudge/nudge_presenter.dart';
import '../models/digia_experience_event.dart';

/// Wraps the application root and renders in-app message overlays
/// (dialogs, bottom sheets, PIPs, fullscreen) above all app content.
///
/// Place this widget once, at the root of your application. The recommended
/// placement is in [MaterialApp.builder]:
///
/// ```dart
/// MaterialApp(
///   navigatorObservers: [DigiaNavigatorObserver()],
///   builder: (context, child) => DigiaHost(child: child!),
/// )
/// ```
///
/// Placing [DigiaHost] multiple times or below the navigation root
/// produces undefined behavior — the SDK logs a warning.
///
/// Marketing name: "In-App Messages" → [DigiaHost]
class DigiaHost extends StatefulWidget {
  /// Navigator key that must be passed to [MaterialApp.navigatorKey].
  ///
  /// [DigiaHost] sits in [MaterialApp.builder], which is above the app's
  /// [Navigator] in the widget tree. Dialogs and bottom sheets require a
  /// context that is a descendant of that navigator; this key provides it.
  ///
  /// ```dart
  /// MaterialApp(
  ///   navigatorKey: DigiaHost.navigatorKey,
  ///   navigatorObservers: [DigiaNavigatorObserver()],
  ///   builder: (context, child) => DigiaHost(child: child!),
  /// )
  /// ```
  final GlobalKey<NavigatorState>? navigatorKey;

  /// The application widget tree to render below the overlay layer.
  final Widget child;

  const DigiaHost({required this.child, this.navigatorKey, super.key});

  @override
  State<DigiaHost> createState() => _DigiaHostState();
}

class _DigiaHostState extends State<DigiaHost> {
  late final DigiaOverlayController _controller;

  @override
  void initState() {
    super.initState();
    _controller = DigiaInstance.instance.controller;
    _controller.addListener(_onControllerChanged);
    DigiaInstance.instance.onHostMounted();
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    DigiaInstance.instance.onHostUnmounted();
    super.dispose();
  }

  void _onControllerChanged() {
    final campaign = DigiaInstance.instance.resolveActiveCampaign();
    if (campaign == null) return;

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      switch (campaign.config) {
        case NudgeCampaignConfig(:final nudgeConfig):
          _presentNudge(campaign, nudgeConfig);
        case InlineCarouselCampaignConfig():
        case InlineStoryCampaignConfig():
          // Inline campaigns are handled by DigiaSlot — nothing to do here.
          _controller.dismiss();
        case UnsupportedCampaignConfig():
          _controller.dismiss();
      }
    });
  }

  void _presentNudge(CampaignModel campaign, NudgeConfig config) {
    final payload = _controller.activePayload;
    if (payload == null) return;

    final navContext = widget.navigatorKey?.currentContext ??
        DigiaInstance.instance.navigator?.context ??
        context;

    _controller.onEvent?.call(const ExperienceImpressed(), payload);

    presentNudge(
      context: navContext,
      config: config,
      actionContext: EngageActionContext(
        campaignId: campaign.id,
        campaignKey: campaign.campaignKey,
        surface: EngageSurface.nudge,
      ),
    ).whenComplete(() {
      _controller.onEvent?.call(const ExperienceDismissed(), payload);
      _controller.dismiss();
    });
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }
}
