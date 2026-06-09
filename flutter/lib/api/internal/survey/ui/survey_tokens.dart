import 'package:flutter/material.dart';

import '../../engage_fonts.dart';
import '../survey_config.dart';

/// Visual tokens + shared text-style helpers for the survey UI. Mirrors the
/// dashboard tokens and the Android `SurveyTokens` / `toTextStyle` helpers.
abstract final class SurveyTokens {
  static const border = Color(0xFFE4E6EB);
  static const borderStrong = Color(0xFFCDD2DA);
  static const surface = Color(0xFFFFFFFF);
  static const surfaceSunken = Color(0xFFF4F5F8);
  static const textPrimary = Color(0xFF1A1D24);
  static const textSecondary = Color(0xFF55606E);
  static const textTertiary = Color(0xFF8A93A1);
  static const danger = Color(0xFFD92D20);
}

/// Default size/weight/colour for a text role when the block carries no style.
class TextDefaults {
  final double size;
  final FontWeight weight;
  final Color color;

  const TextDefaults({required this.size, required this.weight, required this.color});
}

const titleDefaults =
    TextDefaults(size: 20, weight: FontWeight.bold, color: SurveyTokens.textPrimary);
const bodyDefaults =
    TextDefaults(size: 14, weight: FontWeight.w400, color: SurveyTokens.textSecondary);
const optionDefaults =
    TextDefaults(size: 16, weight: FontWeight.w500, color: SurveyTokens.textPrimary);

/// Parses `#RGB` / `#RRGGBB` / `#AARRGGBB`; empty/invalid → null.
Color? surveyColorOrNull(String hex) {
  var value = hex.trim();
  if (value.isEmpty) return null;
  if (value.startsWith('#')) value = value.substring(1);
  if (value.length == 3) value = value.split('').map((c) => '$c$c').join();
  if (value.length == 6) value = 'FF$value';
  if (value.length != 8) return null;
  final intValue = int.tryParse(value, radix: 16);
  return intValue == null ? null : Color(intValue);
}

FontWeight surveyFontWeight(SurveyFontWeight weight) => switch (weight) {
      SurveyFontWeight.regular => FontWeight.w400,
      SurveyFontWeight.medium => FontWeight.w500,
      SurveyFontWeight.semibold => FontWeight.w600,
      SurveyFontWeight.bold => FontWeight.w700,
    };

TextAlign surveyFlutterAlign(SurveyTextAlign align) => switch (align) {
      SurveyTextAlign.left => TextAlign.left,
      SurveyTextAlign.center => TextAlign.center,
      SurveyTextAlign.right => TextAlign.right,
    };

extension ElementStyleX on ElementStyle {
  /// Resolves this element style to a Flutter [TextStyle] against [def].
  /// Note: text alignment lives on the [Text] widget — see [flutterAlign].
  TextStyle toTextStyle(TextDefaults def) {
    final parsed = surveyColorOrNull(colorHex);
    return TextStyle(
      fontSize: sizePx > 0 ? sizePx : def.size,
      fontWeight: surveyFontWeight(weight),
      color: parsed ?? def.color,
      fontFamily: EngageFonts.fontFamily,
    );
  }

  TextAlign get flutterAlign => surveyFlutterAlign(align);
}

extension TextDefaultsX on TextDefaults {
  TextStyle toStyle() => TextStyle(
        fontSize: size,
        fontWeight: weight,
        color: color,
        fontFamily: EngageFonts.fontFamily,
      );
}
