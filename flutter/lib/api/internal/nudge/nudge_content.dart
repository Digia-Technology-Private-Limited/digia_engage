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

  /// A copy with the fixed height cleared — used when another mechanism (e.g. an
  /// image's aspect ratio) owns the height and the box must not clamp it.
  NudgeBox withoutFixedHeight() => NudgeBox(
        fillWidth: fillWidth,
        fixedWidth: fixedWidth,
        fixedHeight: null,
        background: background,
        padding: padding,
        margin: margin,
        borderRadius: borderRadius,
        borderColor: borderColor,
        borderWidth: borderWidth,
        selfAlign: selfAlign,
      );
}

// ─── nodes (sealed → exhaustive, type-safe rendering) ────────────────────────

sealed class NudgeNode {
  final NudgeBox box;
  const NudgeNode(this.box);
}

/// One styled run of a Text widget's rich overlay. [text] is the literal run;
/// every style field is `null` when the run inherits the Text widget's base
/// style for that property (mirroring the dashboard's `span.style` contract).
class NudgeTextSpan {
  final String text;
  final FontWeight? weight;
  final double? fontSize;
  final Color? color;

  /// Highlight/background colour behind the run; `null` = none.
  final Color? highlightColor;

  /// Unitless line-height multiplier (Flutter `TextStyle.height`); `null` = inherit.
  final double? lineHeight;

  const NudgeTextSpan(
    this.text, {
    this.weight,
    this.fontSize,
    this.color,
    this.highlightColor,
    this.lineHeight,
  });
}

class NudgeText extends NudgeNode {
  final String text;
  final double fontSize;
  final FontWeight weight;
  final Color color;
  final TextAlign align;

  /// Optional rich overlay: styled runs drawn on top of the base style above.
  /// Empty = render the plain [text] with the base style, exactly as before.
  final List<NudgeTextSpan> spans;

  const NudgeText(
    super.box, {
    required this.text,
    required this.fontSize,
    required this.weight,
    required this.color,
    required this.align,
    this.spans = const [],
  });
}

class NudgeImage extends NudgeNode {
  final String url;
  final BoxFit fit;

  /// Width-to-height ratio; `0` = off (use the box's fixed height / natural size).
  final double aspectRatio;

  const NudgeImage(super.box,
      {required this.url, required this.fit, required this.aspectRatio});
}

/// Visual style of a button. `fill`/`elevated` use background + textColor;
/// `outline`/`text` use background as the accent (border + label).
enum NudgeButtonVariant { fill, outline, text, elevated }

class NudgeButton extends NudgeNode {
  final String label;
  final NudgeButtonVariant variant;
  final double fontSize;
  final FontWeight weight;
  final Color background;
  final Color textColor;
  final double radius;

  /// Ordered actions to run on tap (may be empty). Run by EngageActionRunner.
  final List<EngageAction> actions;

  /// Marks this as the experience's primary call-to-action, distinguishing the
  /// emitted click (`cta_primary` vs `cta_secondary`). A button tap emits an
  /// `ExperienceClicked` event only after its [actions] run successfully; a
  /// button with no [actions], or one whose action throws, emits nothing.
  final bool isPrimary;

  const NudgeButton(
    super.box, {
    required this.label,
    required this.variant,
    required this.fontSize,
    required this.weight,
    required this.background,
    required this.textColor,
    required this.radius,
    required this.actions,
    required this.isPrimary,
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
  final BoxFit fit;

  /// Width-to-height ratio; `0` = off (use the fixed height / natural size).
  final double aspectRatio;

  const NudgeLottie(
    super.box, {
    required this.url,
    required this.height,
    required this.loop,
    required this.autoplay,
    required this.fit,
    required this.aspectRatio,
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
