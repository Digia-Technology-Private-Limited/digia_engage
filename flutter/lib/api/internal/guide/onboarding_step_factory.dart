import 'package:flutter/material.dart';
import 'package:onboarding_overlay/onboarding_overlay.dart';

import '../variable_scope.dart';
import 'guide_bubble.dart';
import 'guide_config_model.dart';

/// Adapts a [GuideStepModel] (our cross-platform config) into the
/// [OnboardingStep] the onboarding_overlay engine consumes.
///
/// This is the single seam between Digia's guide model and the third-party
/// overlay package: it maps the tooltip/spotlight switch (`overlay.visible`),
/// the cutout shape and the scrim colour onto the package's API, and delegates
/// all visible content to [GuideBubble] via `stepBuilder` so the bubble UI
/// stays identical to the other platforms. Keeping this isolated means swapping
/// the overlay engine later touches only this file.
class OnboardingStepFactory {
  const OnboardingStepFactory();

  OnboardingStep build({
    required GuideStepModel step,
    required FocusNode focusNode,
    required VariableScope scope,
    required int index,
    required int total,
    required bool multiStep,
    required void Function(GuideAction action, OnboardingStepRenderInfo info)
        onAction,
    required VoidCallback onDismiss,
  }) {
    final widget = step.widgetConfig;
    final overlay = widget.overlay;
    final isSpotlight = overlay.visible;

    return OnboardingStep(
      focusNode: focusNode,
      // Title/body are rendered by GuideBubble; the package still requires a
      // titleText and text colours, so we pass inert values.
      titleText: '',
      titleTextColor: const Color(0xFFFFFFFF),
      bodyText: '',
      bodyTextColor: const Color(0xFFFFFFFF),
      // Spotlight dims the screen; tooltip uses a transparent (no-dim) scrim.
      overlayColor: isSpotlight
          ? overlay.color.withAlpha((overlay.alpha.clamp(0.0, 1.0) * 255).round())
          : const Color(0x00000000),
      shape: _holeShape(overlay.cutout),
      margin: EdgeInsets.all(overlay.cutout.padding),
      fullscreen: false,
      hasLabelBox: false,
      hasArrow: false,
      overlayBehavior: HitTestBehavior.opaque,
      onTapCallback: (TapArea area, VoidCallback next, VoidCallback close) {
        if (area == TapArea.overlay && overlay.dismissOnTap) {
          onDismiss();
        }
      },
      stepBuilder: (BuildContext context, OnboardingStepRenderInfo info) {
        return GuideBubble(
          config: widget,
          scope: scope,
          stepIndex: index,
          stepTotal: total,
          multiStep: multiStep,
          maxWidth: info.size.width,
          onActionTap: (action) => onAction(action, info),
        );
      },
    );
  }

  ShapeBorder _holeShape(CutoutConfig cutout) {
    switch (cutout.shape.trim().toLowerCase()) {
      case 'circle':
        return const CircleBorder();
      case 'rect':
        return const RoundedRectangleBorder(borderRadius: BorderRadius.zero);
      case 'rounded_rect':
      default:
        return RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(cutout.cornerRadius),
        );
    }
  }
}
