import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';

import '../../../src/framework/internal_widgets/internal_carousel.dart';
import '../../../src/framework/internal_widgets/internal_video_player.dart';
import '../action/engage_action_context.dart';
import '../action/engage_action_handler.dart';
import '../engage_fonts.dart';
import 'nudge_content.dart';

/// Strategy (GoF): renders a single kind of [NudgeNode] to a Flutter widget.
///
/// One concrete strategy per widget type, each with a single responsibility.
/// The strategy returns only the node's own content — the shared box envelope is
/// added separately by `NudgeBoxDecorator`.
///
/// `T` types each concrete strategy to its node; [renderDynamic] is the
/// type-erased entry the registry calls, with the (registry-guaranteed) cast
/// localised here.
abstract base class NudgeNodeRenderer<T extends NudgeNode> {
  const NudgeNodeRenderer();

  Type get nodeType => T;

  Widget render(T node, BuildContext context);

  Widget renderDynamic(NudgeNode node, BuildContext context) =>
      render(node as T, context);
}

/// Registry / dispatcher (Strategy selector). Open/closed: support a new widget
/// by adding a renderer to [_defaults] (or injecting one) — no existing code
/// changes. Unknown nodes degrade to an empty box rather than throwing.
class NudgeNodeRendererRegistry {
  final Map<Type, NudgeNodeRenderer> _byType;

  NudgeNodeRendererRegistry([List<NudgeNodeRenderer>? renderers])
      : _byType = {for (final r in renderers ?? _defaults) r.nodeType: r};

  static const List<NudgeNodeRenderer> _defaults = [
    NudgeTextRenderer(),
    NudgeImageRenderer(),
    NudgeButtonRenderer(),
    NudgeGapRenderer(),
    NudgeDividerRenderer(),
    NudgeLottieRenderer(),
    NudgeCarouselRenderer(),
    NudgeVideoRenderer(),
  ];

  Widget render(NudgeNode node, BuildContext context) {
    final renderer = _byType[node.runtimeType];
    return renderer?.renderDynamic(node, context) ?? const SizedBox.shrink();
  }
}

// ─── concrete strategies ──────────────────────────────────────────────────────

final class NudgeTextRenderer extends NudgeNodeRenderer<NudgeText> {
  const NudgeTextRenderer();

  @override
  Widget render(NudgeText node, BuildContext context) => Text(
        node.text,
        textAlign: node.align,
        style: TextStyle(
          fontSize: node.fontSize,
          fontWeight: node.weight,
          color: node.color,
          fontFamily: EngageFonts.fontFamily,
        ),
      );
}

final class NudgeImageRenderer extends NudgeNodeRenderer<NudgeImage> {
  const NudgeImageRenderer();

  @override
  Widget render(NudgeImage node, BuildContext context) {
    if (node.url.isEmpty) {
      return _placeholder(node);
    }
    // Aspect ratio (when set) drives the height; otherwise use the box's fixed
    // height, then natural size.
    if (node.aspectRatio > 0) {
      return AspectRatio(
        aspectRatio: node.aspectRatio,
        child: Image.network(
          node.url,
          fit: node.fit,
          width: double.infinity,
          height: double.infinity,
          errorBuilder: (_, __, ___) => _placeholder(node),
        ),
      );
    }
    return Image.network(
      node.url,
      fit: node.fit,
      width: node.box.fillWidth ? double.infinity : null,
      height: node.box.fixedHeight,
      errorBuilder: (_, __, ___) => _placeholder(node),
    );
  }

  Widget _placeholder(NudgeImage node) {
    if (node.aspectRatio > 0) {
      return AspectRatio(
        aspectRatio: node.aspectRatio,
        child: const NudgePlaceholder(label: 'Image', height: double.infinity),
      );
    }
    return NudgePlaceholder(label: 'No image URL', height: node.box.fixedHeight ?? 120);
  }
}

final class NudgeButtonRenderer extends NudgeNodeRenderer<NudgeButton> {
  const NudgeButtonRenderer();

