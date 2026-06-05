import 'package:flutter/material.dart'
    show BoxFit, Color, CrossAxisAlignment, FontWeight, MainAxisAlignment, TextAlign;

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
  final double padding;
  final double margin;
  final double borderRadius;
  final Color? borderColor;
  final double borderWidth;
  final NudgeSelfAlign? selfAlign;

  const NudgeBox({
    this.fillWidth = false,
    this.fixedWidth,
    this.fixedHeight,
    this.background,
    this.padding = 0,
    this.margin = 0,
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

  const NudgeImage(super.box, {required this.url, required this.fit});
}

class NudgeButton extends NudgeNode {
  final String label;
  final Color background;
  final Color textColor;
  final double radius;

  /// The raw `onClick` flow exactly as authored, kept untouched for now. Tap
  /// handling is intentionally NOT wired yet — see
  /// `docs/nudge-action-handler-flow.md` for the planned handler structure.
  final Map<String, dynamic>? onClick;

  const NudgeButton(
    super.box, {
    required this.label,
    required this.background,
    required this.textColor,
    required this.radius,
    required this.onClick,
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
