import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../src/vendor/showcaseview/showcaseview.dart';
import '../../models/digia_experience_event.dart';
import '../action/engage_action.dart';
import '../action/engage_action_context.dart';
import '../action/engage_action_handler.dart';
import '../event/engage_event_emitter.dart';
import '../variable_scope.dart';
import 'anchor_registry.dart';
import 'guide_bubble.dart';
import 'guide_config_model.dart';
import 'guide_orchestrator.dart';

/// The scope all Digia guide showcases share, so they never collide with any
/// showcaseview the host app might run itself.
const String kDigiaGuideScope = 'digia_guide';

/// What [DigiaAnchor] should hand `Showcase.withWidget` for the active step.
///
/// Both tooltip and spotlight render our own exact [container] bubble; the
/// (vendored, patched) showcaseview draws the placement-aware arrow at the
/// resolved side, in [arrowColor], so the bubble stays exact and the arrow
/// stays correct on flip.
class ShowcasePresentation {
  final Widget container;
  final Color overlayColor;
  final double overlayOpacity;
  final ShapeBorder targetShapeBorder;
  final EdgeInsets targetPadding;
  final TooltipPosition? tooltipPosition;
  final bool showArrow;
  final Color arrowColor;
  final Color? arrowBorderColor;
  final double arrowBorderWidth;

  const ShowcasePresentation({
    required this.container,
    required this.overlayColor,
    required this.overlayOpacity,
    required this.targetShapeBorder,
    required this.targetPadding,
    required this.tooltipPosition,
    required this.showArrow,
    required this.arrowColor,
    required this.arrowBorderColor,
    required this.arrowBorderWidth,
  });
}

/// Bridges the [GuideOrchestrator] to the showcaseview engine.
///
/// Owns the single seam to the package: registers the [ShowcaseView]
/// controller, starts a showcase when a guide is routed, maps each step's flat
/// (RN-shaped) config into a [ShowcasePresentation] for [DigiaAnchor], routes
/// button taps (next / back / dismiss / links) and emits lifecycle analytics.
class GuideShowcaseManager {
  final GuideOrchestrator _orchestrator;
  final AnchorRegistry _registry;
  final EngageEventEmitter Function() _events;

  GuideShowcaseManager({
    required GuideOrchestrator orchestrator,
    required AnchorRegistry registry,
    required EngageEventEmitter Function() events,
  })  : _orchestrator = orchestrator,
        _registry = registry,
        _events = events {
    _register();
    _orchestrator.addListener(_onOrchestratorChanged);
  }

  int? _shownToken;
  bool _finishing = false;

  void _register() {
    ShowcaseView.register(
      scope: kDigiaGuideScope,
      autoPlay: false,
      // Auto-scroll a scrolled-away anchor into view before showing its step
      // (single-target only; shows a brief loader, then `ensureVisible`).
      enableAutoScroll: true,
      onStart: (index, key) {
        if (index == 0) _emit(const ExperienceImpressed());
      },
      onFinish: () => _finish(completed: true),
      onDismiss: (_) => _finish(completed: false),
    );
  }

  ShowcaseView get _view => ShowcaseView.getNamed(kDigiaGuideScope);

  // ─── Orchestrator → showcase ─────────────────────────────────────────────

