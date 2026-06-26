import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../src/vendor/showcaseview/showcaseview.dart';
import '../../models/digia_experience_event.dart';
import '../action/engage_action.dart';
import '../action/engage_action_context.dart';
import '../action/engage_action_handler.dart';
import '../event/dwell_tracker.dart';
import '../event/engage_analytics_event.dart';
import '../event/engage_event_emitter.dart';
import '../frequency/frequency_manager.dart';
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

  /// Whether scrim/barrier taps are inert (`outsideTapBehavior == 'nothing'`).
  /// When false, [onBarrierClick] (if set) runs, else the engine advances.
  final bool disableBarrierInteraction;

  /// What a scrim/barrier tap should do, for `outsideTapBehavior == 'dismiss'`.
  /// Null means use the engine default (advance) — i.e. `'next'`.
  final VoidCallback? onBarrierClick;

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
    required this.disableBarrierInteraction,
    required this.onBarrierClick,
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
  final FrequencyManager Function() _frequency;
  final DwellTracker _dwell;
  final String? Function() _screenName;

  GuideShowcaseManager({
    required GuideOrchestrator orchestrator,
    required AnchorRegistry registry,
    required EngageEventEmitter Function() events,
    required FrequencyManager Function() frequency,
    required DwellTracker dwell,
    required String? Function() screenName,
  })  : _orchestrator = orchestrator,
        _registry = registry,
        _events = events,
        _frequency = frequency,
        _dwell = dwell,
        _screenName = screenName {
    _register();
    _orchestrator.addListener(_onOrchestratorChanged);
  }

  int? _shownToken;
  bool _finishing = false;

  /// Set once a CTA tap on the last/only step has fired the guide's "Completed".
  /// Reset per showing. Guards against a trailing close re-counting as a
  /// completion and against emitting a step-dismiss after a real completion.
  bool _completedFired = false;

  /// True while a delayed step transition (a button next/prev waiting out the
  /// target step's `delayInMs`) is pending, so a second tap can't queue a second
  /// advance. Cleared when the next step actually shows.
  bool _advancing = false;

  /// The `anchorKey`s actually shown this session, in showcase order — parallel
  /// to the keys handed to `startShowCase`, so an `onStart` index maps back to
  /// its anchor (and through it, the full-config step index). Rebuilt per guide.
  List<String> _activeAnchorKeys = const [];

  /// 1-based index (into the full config's steps) of the step on screen, and its
  /// anchor — tracked on each `onStart` so step clicks / dismissal can report
  /// which step they happened on. Mirrors the wire's `item_index` / `anchor_key`.
  int _currentItemIndex = 1;

  /// 0-based position of the current step within [_activeAnchorKeys] (the shown
  /// sequence), used to decide whether a termination landed on the final step.
  int _currentShownIndex = 0;

  /// Whether the step on screen is the last one in the active (shown) sequence.
  /// Reaching it means the user saw every step, so any close from here counts as
  /// a completion rather than an early abandonment.
  bool get _isOnLastActiveStep =>
      _activeAnchorKeys.isNotEmpty &&
      _currentShownIndex >= _activeAnchorKeys.length - 1;

  void _register() {
    ShowcaseView.register(
      scope: kDigiaGuideScope,
      autoPlay: false,
      // Auto-scroll a scrolled-away anchor into view before showing its step
      // (single-target only; shows a brief loader, then `ensureVisible`).
      enableAutoScroll: true,
      onStart: (index, key) => _onStepShown(index ?? 0),
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
    _completedFired = false;

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
      final keys = <GlobalKey>[];
      final shownAnchorKeys = <String>[];
      for (final anchorKey in state.config.anchorKeys) {
        if (_view.isTargetRendered(_registry.keyFor(anchorKey))) {
          keys.add(_registry.keyFor(anchorKey));
          shownAnchorKeys.add(anchorKey);
        }
      }
      _activeAnchorKeys = shownAnchorKeys;
      _currentItemIndex = 1;
      _currentShownIndex = 0;
      _advancing = false;
      // Honor the first step's `delayInMs` before the guide appears (RN waits
      // the step's delay before measuring/showing it). Token-guarded so a guide
      // swapped out during the wait doesn't pop in late.
      final firstDelay = shownAnchorKeys.isEmpty
          ? 0
          : _stepDelayMs(state.config, shownAnchorKeys.first);
      if (firstDelay <= 0) {
        _view.startShowCase(keys);
      } else {
        Future<void>.delayed(Duration(milliseconds: firstDelay), () {
          if (_orchestrator.state?.token != state.token) return;
          _view.startShowCase(keys);
        });
      }
    });
  }

  // ─── Per-anchor presentation (consumed by DigiaAnchor) ────────────────────

  /// The presentation for [anchorKey] under the active guide, or `null` when no
  /// guide is active or no step targets this anchor.
  ShowcasePresentation? presentationFor(String anchorKey) {
    final state = _orchestrator.state;
    if (state == null) return null;
    final scope = VariableScope.fromSchemas(
      state.campaign.config.defaultVariables,
      state.payload.variables,
    );
    final config = state.config;

    // Honor the dashboard's `outsideTapBehavior` for scrim/barrier taps instead
    // of the engine default (which always advances — silently completing the
    // last step and stealing taps meant for the bubble button). 'nothing' makes
    // the scrim inert (buttons drive the flow); 'dismiss' dismisses; 'next'
    // (default) keeps the engine's advance.
    final behavior = config.outsideTapBehavior.trim().toLowerCase();
    final disableBarrier = behavior == 'nothing';
    final onBarrier = behavior == 'dismiss' ? () => _view.dismiss() : null;

    if (config is TooltipGuideConfig) {
      final step = _firstWhere(config.steps, (s) => s.anchorKey == anchorKey);
      if (step == null) return null;
      return ShowcasePresentation(
        container: GuideBubble.tooltip(
            step: step, scope: scope, onAction: handleAction),
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
        disableBarrierInteraction: disableBarrier,
        onBarrierClick: onBarrier,
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
        disableBarrierInteraction: disableBarrier,
        onBarrierClick: onBarrier,
      );
    }
    return null;
  }

  // ─── Button actions ───────────────────────────────────────────────────────

  void handleAction(GuideAction action) {
    final state = _orchestrator.state;
    if (state == null) return;
    // Guides only have Step Clicked in the matrix (no Experience Clicked), so
    // the click is Digia analytics only — never forwarded to the CEP plugin.
    _events().toDigia(
      GuideStepClicked(
        itemIndex: _currentItemIndex,
        ctaLabel: action.label,
        actionType: _actionTypeWire(action.type),
        actionUrl: action.url,
      ),
      state.payload,
    );

    // A CTA tap (anything but back/prev) on the last or only step is the guide's
    // "Completed" — the single place completion is decided. Scrim taps, the close
    // button, the back gesture, and an outside-tap advance never reach here, so
    // they stay plain dismissals. Mirrors RN's handleActionPress rule.
    if (_isOnLastActiveStep &&
        !_completedFired &&
        action.type != GuideActionType.back &&
        action.type != GuideActionType.prev) {
      _completedFired = true;
      // Permanent stop when the policy opted into stopOn: experienceCompleted.
      _frequency().recordCompleted(state.campaign);
      _events().toDigia(
        GuideCompleted(
          itemTotal: state.config.stepCount,
          timeToCompleteMs: _dwell.elapsedMs(state.payload.cepCampaignId),
        ),
        state.payload,
      );
    }

    switch (action.type) {
      case GuideActionType.next:
        _transition(forward: true);
        break;
      case GuideActionType.back:
      case GuideActionType.prev:
        _transition(forward: false);
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

  /// Advances (or rewinds) the showcase, honoring the destination step's
  /// `delayInMs` before it appears — mirroring RN, which on every step entry
  /// hides the current step, waits the delay, then shows the next (a blank gap
  /// in between). A pending delayed transition swallows further taps
  /// ([_advancing]); leaving the active sequence (a completion) carries no delay.
  void _transition({required bool forward}) {
    final state = _orchestrator.state;
    if (state == null || _advancing) return;
    final targetIndex =
        forward ? _currentShownIndex + 1 : _currentShownIndex - 1;
    final delayMs = (targetIndex >= 0 && targetIndex < _activeAnchorKeys.length)
        ? _stepDelayMs(state.config, _activeAnchorKeys[targetIndex])
        : 0;
    if (delayMs <= 0) {
      _runTransition(forward);
      return;
    }
    // Hide the current bubble now; advancing after the delay re-shows the
    // overlay on the destination step (and fires its StepViewed then).
    _advancing = true;
    _view.hideOverlay();
    Future<void>.delayed(Duration(milliseconds: delayMs), () {
      if (_orchestrator.state?.token != state.token) {
        _advancing = false;
        return;
      }
      _runTransition(forward);
    });
  }

  void _runTransition(bool forward) =>
      forward ? _view.next() : _view.previous();

  /// The configured `delayInMs` for the step targeting [anchorKey], or 0.
  static int _stepDelayMs(GuideConfig config, String anchorKey) {
    if (config is TooltipGuideConfig) {
      for (final s in config.steps) {
        if (s.anchorKey == anchorKey) return s.delayInMs ?? 0;
      }
    } else if (config is SpotlightGuideConfig) {
      for (final s in config.steps) {
        if (s.anchorKey == anchorKey) return s.delayInMs ?? 0;
      }
    }
    return 0;
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
      copy: (_) async {},
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

  /// Fired by showcaseview as each step appears. The first step is also the
  /// guide-level "Viewed"; every step emits a rich step-viewed to Digia. Mirrors
  /// Android's `GuideEvent.Viewed` / `GuideEvent.StepViewed` and the RN wire.
  void _onStepShown(int index) {
    final state = _orchestrator.state;
    if (state == null) return;
    _currentShownIndex = index;
    _advancing = false;
    final anchorKey = (index >= 0 && index < _activeAnchorKeys.length)
        ? _activeAnchorKeys[index]
        : null;
    // Report the step's position within the full config (some anchors may have
    // been skipped because they weren't on screen), 1-based like the RN wire.
    final fullIndex =
        anchorKey == null ? index : state.config.anchorKeys.indexOf(anchorKey);
    _currentItemIndex = fullIndex + 1;

    final total = state.config.stepCount;
    final style = _displayStyle(state.config);

    if (index == 0) {
      // Count one show toward the guide's frequency cap (its "Viewed").
      _frequency().recordShow(state.campaign);
      _dwell.markViewed(state.payload.cepCampaignId);
      // Coarse impression to the CEP plugin; rich guide-viewed to Digia.
      _events().toBoth(
        const ExperienceImpressed(),
        GuideViewed(
          displayStyle: style,
          itemTotal: total,
          screenName: _screenName(),
        ),
        state.payload,
      );
    }

    // Step views have no coarse CEP counterpart — Digia analytics only.
    _events().toDigia(
      GuideStepViewed(
        itemIndex: _currentItemIndex,
        itemTotal: total,
        anchorKey: anchorKey,
        displayStyle: style,
      ),
      state.payload,
    );
  }

  /// [completed] is true when showcaseview advanced past the last step (onFinish);
  /// a close — dismiss button, scrim, back, or an outside-tap advance — arrives
  /// with it false.
  ///
  /// Completion is *not* decided here: it is fired in [handleAction] on a CTA tap
  /// on the last/only step (mirrors RN). Every terminal close emits Dismissed —
  /// the CEP plugin's unconditional teardown signal, even right after a
  /// completion. A close that was *not* a completion, on a multi-step guide, also
  /// emits the step-level dismiss (RN's `dismiss()` → `step_dismissed`).
  void _finish({required bool completed}) {
    if (_finishing) return;
    final state = _orchestrator.state;
    if (state == null) return;
    _finishing = true;
    final total = state.config.stepCount;
    final elapsedMs = _dwell.consumeDwellMs(state.payload.cepCampaignId);
    // A single-step guide has no completion semantics (matches RN) — closing the
    // only step is just a dismiss. For multi-step, reaching the final step (by
    // advancing past it or closing it) is a completion.
    final reachedEnd =
        _activeAnchorKeys.length > 1 && (completed || _isOnLastActiveStep);

    // Mirrors RN exactly. Reaching the end adds Completed first (RN's last-step
    // CTA / `complete()`). Every terminal close then emits Dismissed — the CEP
    // plugin's unconditional teardown signal, even right after a completion. A
    // *close* (the onDismiss path, `completed == false`) on a multi-step guide
    // also emits the step-level dismiss (RN's `dismiss()` → `step_dismissed`);
    // an advance-past-last (`completed == true`) does not. Step-level dismiss is
    // Digia-only — the coarse channel has no per-step dismiss.
    if (reachedEnd) {
      // Completing the guide is its CEP engagement signal — the coarse channel
      // has no "completed", so a completion maps to ExperienceClicked.
      _events().toCep(const ExperienceClicked(), state.payload);
      _events().toDigia(
        GuideCompleted(itemTotal: total, timeToCompleteMs: elapsedMs),
        state.payload,
      );
    }
    _events().toBoth(
      const ExperienceDismissed(),
      GuideDismissed(
        abandonedAtItem: _currentItemIndex,
        itemTotal: total,
        dwellMs: elapsedMs,
      ),
      state.payload,
    );
    if (!_completedFired && !completed && total > 1) {
      _events().toDigia(
        GuideStepDismissed(itemIndex: _currentItemIndex),
        state.payload,
      );
    }
    _orchestrator.dismiss();
  }

  // ─── Mapping helpers ──────────────────────────────────────────────────────

  static String _displayStyle(GuideConfig config) =>
      config is SpotlightGuideConfig ? 'spotlight' : 'tooltip';

  // Inverse of `_actionTypeFrom` — the raw wire token the dashboard configured,
  // matching what RN forwards as `action_type`.
  static String _actionTypeWire(GuideActionType type) {
    switch (type) {
      case GuideActionType.next:
        return 'next';
      case GuideActionType.back:
        return 'back';
      case GuideActionType.prev:
        return 'prev';
      case GuideActionType.deepLink:
        return 'deep_link';
      case GuideActionType.openUrl:
        return 'open_url';
      case GuideActionType.fireEvent:
        return 'fire_event';
      case GuideActionType.dismiss:
        return 'dismiss';
    }
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
        return RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(9999));
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
