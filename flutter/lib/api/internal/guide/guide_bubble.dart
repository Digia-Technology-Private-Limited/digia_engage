import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../engage_fonts.dart';
import '../variable_scope.dart';
import 'guide_config_model.dart';

/// Pure presentation of one guide step's bubble — the card with optional media,
/// title, body, step indicator and action buttons, plus a directional arrow.
///
/// This holds no lifecycle logic: it reports button taps through [onActionTap]
/// and lets the renderer decide what advancing/dismissing means. Rendering the
/// bubble ourselves (rather than using onboarding_overlay's label box) is what
/// keeps the UI on par with the Android / iOS / React Native guide renderers.
class GuideBubble extends StatelessWidget {
  final GuideStepWidgetConfig config;
  final VariableScope scope;
  final int stepIndex; // 0-based
  final int stepTotal;
  final bool multiStep;
  final double maxWidth;
  final ValueChanged<GuideAction> onActionTap;

  const GuideBubble({
    required this.config,
    required this.scope,
    required this.stepIndex,
    required this.stepTotal,
    required this.multiStep,
    required this.maxWidth,
    required this.onActionTap,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final bubble = config.bubble;
    final arrow = bubble.arrow;
    final direction = _resolveDirection(arrow.preferredDirection);
    final isVertical = direction == _ArrowDir.top || direction == _ArrowDir.bottom;

    final card = ConstrainedBox(
      constraints: BoxConstraints(maxWidth: bubble.maxWidth),
      child: Container(
        decoration: BoxDecoration(
          color: bubble.backgroundColor,
          borderRadius: BorderRadius.circular(bubble.cornerRadius),
          boxShadow: bubble.elevation > 0
              ? [
                  BoxShadow(
                    color: const Color(0x33000000),
                    blurRadius: bubble.elevation * 2,
                    offset: Offset(0, bubble.elevation / 2),
                  ),
                ]
              : null,
        ),
        padding: EdgeInsets.symmetric(
          horizontal: bubble.paddingHorizontal,
          vertical: bubble.paddingVertical,
        ),
        child: _buildContent(),
      ),
    );

    if (!arrow.visible) return _wrap(card);

    final arrowWidget = _ArrowTriangle(
      direction: direction,
      size: arrow.size,
      color: arrow.color,
    );

    // The arrow sits on the side of the card that faces the anchor.
    final children = <Widget>[
      if (direction == _ArrowDir.top || direction == _ArrowDir.start)
        arrowWidget,
      Flexible(child: card),
      if (direction == _ArrowDir.bottom || direction == _ArrowDir.end)
        arrowWidget,
    ];

    return _wrap(
      isVertical
          ? Column(mainAxisSize: MainAxisSize.min, children: children)
          : Row(mainAxisSize: MainAxisSize.min, children: children),
    );
  }

  Widget _wrap(Widget child) =>
      Material(type: MaterialType.transparency, child: child);

  Widget _buildContent() {
    final content = config.content;
    final children = <Widget>[];

    if (content.mediaUrl != null && content.mediaUrl!.isNotEmpty) {
      children.add(
        ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: CachedNetworkImage(
            imageUrl: content.mediaUrl!,
            fit: BoxFit.cover,
            height: 120,
            width: double.infinity,
          ),
        ),
      );
      children.add(const SizedBox(height: 8));
    }

    if (content.title != null) {
      children.add(_text(content.title!));
    }
    if (content.body != null) {
      if (children.isNotEmpty) children.add(const SizedBox(height: 6));
      children.add(_text(content.body!));
    }

    if (multiStep && content.stepIndicator.visible) {
      children.add(const SizedBox(height: 8));
      children.add(
        Text(
          '${stepIndex + 1} / $stepTotal',
          style: TextStyle(
            color: content.stepIndicator.color,
            fontSize: 12,
            fontFamily: EngageFonts.fontFamily,
          ),
        ),
      );
    }

    if (config.actions.isNotEmpty) {
      children.add(const SizedBox(height: 12));
      children.add(_buildActions());
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: children,
    );
  }