  void _onOrchestratorChanged() {
    final state = _orchestrator.state;
    if (state == null) {
      if (_view.isShowcaseRunning) _view.dismiss();
      _shownToken = null;
      return;
    }
    if (state.token == _shownToken) return;
    _shownToken = state.token;
    _finishing = false;

    // Wait a frame so the matching DigiaAnchors rebuild as Showcases before we
    // ask the controller to run them.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_orchestrator.state?.token != state.token) return;
      final firstKey = _registry.keyFor(state.config.firstAnchorKey);
      if (!_view.isTargetRendered(firstKey)) {
        debugPrint(
          "[Digia] guide '${state.campaign.campaignKey}' skipped: "
          "anchor '${state.config.firstAnchorKey}' is not on screen.",
        );
        _orchestrator.dismiss();
        return;
      }
      final keys = <GlobalKey>[
        for (final anchorKey in state.config.anchorKeys)
          if (_view.isTargetRendered(_registry.keyFor(anchorKey)))
            _registry.keyFor(anchorKey),
      ];
      _view.startShowCase(keys);
    });
  }

  // ─── Per-anchor presentation (consumed by DigiaAnchor) ────────────────────

  /// The presentation for [anchorKey] under the active guide, or `null` when no
  /// guide is active or no step targets this anchor.
  ShowcasePresentation? presentationFor(String anchorKey) {
    final state = _orchestrator.state;
    if (state == null) return null;
    final scope = VariableScope({
      ...state.campaign.config.defaultVariables,
      ...?state.payload.variables,
    });
    final config = state.config;

    if (config is TooltipGuideConfig) {
      final step = _firstWhere(config.steps, (s) => s.anchorKey == anchorKey);
      if (step == null) return null;
      return ShowcasePresentation(
        container:
            GuideBubble.tooltip(step: step, scope: scope, onAction: handleAction),
        // Tooltip = no dim (RN renders the bubble over a transparent scrim).
        overlayColor: const Color(0x00000000),
        overlayOpacity: 0,
        targetShapeBorder:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        targetPadding: EdgeInsets.zero,
        tooltipPosition: _tooltipPosition(step.placement),
        showArrow: step.showArrow,
        // RN: arrowColor ?? backgroundColor — the arrow matches the bubble.
        arrowColor: step.arrowColor ?? step.backgroundColor,
        // RN: arrowBorderColor ?? borderColor; border width follows the bubble.
        arrowBorderColor: step.arrowBorderColor ?? step.borderColor,
        arrowBorderWidth: step.borderWidth,
      );
    }
    if (config is SpotlightGuideConfig) {
      final step = _firstWhere(config.steps, (s) => s.anchorKey == anchorKey);
      if (step == null) return null;
      return ShowcasePresentation(
        container: GuideBubble.spotlight(
            step: step, scope: scope, onAction: handleAction),
        overlayColor: step.overlayColor,
        overlayOpacity: step.overlayOpacity,
        targetShapeBorder: _highlightShape(step),
        targetPadding: EdgeInsets.all(step.highlightPadding),
        tooltipPosition: _calloutPosition(step.calloutPosition),
        showArrow: step.showArrow,
        arrowColor: step.arrowColor ?? step.calloutBackgroundColor,
        arrowBorderColor: step.arrowBorderColor ?? step.calloutBorderColor,
        arrowBorderWidth: step.calloutBorderWidth,
      );
    }
    return null;
  }

  // ─── Button actions ───────────────────────────────────────────────────────

  void handleAction(GuideAction action) {
    final state = _orchestrator.state;
    if (state == null) return;
    _events().toAll(ExperienceClicked(elementId: action.label), state.payload);

    switch (action.type) {
      case GuideActionType.next:
        _view.next();
        break;
      case GuideActionType.back:
      case GuideActionType.prev:
        _view.previous();
        break;
      case GuideActionType.dismiss:
        _view.dismiss();
        break;
      case GuideActionType.deepLink:
        _runLink(OpenDeeplinkAction(action.url ?? ''), state);
        break;
      case GuideActionType.openUrl:
        _runLink(OpenUrlAction(action.url ?? ''), state);
        break;
      case GuideActionType.fireEvent:
        // Custom analytics event — not yet bridged to the Flutter event model.
        break;
    }
  }

  void _runLink(LinkAction action, ActiveGuideState state) {
    if (action.target.isEmpty) return;
    final scope = EngageActionScope(
      dismiss: () => _view.dismiss(),
      openUri: (uri, {required external}) => launchUrl(
        uri,
        mode: external
            ? LaunchMode.externalApplication
            : LaunchMode.platformDefault,
      ),
      share: (_) async {},
    );
    EngageActionRunner.shared.run(
      [action],
      scope,
      EngageActionContext(
        campaignId: state.campaign.id,
        campaignKey: state.campaign.campaignKey,
        surface: EngageSurface.guide,
      ),
    );
  }

  // ─── Lifecycle events ─────────────────────────────────────────────────────

  void _finish({required bool completed}) {
    if (_finishing) return;
    final state = _orchestrator.state;
    if (state == null) return;
    _finishing = true;
    _events().toAll(
      completed ? const ExperienceCompleted() : const ExperienceDismissed(),
      state.payload,
    );
    _orchestrator.dismiss();
  }

  void _emit(DigiaExperienceEvent event) {
    final state = _orchestrator.state;
    if (state != null) _events().toAll(event, state.payload);
  }

  // ─── Mapping helpers ──────────────────────────────────────────────────────

  // Returns null for `auto` so showcaseview chooses + flips on available space
  // (it still draws a correct arrow either way).
  static TooltipPosition? _tooltipPosition(String placement) {
    switch (placement.trim().toLowerCase()) {
      case 'top':
        return TooltipPosition.top;
      case 'bottom':
        return TooltipPosition.bottom;
      case 'left':
        return TooltipPosition.left;
      case 'right':
        return TooltipPosition.right;
      default:
        return null; // auto
    }
  }

  static TooltipPosition? _calloutPosition(String position) {
    switch (position.trim().toLowerCase()) {
      case 'above':
        return TooltipPosition.top;
      case 'below':
        return TooltipPosition.bottom;
      case 'left':
        return TooltipPosition.left;
      case 'right':
        return TooltipPosition.right;
      default:
        return null; // auto
    }
  }

  static ShapeBorder _highlightShape(SpotlightStep step) {
    switch (step.highlightShape.trim().toLowerCase()) {
      case 'circle':
        return const CircleBorder();
      case 'pill':
        return RoundedRectangleBorder(borderRadius: BorderRadius.circular(9999));
      case 'rect':
      default:
        return RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(step.highlightCornerRadius),
        );
    }
  }

  static T? _firstWhere<T>(List<T> items, bool Function(T) test) {
    for (final item in items) {
      if (test(item)) return item;
    }
    return null;
  }
}
