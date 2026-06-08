import 'json_util.dart';

/// The action invoked when a story item's CTA button is tapped.
///
/// Dart port of the Android `StoryCtaAction` data class. The [type] selects the
/// behaviour (`deepLink` / `openUrl` open [url]; anything else just dismisses).
class StoryCtaAction {
  final String type;
  final String? url;

  const StoryCtaAction({required this.type, this.url});

  static StoryCtaAction fromJson(Map<String, dynamic> json) {
    final url = optString(json, 'url');
    return StoryCtaAction(
      type: optString(json, 'type', 'dismiss'),
      url: url.isEmpty ? null : url,
    );
  }
}

/// A single item (image or video) inside an inline story.
///
/// Dart port of the Android `StoryItemConfig` data class — field names,
/// defaults, and parsing semantics match the Android SDK.
class StoryItemConfig {
  final String type;
  final String url;

  /// Per-item display duration in milliseconds. `null` falls back to the
  /// story's `defaultDuration` (images) or the natural video length (videos).
  final int? duration;
  final bool ctaEnabled;
  final String? ctaText;
  final String ctaTextColor;
  final String ctaBackgroundColor;
  final int ctaCornerRadius;
  final StoryCtaAction? ctaAction;

  const StoryItemConfig({
    required this.type,
    required this.url,
    this.duration,
    this.ctaEnabled = false,
    this.ctaText,
    this.ctaTextColor = '#FFFFFF',
    this.ctaBackgroundColor = '#4945FF',
    this.ctaCornerRadius = 8,
    this.ctaAction,
  });

  bool get isVideo => type == 'video';

  /// Parses one story item. Returns `null` when it has no media [url] —
  /// mirroring Android, where such items are dropped.
  static StoryItemConfig? fromJson(Map<String, dynamic> json) {
    final url = optString(json, 'url');
    if (url.isEmpty) return null;

    final duration = optInt(json, 'duration', 0);
    final ctaText = optString(json, 'ctaText');
    final ctaAction = optMap(json, 'ctaAction');

    return StoryItemConfig(
      type: optString(json, 'type', 'image'),
      url: url,
      duration: duration > 0 ? duration : null,
      ctaEnabled: optBool(json, 'ctaEnabled', false),
      ctaText: ctaText.isEmpty ? null : ctaText,
      ctaTextColor: _orDefault(optString(json, 'ctaTextColor', '#FFFFFF'), '#FFFFFF'),
      ctaBackgroundColor:
          _orDefault(optString(json, 'ctaBackgroundColor', '#4945FF'), '#4945FF'),
      ctaCornerRadius: optInt(json, 'ctaCornerRadius', 8),
      ctaAction: ctaAction == null ? null : StoryCtaAction.fromJson(ctaAction),
    );
  }
}

/// Styling for the story thumbnail cards rendered inside the [DigiaSlot] row.
///
/// Dart port of the Android `StoryCardConfig` data class.
class StoryCardConfig {
  final int height;
  final double aspectRatio;
  final double borderRadius;
  final int spacing;

  const StoryCardConfig({
    this.height = 220,
    this.aspectRatio = 0.6,
    this.borderRadius = 12,
    this.spacing = 8,
  });

  /// Card width derived from [height] and [aspectRatio] (matches Android).
  double get width => height * aspectRatio;

  static StoryCardConfig fromJson(Map<String, dynamic>? json) {
    if (json == null) return const StoryCardConfig();
    final height = optInt(json, 'height', 220);
    final aspectRatio = optDouble(json, 'aspectRatio', 0.6);
    return StoryCardConfig(
      height: height > 0 ? height : 220,
      aspectRatio: aspectRatio > 0 ? aspectRatio : 0.6,
      borderRadius: optDouble(json, 'borderRadius', 12),
      spacing: optInt(json, 'spacing', 8),
    );
  }
}

