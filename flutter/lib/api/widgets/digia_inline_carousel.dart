import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../src/framework/internal_widgets/internal_carousel.dart';
import '../internal/campaign/inline_carousel_config.dart';

/// Renders an [InlineCarouselConfig] as a native image carousel.
///
/// Mirrors the Android inline-carousel rendering: a swipeable set of network
/// images with optional auto-play and a page indicator, driven entirely by the
/// server-delivered config. Reuses the SDK's [InternalCarousel] primitive.
class DigiaInlineCarousel extends StatelessWidget {
  final InlineCarouselConfig config;

  /// Invoked when a slide is tapped, carrying the slide's `deepLink` (if any).
  final void Function(CarouselItem item)? onItemTap;

  const DigiaInlineCarousel({
    super.key,
    required this.config,
    this.onItemTap,
  });

  @override
  Widget build(BuildContext context) {
    final indicator = config.indicator;

    final slides = config.items.map((item) {
      final image = CachedNetworkImage(
        imageUrl: item.imageUrl,
        fit: BoxFit.cover,
        width: double.infinity,
        errorWidget: (_, __, ___) => const SizedBox.shrink(),
      );

      if (onItemTap == null) return image;
      return GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => onItemTap!.call(item),
        child: image,
      );
    }).toList();

    return InternalCarousel(
      height: config.height.toDouble(),
      width: config.width?.toDouble(),
      autoPlay: config.autoPlay,
      autoPlayInterval: config.autoPlayInterval,
      animationDuration: config.animationDuration,
      infiniteScroll: config.infiniteScroll,
      viewportFraction: config.viewportFraction,
      showIndicator: indicator.showIndicator,
      dotHeight: indicator.dotHeight,
      dotWidth: indicator.dotWidth,
      spacing: indicator.spacing,
      dotColor: _parseHexColor(indicator.dotColor) ?? const Color(0xFFCBD5E1),
      activeDotColor:
          _parseHexColor(indicator.activeDotColor) ?? const Color(0xFF4945FF),
      indicatorEffectType: indicator.indicatorEffectType,
      children: slides,
    );
  }

  /// Parses `#RRGGBB` or `#AARRGGBB` hex strings (Android `Color.parseColor`
  /// format). Returns `null` for unparseable input so the caller can fall back.
  static Color? _parseHexColor(String hex) {
    var value = hex.trim();
    if (value.startsWith('#')) value = value.substring(1);
    if (value.length == 6) value = 'FF$value';
    if (value.length != 8) return null;
    final intValue = int.tryParse(value, radix: 16);
    if (intValue == null) return null;
    return Color(intValue);
  }
}
