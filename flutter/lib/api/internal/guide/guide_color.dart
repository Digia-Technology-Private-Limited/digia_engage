import 'dart:ui' show Color;

/// Parses a hex colour string into a [Color], mirroring Android's
/// `Color.parseColor` semantics used by the native guide models so the same
/// backend JSON yields identical colours on every platform.
///
/// Accepts `#RGB`, `#RRGGBB`, and `#AARRGGBB` (alpha-first, like Android), with
/// or without the leading `#`. Returns [fallback] for null/blank/invalid input.
Color colorOr(String? hex, Color fallback) {
  if (hex == null) return fallback;
  var value = hex.trim();
  if (value.isEmpty) return fallback;
  if (value.startsWith('#')) value = value.substring(1);
  if (value.length == 3) value = value.split('').map((c) => '$c$c').join();
  if (value.length == 6) value = 'FF$value';
  if (value.length != 8) return fallback;
  final intValue = int.tryParse(value, radix: 16);
  return intValue == null ? fallback : Color(intValue);
}