/// Styling for the full-screen progress indicator bars.
///
/// Dart port of the Android `StoryIndicatorDisplayConfig` data class. Colours
/// are kept as hex strings exactly as delivered and parsed at render time.
class StoryIndicatorDisplayConfig {
  final String activeColor;
  final String completedColor;
  final String disabledColor;
  final double height;
  final double borderRadius;
  final double horizontalGap;
  final double topPadding;
  final double horizontalPadding;

  const StoryIndicatorDisplayConfig({
    this.activeColor = '#FFFFFF',
    this.completedColor = '#AAAAAA',
    this.disabledColor = '#555555',
    this.height = 3.5,
    this.borderRadius = 4,
    this.horizontalGap = 4,
    this.topPadding = 14,
    this.horizontalPadding = 10,
  });

  static StoryIndicatorDisplayConfig fromJson(Map<String, dynamic>? json) {
    if (json == null) return const StoryIndicatorDisplayConfig();
    return StoryIndicatorDisplayConfig(
      activeColor: _orDefault(optString(json, 'activeColor', '#FFFFFF'), '#FFFFFF'),
      completedColor:
          _orDefault(optString(json, 'completedColor', '#AAAAAA'), '#AAAAAA'),
      disabledColor:
          _orDefault(optString(json, 'disabledColor', '#555555'), '#555555'),
      height: optDouble(json, 'height', 3.5),
      borderRadius: optDouble(json, 'borderRadius', 4),
      horizontalGap: optDouble(json, 'horizontalGap', 4),
      topPadding: optDouble(json, 'topPadding', 14),
      horizontalPadding: optDouble(json, 'horizontalPadding', 10),
    );
  }
}

/// Configuration for an inline story campaign, surfaced through a [DigiaSlot] at
/// the placement identified by [slotKey].
///
/// Dart port of the Android `InlineStoryConfig` data class — field names,
/// defaults, and parsing semantics match the Android SDK so both platforms
/// consume the same backend `templateConfig` payload.
class InlineStoryConfig {
  final String slotKey;

  /// Default per-item duration in milliseconds (images and videos that report
  /// no length).
  final int defaultDuration;
  final bool restartOnCompleted;
  final StoryCardConfig card;
  final StoryIndicatorDisplayConfig indicator;
  final List<StoryItemConfig> items;

  const InlineStoryConfig({
    required this.slotKey,
    this.defaultDuration = 5000,
    this.restartOnCompleted = false,
    this.card = const StoryCardConfig(),
    this.indicator = const StoryIndicatorDisplayConfig(),
    required this.items,
  });

  /// Parses a story `templateConfig` object. Returns `null` when the config is
  /// missing a [slotKey] or has no valid items — mirroring Android, where such
  /// campaigns are dropped rather than rendered empty.
  static InlineStoryConfig? fromJson(Map<String, dynamic> json) {
    final slotKey = optString(json, 'slotKey');
    if (slotKey.isEmpty) return null;

    final itemsArr = optList(json, 'items');
    if (itemsArr == null) return null;

    final items = <StoryItemConfig>[];
    for (final raw in itemsArr) {
      if (raw is! Map) continue;
      final item = StoryItemConfig.fromJson(raw.cast<String, dynamic>());
      if (item != null) items.add(item);
    }
    if (items.isEmpty) return null;

    final defaultDuration = optInt(json, 'defaultDuration', 5000);
    return InlineStoryConfig(
      slotKey: slotKey,
      defaultDuration: defaultDuration > 0 ? defaultDuration : 5000,
      restartOnCompleted: optBool(json, 'restartOnCompleted', false),
      card: StoryCardConfig.fromJson(optMap(json, 'card')),
      indicator: StoryIndicatorDisplayConfig.fromJson(optMap(json, 'indicator')),
      items: items,
    );
  }
}

/// Returns [value] unless it is blank, in which case [fallback] is used.
String _orDefault(String value, String fallback) =>
    value.trim().isEmpty ? fallback : value;
