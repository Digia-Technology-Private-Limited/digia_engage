import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../engage_fonts.dart';
import '../variable_scope.dart';
import 'guide_action_button.dart';
import 'guide_config_model.dart';

/// The guide bubble used as the (patched) showcaseview `container` for both
/// tooltip and spotlight steps.
///
/// A faithful port of React Native's tooltip/callout body in `DigiaProvider.tsx`:
/// title, optional body and a trailing action row, styled from the flat step
/// config (background, border, shadow, radius, colors, buttons). It draws **no**
/// arrow — the patched `Showcase.withWidget` draws a placement-aware arrow at
/// the resolved side, so the bubble stays exact while the arrow stays correct.
class GuideBubble extends StatelessWidget {
  final String title;
  final String body;
  final double maxWidth;
  final double padding;
  final Color backgroundColor;
  final Color borderColor;
  final double borderWidth;
  final double cornerRadius;
  final bool shadow;
  final Color titleColor;
  final double titleSize;
  final FontWeight titleWeight;
  final Color bodyColor;
  final double bodySize;
  final Color buttonPrimaryBackgroundColor;
  final Color buttonPrimaryTextColor;
  final Color buttonGhostTextColor;
  final List<GuideAction> actions;
  final VariableScope scope;
  final ValueChanged<GuideAction> onAction;

  const GuideBubble({
    required this.title,
    required this.body,
    required this.maxWidth,
    required this.padding,
    required this.backgroundColor,
    required this.borderColor,
    required this.borderWidth,
    required this.cornerRadius,
    required this.shadow,
    required this.titleColor,
    required this.titleSize,
    required this.titleWeight,
    required this.bodyColor,
    required this.bodySize,
    required this.buttonPrimaryBackgroundColor,
    required this.buttonPrimaryTextColor,
    required this.buttonGhostTextColor,
    required this.actions,
    required this.scope,
    required this.onAction,
    super.key,
  });

  /// Builds a bubble for a [TooltipStep].
  factory GuideBubble.tooltip({
    required TooltipStep step,
    required VariableScope scope,
    required ValueChanged<GuideAction> onAction,
  }) =>
      GuideBubble(
        title: step.title,
        body: step.body,
        maxWidth: step.maxWidth,
        padding: step.padding,
        backgroundColor: step.backgroundColor,
        borderColor: step.borderColor,
        borderWidth: step.borderWidth,
        cornerRadius: step.cornerRadius,
        shadow: step.shadow,
        titleColor: step.titleColor,
        titleSize: step.titleSize,
        titleWeight: step.titleWeight,
        bodyColor: step.bodyColor,
        bodySize: step.bodySize,
        buttonPrimaryBackgroundColor: step.buttonPrimaryBackgroundColor,
        buttonPrimaryTextColor: step.buttonPrimaryTextColor,
        buttonGhostTextColor: step.buttonGhostTextColor,
        actions: step.actions,
        scope: scope,
        onAction: onAction,
      );

  /// Builds a bubble for a [SpotlightStep] (its callout).
  factory GuideBubble.spotlight({
    required SpotlightStep step,
    required VariableScope scope,
    required ValueChanged<GuideAction> onAction,
  }) =>
      GuideBubble(
        title: step.title,
        body: step.body,
        maxWidth: step.calloutMaxWidth,
        padding: step.calloutPadding,
        backgroundColor: step.calloutBackgroundColor,
        borderColor: step.calloutBorderColor,
        borderWidth: step.calloutBorderWidth,
        cornerRadius: step.calloutCornerRadius,
        shadow: step.calloutShadow,
        titleColor: step.titleColor,
        titleSize: step.titleSize,
        titleWeight: step.titleWeight,
        bodyColor: step.bodyColor,
        bodySize: step.bodySize,
        buttonPrimaryBackgroundColor: step.buttonPrimaryBackgroundColor,
        buttonPrimaryTextColor: step.buttonPrimaryTextColor,
        buttonGhostTextColor: step.buttonGhostTextColor,
        actions: step.actions,
        scope: scope,
        onAction: onAction,
      );

  @override
  Widget build(BuildContext context) {
    final width = math.min(maxWidth, MediaQuery.of(context).size.width - 32);
    final fontFamily = EngageFonts.fontFamily;

    return Container(
      width: width,
      padding: EdgeInsets.all(padding),
      // Clip to the rounded box: when showcaseview must place the bubble in tight
      // space it shrinks the box, and a custom `withWidget` container can't
      // shrink on its own — clipping keeps any overflow inside the bubble
      // instead of spilling off‑screen ("content goes outside").
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(cornerRadius),
        border: borderWidth > 0
            ? Border.all(color: borderColor, width: borderWidth)
            : null,
        // RN: shadowOpacity 0.15, radius 12, offset (0,4).
        boxShadow: shadow
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
          if (title.isNotEmpty)
            Text(
              scope.resolve(title),
              style: TextStyle(
                color: titleColor,
                fontSize: titleSize,
                fontWeight: titleWeight,
                fontFamily: fontFamily,
              ),
            ),
          if (body.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                scope.resolve(body),
                style: TextStyle(
                  color: bodyColor,
                  fontSize: bodySize,
                  fontFamily: fontFamily,
                ),
              ),
            ),
          if (actions.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  for (var i = 0; i < actions.length; i++) ...[
                    if (i > 0) const SizedBox(width: 8),
                    GuideActionButton(
                      label: scope.resolve(actions[i].label),
                      isPrimary: actions[i].style == GuideActionStyle.primary,
                      primaryBg: buttonPrimaryBackgroundColor,
                      primaryText: buttonPrimaryTextColor,
                      ghostText: buttonGhostTextColor,
                      onTap: () => onAction(actions[i]),
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
