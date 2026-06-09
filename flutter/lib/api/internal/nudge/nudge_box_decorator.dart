import 'package:flutter/material.dart';

import 'nudge_content.dart';

/// Decorator (GoF): wraps any rendered node with its shared [NudgeBox] — explicit
/// size, background, border, corner radius, inner padding, outer margin, and
/// per-item self-alignment.
///
/// Keeping this out of the per-node renderers means the "how a box is framed"
/// rule lives in exactly one place (SRP). Every renderer returns only its own
/// content; the decorator adds the common envelope uniformly. A node with the
/// default [NudgeBox] (e.g. a gap) passes through untouched.
class NudgeBoxDecorator {
  const NudgeBoxDecorator();

  Widget decorate(NudgeBox box, Widget child) {
    final radius = box.borderRadius > 0 ? BorderRadius.circular(box.borderRadius) : null;
    final hasDecoration = box.background != null || box.borderWidth > 0 || radius != null;
    final hasPadding = box.padding != EdgeInsets.zero;
    final hasFrame = box.fillWidth ||
        box.fixedWidth != null ||
        box.fixedHeight != null ||
        hasPadding ||
        hasDecoration;

    Widget result = child;

    if (hasFrame) {
      result = Container(
        width: box.fillWidth ? double.infinity : box.fixedWidth,
        height: box.fixedHeight,
        padding: hasPadding ? box.padding : null,
        clipBehavior: radius != null ? Clip.antiAlias : Clip.none,
        decoration: hasDecoration
            ? BoxDecoration(
                color: box.background,
                borderRadius: radius,
                border: box.borderWidth > 0
                    ? Border.all(
                        color: box.borderColor ?? const Color(0xFF000000),
                        width: box.borderWidth,
                      )
                    : null,
              )
            : null,
        child: result,
      );
    }

    if (box.margin != EdgeInsets.zero) {
      result = Padding(padding: box.margin, child: result);
    }

    final selfAlign = box.selfAlign;
    if (selfAlign != null) {
      result = Align(alignment: _alignmentOf(selfAlign), child: result);
    }

    return result;
  }

  Alignment _alignmentOf(NudgeSelfAlign align) => switch (align) {
        NudgeSelfAlign.start => Alignment.centerLeft,
        NudgeSelfAlign.center => Alignment.center,
        NudgeSelfAlign.end => Alignment.centerRight,
      };
}