  Widget _buildActions() {
    return Wrap(
      alignment: WrapAlignment.end,
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final action in config.actions) _actionButton(action),
      ],
    );
  }

  Widget _actionButton(GuideAction action) {
    final isGhost = action.style == 'ghost';
    final label = Text(
      scope.resolve(action.label),
      style: TextStyle(
        color: action.textColor,
        fontFamily: EngageFonts.fontFamily,
        fontWeight: FontWeight.w600,
      ),
    );
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(action.cornerRadius),
    );
    if (isGhost) {
      return TextButton(
        onPressed: () => onActionTap(action),
        style: TextButton.styleFrom(shape: shape),
        child: label,
      );
    }
    return ElevatedButton(
      onPressed: () => onActionTap(action),
      style: ElevatedButton.styleFrom(
        backgroundColor: action.backgroundColor,
        foregroundColor: action.textColor,
        elevation: 0,
        shape: shape,
      ),
      child: label,
    );
  }

  Widget _text(GuideTextContent t) => Text(
        scope.resolve(t.text),
        style: TextStyle(
          color: t.color,
          fontSize: t.fontSize,
          fontWeight: _weight(t.fontWeight),
          fontFamily: t.fontFamily.isNotEmpty ? t.fontFamily : EngageFonts.fontFamily,
        ),
      );

  static FontWeight _weight(String raw) {
    switch (raw.trim().toLowerCase()) {
      case 'bold':
      case '700':
        return FontWeight.w700;
      case '600':
      case 'semibold':
        return FontWeight.w600;
      case '500':
      case 'medium':
        return FontWeight.w500;
      default:
        return FontWeight.w400;
    }
  }

  static _ArrowDir _resolveDirection(String raw) {
    switch (raw.trim().toLowerCase()) {
      case 'top':
        return _ArrowDir.top;
      case 'start':
      case 'left':
        return _ArrowDir.start;
      case 'end':
      case 'right':
        return _ArrowDir.end;
      case 'bottom':
      case 'auto':
      default:
        // Android default: arrow at the bottom, bubble above the anchor.
        return _ArrowDir.bottom;
    }
  }
}

enum _ArrowDir { top, bottom, start, end }

/// A small filled triangle pointing toward the anchor.
class _ArrowTriangle extends StatelessWidget {
  final _ArrowDir direction;
  final double size;
  final Color color;

  const _ArrowTriangle({
    required this.direction,
    required this.size,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    final isVertical =
        direction == _ArrowDir.top || direction == _ArrowDir.bottom;
    return CustomPaint(
      size: isVertical ? Size(size * 2, size) : Size(size, size * 2),
      painter: _ArrowPainter(direction, color),
    );
  }
}

class _ArrowPainter extends CustomPainter {
  final _ArrowDir direction;
  final Color color;

  _ArrowPainter(this.direction, this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;
    final path = Path();
    switch (direction) {
      case _ArrowDir.top: // points up
        path.moveTo(size.width / 2, 0);
        path.lineTo(0, size.height);
        path.lineTo(size.width, size.height);
        break;
      case _ArrowDir.bottom: // points down
        path.moveTo(0, 0);
        path.lineTo(size.width, 0);
        path.lineTo(size.width / 2, size.height);
        break;
      case _ArrowDir.start: // points left
        path.moveTo(0, size.height / 2);
        path.lineTo(size.width, 0);
        path.lineTo(size.width, size.height);
        break;
      case _ArrowDir.end: // points right
        path.moveTo(0, 0);
        path.lineTo(0, size.height);
        path.lineTo(size.width, size.height / 2);
        break;
    }
    path.close();
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(_ArrowPainter old) =>
      old.direction != direction || old.color != color;
}
