import 'package:flutter/material.dart';
import 'package:onboarding_overlay/onboarding_overlay.dart';

import '../../models/digia_experience_event.dart';
import '../digia_instance.dart';
import '../variable_scope.dart';
import 'guide_config_model.dart';
import 'guide_orchestrator.dart';
import 'onboarding_step_factory.dart';

/// Top-level host for guide (tooltip / spotlight) campaigns.
///
/// Mounted once by [DigiaHost] as a sibling overlay. It listens to the
/// [GuideOrchestrator]; when a guide becomes active it builds an
/// onboarding_overlay session (its own [Overlay], so it works above the app's
/// Navigator just like the survey renderer) and shows it. Step content is
/// produced by [OnboardingStepFactory] → `GuideBubble`.
///
/// Lifecycle events are reported through the same [DigiaInstance.events] emitter
/// the other surfaces use, keeping analytics consistent:
/// * first show → [ExperienceImpressed]
/// * action tap → [ExperienceClicked]
/// * advance past the last step → [ExperienceCompleted]
/// * dismiss (button / scrim / engine end) → [ExperienceDismissed]
class GuideRenderer extends StatefulWidget {
  const GuideRenderer({super.key});

  @override
  State<GuideRenderer> createState() => _GuideRendererState();
}

class _GuideRendererState extends State<GuideRenderer> {
  final GuideOrchestrator _orchestrator =
      DigiaInstance.instance.guideOrchestrator;
  final GlobalKey<OnboardingState> _onboardingKey =
      GlobalKey<OnboardingState>();
  static const _factory = OnboardingStepFactory();

  ActiveGuideState? _active;
  bool _finishing = false;

  @override
  void initState() {
    super.initState();
    _orchestrator.addListener(_onOrchestratorChanged);
  }

  @override
  void dispose() {
    _orchestrator.removeListener(_onOrchestratorChanged);
    super.dispose();
  }

  void _onOrchestratorChanged() {
    final next = _orchestrator.state;
    final current = _active;

    // Guide ended elsewhere (invalidation / dismiss): tear the overlay down.
    if (next == null) {
      if (current != null) {
        _hideOverlay();
        setState(() => _active = null);
      }
      return;
    }

    // A new guide was routed.
    if (current == null || current.token != next.token) {
      final firstAnchor = next.config.steps.first.anchorKey;
      if (!DigiaInstance.instance.anchorRegistry.isMounted(firstAnchor)) {
        debugPrint(
          "[Digia] guide '${next.campaign.campaignKey}' skipped: "
          "anchor '$firstAnchor' is not on screen.",
        );
        _orchestrator.dismiss();
        return;
      }
      _finishing = false;
      setState(() => _active = next);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || _active?.token != next.token) return;
        _onboardingKey.currentState?.show();
        DigiaInstance.instance.events
            .toAll(const ExperienceImpressed(), next.payload);
      });
    }
  }

  void _hideOverlay() {
    final state = _onboardingKey.currentState;
    if (state != null && state.isVisible()) state.hide();
  }

  VariableScope _scopeFor(ActiveGuideState active) => VariableScope({
        ...active.campaign.config.defaultVariables,
        ...?active.payload.variables,
      });

  List<OnboardingStep> _buildSteps(ActiveGuideState active) {
    final scope = _scopeFor(active);
    final steps = active.config.steps;
    final registry = DigiaInstance.instance.anchorRegistry;
    return [
      for (var i = 0; i < steps.length; i++)
        _factory.build(
          step: steps[i],
          focusNode: registry.focusNodeFor(steps[i].anchorKey),
          scope: scope,
          index: i,
          total: steps.length,
          multiStep: active.config.multiStep,
          onAction: (action, info) => _handleAction(i, action, info),
          onDismiss: () => _finish(completed: false),
        ),
    ];
  }

  void _handleAction(
    int index,
    GuideAction action,
    OnboardingStepRenderInfo info,
  ) {
    final active = _active;
    if (active == null) return;

    DigiaInstance.instance.events
        .toAll(ExperienceClicked(elementId: action.id), active.payload);

    switch (action.actionType) {
      case GuideActionType.next:
        if (index < active.config.steps.length - 1) {
          info.nextStep();
        } else {
          _finish(completed: true);
        }
        break;
      case GuideActionType.prev:
        if (index > 0) _goToPrevious(index - 1);
        break;
      case GuideActionType.dismiss:
        _finish(completed: false);
        break;
    }
  }

  void _goToPrevious(int targetIndex) {
    final state = _onboardingKey.currentState;
    if (state == null) return;
    if (state.isVisible()) state.hide();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _onboardingKey.currentState?.showFromIndex(targetIndex);
    });
  }

  void _finish({required bool completed}) {
    final active = _active;
    if (active == null || _finishing) return;
    _finishing = true;
    DigiaInstance.instance.events.toAll(
      completed ? const ExperienceCompleted() : const ExperienceDismissed(),
      active.payload,
    );
    _orchestrator.dismiss(); // → _onOrchestratorChanged tears the overlay down
  }

  @override
  Widget build(BuildContext context) {
    final active = _active;
    if (active == null) return const SizedBox.shrink();

    // onboarding_overlay inserts its session into the nearest [Overlay]; we
    // provide one (with a Directionality, absent above the app's Navigator) so
    // the guide layers above all app content, like the survey renderer.
    return Directionality(
      textDirection: TextDirection.ltr,
      child: Overlay(
        initialEntries: [
          OverlayEntry(
            builder: (_) => Onboarding(
              key: _onboardingKey,
              steps: _buildSteps(active),
              onEnd: (_) {
                // The engine ended the session (e.g. last step advanced through
                // its own machinery) without our finish path — treat as dismiss.
                if (!_finishing) _finish(completed: false);
              },
              child: const SizedBox.expand(),
            ),
          ),
        ],
      ),
    );
  }
}