  @override
  Widget render(NudgeButton node, BuildContext context) {
    final filled = node.variant == NudgeButtonVariant.fill ||
        node.variant == NudgeButtonVariant.elevated;
    // fill/elevated: solid bg + textColor label. outline/text: transparent bg,
    // background colour becomes the accent for the border + label.
    final background = filled ? node.background : const Color(0x00000000);
    final foreground = filled ? node.textColor : node.background;
    final elevation = node.variant == NudgeButtonVariant.elevated ? 3.0 : 0.0;

    return Material(
      color: background,
      elevation: elevation,
      shadowColor: const Color(0x55000000),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(node.radius),
        side: node.variant == NudgeButtonVariant.outline
            ? BorderSide(color: node.background, width: 1.5)
            : BorderSide.none,
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: node.actions.isEmpty
            ? null
            : () => EngageActionRunner.shared.run(
                  node.actions,
                  EngageActionScope.fromContext(context),
                  EngageActionContextScope.of(context) ?? EngageActionContext.unknown,
                ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Center(
            widthFactor: node.box.fillWidth ? null : 1,
            child: Text(
              node.label,
              style: TextStyle(
                color: foreground,
                fontSize: node.fontSize,
                fontWeight: node.weight,
                fontFamily: EngageFonts.fontFamily,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

final class NudgeGapRenderer extends NudgeNodeRenderer<NudgeGap> {
  const NudgeGapRenderer();

  @override
  Widget render(NudgeGap node, BuildContext context) => SizedBox(height: node.height);
}

final class NudgeDividerRenderer extends NudgeNodeRenderer<NudgeDivider> {
  const NudgeDividerRenderer();

  @override
  Widget render(NudgeDivider node, BuildContext context) => Padding(
        padding: EdgeInsets.only(left: node.indent, right: node.endIndent),
        child: Container(height: node.thickness, color: node.color),
      );
}

final class NudgeLottieRenderer extends NudgeNodeRenderer<NudgeLottie> {
  const NudgeLottieRenderer();

  @override
  Widget render(NudgeLottie node, BuildContext context) {
    if (node.url.isEmpty) {
      return NudgePlaceholder(label: 'No Lottie URL', height: node.height);
    }
    return Lottie.network(
      node.url,
      height: node.height,
      repeat: node.loop,
      animate: node.autoplay,
      fit: BoxFit.contain,
      errorBuilder: (_, __, ___) =>
          NudgePlaceholder(label: 'Lottie failed', height: node.height),
    );
  }
}

final class NudgeCarouselRenderer extends NudgeNodeRenderer<NudgeCarousel> {
  const NudgeCarouselRenderer();

  @override
  Widget render(NudgeCarousel node, BuildContext context) {
    if (node.images.isEmpty) {
      return NudgePlaceholder(label: 'No images', height: node.height);
    }
    final radius =
        node.box.borderRadius > 0 ? BorderRadius.circular(node.box.borderRadius) : BorderRadius.zero;

    final slides = node.images
        .map((url) => ClipRRect(
              borderRadius: radius,
              child: CachedNetworkImage(
                imageUrl: url,
                fit: BoxFit.cover,
                width: double.infinity,
                errorWidget: (_, __, ___) => const SizedBox.shrink(),
              ),
            ))
        .toList();

    // One full-width slide per view (viewportFraction 1.0) to match the
    // dashboard's snap carousel.
    return InternalCarousel(
      height: node.height,
      autoPlay: node.autoPlay,
      autoPlayInterval: node.autoPlayInterval,
      infiniteScroll: node.loop,
      viewportFraction: 1,
      showIndicator: node.showIndicator,
      dotColor: const Color(0xFFCBD5E1),
      activeDotColor: const Color(0xFF4945FF),
      children: slides,
    );
  }
}

final class NudgeVideoRenderer extends NudgeNodeRenderer<NudgeVideo> {
  const NudgeVideoRenderer();

  @override
  Widget render(NudgeVideo node, BuildContext context) {
    if (node.url.isEmpty) {
      return NudgePlaceholder(label: 'No video URL', height: node.height);
    }
    final radius =
        node.box.borderRadius > 0 ? BorderRadius.circular(node.box.borderRadius) : BorderRadius.zero;

    // A fixed-height, full-width black box (matching the dashboard preview). The
    // player keeps its own aspect ratio and is centered; any letterbox is black,
    // so it reads as one clean video box rather than white gaps around the frame.
    return ClipRRect(
      borderRadius: radius,
      child: Container(
        height: node.height,
        width: double.infinity,
        color: const Color(0xFF000000),
        alignment: Alignment.center,
        // `muted` is parsed but InternalVideoPlayer has no volume control yet;
        // chewie plays with its default volume.
        child: InternalVideoPlayer(
          videoUrl: node.url,
          autoPlay: node.autoplay,
          looping: node.loop,
          showControls: node.showControls,
        ),
      ),
    );
  }
}

/// Shared fallback box for missing/broken media, matching the dashboard preview.
class NudgePlaceholder extends StatelessWidget {
  final String label;
  final double height;

  const NudgePlaceholder({required this.label, required this.height, super.key});

  @override
  Widget build(BuildContext context) => Container(
        height: height,
        width: double.infinity,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: const Color(0xFFF1F1F5),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(label, style: const TextStyle(fontSize: 11, color: Color(0xFF9A9AAD))),
      );
}
