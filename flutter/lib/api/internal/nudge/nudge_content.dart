import 'package:flutter/material.dart'
    show
        BoxFit,
        Color,
        CrossAxisAlignment,
        EdgeInsets,
        FontWeight,
        MainAxisAlignment,
        TextAlign;

import '../action/engage_action.dart';

/// Pure, render-ready model of a nudge's content tree.
///
/// These are plain immutable value objects — they hold no parsing or rendering
/// logic (SRP). Decoding from the wire JSON lives in `nudge_parser.dart`;
/// drawing them lives in `nudge_view.dart`. Keeping the model free of both means
/// either side can change without touching this contract.

// ─── shared box (common) props ───────────────────────────────────────────────

/// Per-item self alignment on the column's cross axis. `null` = inherit the
/// column's own alignment.
enum NudgeSelfAlign { start, center, end }

/// Decoration + sizing shared by every node (the dashboard's `commonProps`).
class NudgeBox {
  final bool fillWidth;
  final double? fixedWidth;
  final double? fixedHeight;
  final Color? background;
  final EdgeInsets padding;
  final EdgeInsets margin;
  final double borderRadius;
  final Color? borderColor;
  final double borderWidth;
  final NudgeSelfAlign? selfAlign;

  const NudgeBox({
    this.fillWidth = false,
    this.fixedWidth,
    this.fixedHeight,
    this.background,
    this.padding = EdgeInsets.zero,
    this.margin = EdgeInsets.zero,
    this.borderRadius = 0,
    this.borderColor,
    this.borderWidth = 0,
    this.selfAlign,
  });

  static const none = NudgeBox();
}

// ─── nodes (sealed → exhaustive, type-safe rendering) ────────────────────────

sealed class NudgeNode {
  final NudgeBox box;
  const NudgeNode(this.box);
}

class NudgeText extends NudgeNode {
  final String text;
  final double fontSize;
  final FontWeight weight;
  final Color color;
  final TextAlign align;

  const NudgeText(
    super.box, {
    required this.text,
    required this.fontSize,
    required this.weight,
    required this.color,
    required this.align,
  });
}

class NudgeImage extends NudgeNode {
  final String url;
  final BoxFit fit;

  /// Width-to-height ratio; `0` = off (use the box's fixed height / natural size).
  final double aspectRatio;

  const NudgeImage(super.box, {required this.url, required this.fit, required this.aspectRatio});
}

class NudgeButton extends NudgeNode {
  final String label;
  final Color background;
  final Color textColor;
  final double radius;

  /// Ordered actions to run on tap (may be empty). Run by EngageActionRunner.
  final List<EngageAction> actions;

  const NudgeButton(
    super.box, {
    required this.label,
    required this.background,
    required this.textColor,
    required this.radius,
    required this.actions,
  });
}

class NudgeGap extends NudgeNode {
  final double height;
  const NudgeGap(super.box, {required this.height});
}

class NudgeDivider extends NudgeNode {
  final double thickness;
  final double indent;
  final double endIndent;
  final Color color;

  const NudgeDivider(
    super.box, {
    required this.thickness,
    required this.indent,
    required this.endIndent,
    required this.color,
  });
}

class NudgeLottie extends NudgeNode {
  final String url;
  final double height;
  final bool loop;
  final bool autoplay;

  const NudgeLottie(
    super.box, {
    required this.url,
    required this.height,
    required this.loop,
    required this.autoplay,
  });
}

class NudgeCarousel extends NudgeNode {
  final List<String> images;
  final double height;
  final bool autoPlay;
  final int autoPlayInterval;
  final bool loop;
  final bool showIndicator;

  const NudgeCarousel(
    super.box, {
    required this.images,
    required this.height,
    required this.autoPlay,
    required this.autoPlayInterval,
    required this.loop,
    required this.showIndicator,
  });
}

class NudgeVideo extends NudgeNode {
  final String url;
  final double height;
  final bool autoplay;
  final bool loop;
  final bool showControls;
  final bool muted;

  const NudgeVideo(
    super.box, {
    required this.url,
    required this.height,
    required this.autoplay,
    required this.loop,
    required this.showControls,
    required this.muted,
  });
}

// ─── column (root) ───────────────────────────────────────────────────────────

class NudgeColumn {
  final CrossAxisAlignment crossAxisAlignment;
  final MainAxisAlignment mainAxisAlignment;
  final double spacing;
  final List<NudgeNode> children;

  const NudgeColumn({
    required this.crossAxisAlignment,
    required this.mainAxisAlignment,
    required this.spacing,
    required this.children,
  });
}
