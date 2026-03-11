import 'package:flutter/widgets.dart';
import 'package:google_fonts/google_fonts.dart';

import 'font_factory.dart';

/// A [DUIFontFactory] implementation backed by the `google_fonts` package.
///
/// When a user of the SDK does not supply their own [DUIFontFactory], this
/// implementation is used as a fallback so that `fontFamily` names from the
/// server-driven JSON are resolved against the Google Fonts catalogue.
///
/// Usage (in DigiaUIApp):
/// ```dart
/// DigiaUIApp(
///   digiaUI: digiaUI,
///   fontFactory: GoogleFontFactory(),   // ← supply explicitly, or rely on library default
///   builder: (ctx) => ...,
/// )
/// ```
class GoogleFontFactory implements DUIFontFactory {
  const GoogleFontFactory();

  @override
  TextStyle getFont(
    String fontFamily, {
    TextStyle? textStyle,
    Color? color,
    Color? backgroundColor,
    double? fontSize,
    FontWeight? fontWeight,
    FontStyle? fontStyle,
    double? height,
    TextDecoration? decoration,
    Color? decorationColor,
    TextDecorationStyle? decorationStyle,
    double? decorationThickness,
  }) {
    try {
      return GoogleFonts.getFont(
        fontFamily,
        textStyle: textStyle,
        color: color,
        backgroundColor: backgroundColor,
        fontSize: fontSize,
        fontWeight: fontWeight,
        fontStyle: fontStyle,
        height: height,
        decoration: decoration,
        decorationColor: decorationColor,
        decorationStyle: decorationStyle,
        decorationThickness: decorationThickness,
      );
    } catch (_) {
      // Fall back to the system font when the name is not found in Google Fonts.
      return (textStyle ?? const TextStyle()).copyWith(
        fontFamily: fontFamily,
        color: color,
        backgroundColor: backgroundColor,
        fontSize: fontSize,
        fontWeight: fontWeight,
        fontStyle: fontStyle,
        height: height,
        decoration: decoration,
        decorationColor: decorationColor,
        decorationStyle: decorationStyle,
        decorationThickness: decorationThickness,
      );
    }
  }

  @override
  TextStyle getDefaultFont() => GoogleFonts.roboto();
}
