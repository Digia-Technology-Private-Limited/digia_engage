import 'package:flutter/material.dart';
import 'package:showcaseview/showcaseview.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../models/digia_experience_event.dart';
import '../action/engage_action.dart';
import '../action/engage_action_context.dart';
import '../action/engage_action_handler.dart';
import '../engage_fonts.dart';
import '../event/engage_event_emitter.dart';
import '../variable_scope.dart';
import 'anchor_registry.dart';
import 'guide_action_button.dart';
import 'guide_callout.dart';
import 'guide_config_model.dart';
import 'guide_orchestrator.dart';

/// The scope all Digia guide showcases share, so they never collide with any
/// showcaseview the host app might run itself.
const String kDigiaGuideScope = 'digia_guide';

/// What [DigiaAnchor] should present for the active step.
///
/// Tooltips use showcaseview's **built-in** tooltip ([TooltipShowcase]) so the
/// package draws its own placement-aware arrow (the resolved side is internal to
/// its render delegate, so a custom container couldn't). Spotlights use a fully
/// custom callout ([SpotlightShowcase] → `Showcase.withWidget`), which needs no
/// arrow.
sealed class GuidePresentation {
  final Color overlayColor;
  final double overlayOpacity;
  final ShapeBorder targetShapeBorder;
  final EdgeInsets targetPadding;
  final TooltipPosition? tooltipPosition;

  const GuidePresentation({
    required this.overlayColor,
    required this.overlayOpacity,
    required this.targetShapeBorder,
    required this.targetPadding,
    required this.tooltipPosition,
  });
}

/// A tooltip step → showcaseview's standard `Showcase` (title/description/arrow).
class TooltipShowcase extends GuidePresentation {
  final String title;
  final String description;
  final TextStyle titleTextStyle;
  final TextStyle descTextStyle;
  final Color tooltipBackgroundColor;
  final BorderRadius tooltipBorderRadius;
  final EdgeInsets tooltipPadding;
  final bool showArrow;
  final List<TooltipActionButton> actions;
  final TooltipActionConfig actionConfig;

  const TooltipShowcase({
    required this.title,
    required this.description,
    required this.titleTextStyle,
    required this.descTextStyle,
    required this.tooltipBackgroundColor,
    required this.tooltipBorderRadius,
    required this.tooltipPadding,
    required this.showArrow,
    required this.actions,
    required this.actionConfig,
    required super.overlayColor,
    required super.overlayOpacity,
    required super.targetShapeBorder,
    required super.targetPadding,
    required super.tooltipPosition,
  });
}

/// A spotlight step → `Showcase.withWidget` with a custom [container] callout.
class SpotlightShowcase extends GuidePresentation {
  final Widget container;

  const SpotlightShowcase({
    required this.container,
    required super.overlayColor,
    required super.overlayOpacity,
    required super.targetShapeBorder,
    required super.targetPadding,
    required super.tooltipPosition,
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
      enableAutoScroll: false,
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
  GuidePresentation? presentationFor(String anchorKey) {
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
      return TooltipShowcase(
        title: scope.resolve(step.title),
        description: scope.resolve(step.body),
        titleTextStyle: _textStyle(step.titleColor, step.titleSize, step.titleWeight),
        descTextStyle: _textStyle(step.bodyColor, step.bodySize, FontWeight.w400),
        tooltipBackgroundColor: step.backgroundColor,
        tooltipBorderRadius: BorderRadius.circular(step.cornerRadius),
        tooltipPadding: EdgeInsets.all(step.padding),
        showArrow: step.showArrow,
        tooltipPosition: _tooltipPosition(step.placement),
        // Tooltip = no dim (RN renders the bubble over a transparent scrim).
        overlayColor: const Color(0x00000000),
        overlayOpacity: 0,
        targetShapeBorder:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        targetPadding: EdgeInsets.zero,
        actions: _actions(step.actions, scope, step.buttonPrimaryBackgroundColor,
            step.buttonPrimaryTextColor, step.buttonGhostTextColor),
        actionConfig: _actionConfig,
      );
    }
    if (config is SpotlightGuideConfig) {
      final step = _firstWhere(config.steps, (s) => s.anchorKey == anchorKey);
      if (step == null) return null;
      return SpotlightShowcase(
        container: GuideCallout(
          step: step,
          scope: scope,
          onAction: handleAction,
        ),
        overlayColor: step.overlayColor,
        overlayOpacity: step.overlayOpacity,
        targetShapeBorder: _highlightShape(step),
        targetPadding: EdgeInsets.all(step.highlightPadding),
        tooltipPosition: _calloutPosition(step.calloutPosition),
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

  static const TooltipActionConfig _actionConfig = TooltipActionConfig(
    alignment: MainAxisAlignment.end,
    position: TooltipActionPosition.inside,
    actionGap: 8,
    gapBetweenContentAndAction: 12,
  );

  TextStyle _textStyle(Color color, double size, FontWeight weight) => TextStyle(
        color: color,
        fontSize: size,
        fontWeight: weight,
        fontFamily: EngageFonts.fontFamily,
      );

  List<TooltipActionButton> _actions(
    List<GuideAction> actions,
    VariableScope scope,
    Color primaryBg,
    Color primaryText,
    Color ghostText,
  ) {
    return [
      for (final action in actions)
        TooltipActionButton.custom(
          button: GuideActionButton(
            label: scope.resolve(action.label),
            isPrimary: action.style == GuideActionStyle.primary,
            primaryBg: primaryBg,
            primaryText: primaryText,
            ghostText: ghostText,
            onTap: () => handleAction(action),
          ),
        ),
    ];
  }

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
