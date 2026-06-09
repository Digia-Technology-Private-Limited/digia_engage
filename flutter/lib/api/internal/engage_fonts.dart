/// Global font configuration for Digia-rendered text.
///
/// Dart port of Android's `DigiaFontConfig`. Set once from
/// [DigiaConfig.fontFamily] during init and read by the renderers (nudge text,
/// button labels) so the whole experience honours the host app's font.
class EngageFonts {
  EngageFonts._();

  /// The global font family applied to Digia-rendered text, or null to use the
  /// ambient Flutter default.
  static String? fontFamily;
}
