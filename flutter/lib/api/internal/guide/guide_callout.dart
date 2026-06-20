import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../engage_fonts.dart';
import '../variable_scope.dart';
import 'guide_action_button.dart';
import 'guide_config_model.dart';

/// The spotlight callout — a fully custom card used as the showcaseview
/// `container` (via `Showcase.withWidget`) for spotlight steps.
///
/// A port of React Native's spotlight callout body in `DigiaProvider.tsx`:
/// title, optional body and a trailing action row, styled from the flat step
/// config. No arrow is drawn (spotlights present a free-floating callout next to
/// the highlighted cutout, not an arrow-tipped tooltip).
class GuideCallout extends StatelessWidget {
  final SpotlightStep step;
  final VariableScope scope;
  final ValueChanged<GuideAction> onAction;

  const GuideCallout({
    required this.step,
    required this.scope,
    required this.onAction,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final screenW = MediaQuery.of(context).size.width;
    final width = math.min(step.calloutMaxWidth, screenW - 32);
    final fontFamily = EngageFonts.fontFamily;

    return Container(
      width: width,
      padding: EdgeInsets.all(step.calloutPadding),
      decoration: BoxDecoration(
        color: step.calloutBackgroundColor,
        borderRadius: BorderRadius.circular(step.calloutCornerRadius),
        border: step.calloutBorderWidth > 0
            ? Border.all(
                color: step.calloutBorderColor, width: step.calloutBorderWidth)
            : null,
        boxShadow: step.calloutShadow
            ? const [
                BoxShadow(
                  color: Color(0x26000000),
                  blurRadius: 12,
                  offset: Offset(0, 4),
                ),
              ]
            : null,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (step.title.isNotEmpty)
            Text(
              scope.resolve(step.title),
              style: TextStyle(
                color: step.titleColor,
                fontSize: step.titleSize,
                fontWeight: step.titleWeight,
                fontFamily: fontFamily,
              ),
            ),
          if (step.body.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                scope.resolve(step.body),
                style: TextStyle(
                  color: step.bodyColor,
                  fontSize: step.bodySize,
                  fontFamily: fontFamily,
                ),
              ),
            ),
          if (step.actions.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  for (var i = 0; i < step.actions.length; i++) ...[
                    if (i > 0) const SizedBox(width: 8),
                    GuideActionButton(
                      label: scope.resolve(step.actions[i].label),
                      isPrimary:
                          step.actions[i].style == GuideActionStyle.primary,
                      primaryBg: step.buttonPrimaryBackgroundColor,
                      primaryText: step.buttonPrimaryTextColor,
                      ghostText: step.buttonGhostTextColor,
                      onTap: () => onAction(step.actions[i]),
                    ),
                  ],
                ],
              ),
            ),
        ],
      ),
    );
  }
}
