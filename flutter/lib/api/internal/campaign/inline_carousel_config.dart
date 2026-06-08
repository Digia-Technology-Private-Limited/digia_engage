import 'json_util.dart';

/// A single image slide in an inline carousel campaign.
///
/// Dart port of the Android `CarouselItem` data class.
class CarouselItem {
  final String imageUrl;
  final String? deepLink;

  const CarouselItem({required this.imageUrl, this.deepLink});
}

/// Page-indicator styling for an inline carousel.
///
/// Dart port of the Android `CarouselIndicatorConfig` data class. Colours are
/// kept as hex strings exactly as delivered by the backend and parsed at
/// render time.
class CarouselIndicatorConfig {
  final bool showIndicator;
  final double dotHeight;
  final double dotWidth;
  final double spacing;
  final String dotColor;
  final String activeDotColor;
  final String indicatorEffectType;

  const CarouselIndicatorConfig({
    this.showIndicator = true,
    this.dotHeight = 8,
    this.dotWidth = 8,
    this.spacing = 12,
    this.dotColor = '#CBD5E1',
    this.activeDotColor = '#4945FF',
    this.indicatorEffectType = 'slide',
  });

  static CarouselIndicatorConfig fromJson(Map<String, dynamic>? json) {
    if (json == null) return const CarouselIndicatorConfig();
    return CarouselIndicatorConfig(
      showIndicator: optBool(json, 'showIndicator', true),
      dotHeight: optDouble(json, 'dotHeight', 8),
      dotWidth: optDouble(json, 'dotWidth', 8),
      spacing: optDouble(json, 'spacing', 12),
      dotColor: optString(json, 'dotColor', '#CBD5E1'),
      activeDotColor: optString(json, 'activeDotColor', '#4945FF'),
      indicatorEffectType: optString(json, 'indicatorEffectType', 'slide'),
    );
  }
}

/// Configuration for an inline carousel campaign, surfaced through [DigiaSlot]
/// at the placement identified by [slotKey].
///
/// Dart port of the Android `InlineCarouselConfig` data class — field names,
/// defaults, and parsing semantics match the Android SDK so both platforms
/// consume the same backend `templateConfig` payload.
class InlineCarouselConfig {
  final String slotKey;
  final List<CarouselItem> items;
  final int height;
  final int? width;
  final bool autoPlay;

  /// Auto-play interval in milliseconds.
  final int autoPlayInterval;

  /// Slide transition duration in milliseconds.
  final int animationDuration;
  final bool infiniteScroll;
  final double viewportFraction;
  final CarouselIndicatorConfig indicator;

  const InlineCarouselConfig({
    required this.slotKey,
    required this.items,
    this.height = 180,
    this.width,
    this.autoPlay = true,
    this.autoPlayInterval = 3000,
    this.animationDuration = 700,
    this.infiniteScroll = true,
    this.viewportFraction = 0.88,
    this.indicator = const CarouselIndicatorConfig(),
  });

  /// Parses a carousel `templateConfig` object. Returns `null` when the config
  /// is missing a [slotKey] or has no valid items — mirroring Android, where
  /// such campaigns are dropped rather than rendered empty.
  static InlineCarouselConfig? fromJson(Map<String, dynamic> json) {
    final slotKey = optString(json, 'slotKey');
    if (slotKey.isEmpty) return null;

    final itemsArr = optList(json, 'items');
    if (itemsArr == null) return null;

    final items = <CarouselItem>[];
    for (final raw in itemsArr) {
      if (raw is! Map) continue;
      final itemJson = raw.cast<String, dynamic>();
      final imageUrl = optString(itemJson, 'imageUrl');
      if (imageUrl.isEmpty) continue;
      final deepLink = optString(itemJson, 'deepLink');
      items.add(CarouselItem(
        imageUrl: imageUrl,
        deepLink: deepLink.isEmpty ? null : deepLink,
      ));
    }
    if (items.isEmpty) return null;

    final widthValue = json['width'];
    final width = widthValue is num && widthValue.toInt() > 0
        ? widthValue.toInt()
        : null;

    return InlineCarouselConfig(
      slotKey: slotKey,
      items: items,
      height: optInt(json, 'height', 180),
      width: width,
      autoPlay: optBool(json, 'autoPlay', true),
      autoPlayInterval: optInt(json, 'autoPlayInterval', 3000),
      animationDuration: optInt(json, 'animationDuration', 700),
      infiniteScroll: optBool(json, 'infiniteScroll', true),
      viewportFraction: optDouble(json, 'viewportFraction', 0.88),
      indicator: CarouselIndicatorConfig.fromJson(optMap(json, 'indicator')),
    );
  }
}
