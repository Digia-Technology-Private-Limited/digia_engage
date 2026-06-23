import 'dart:ui' show Color;

import 'package:flutter/painting.dart' show FontWeight;

import '../campaign/json_util.dart';
import 'guide_color.dart';

/// Typed config holders for a guide (tooltip / spotlight) campaign.
///
/// This is a faithful Dart port of the **React Native** guide config
/// (`react-native/src/templateTypes.ts`) — the *flat* per-step shape the
/// backend actually sends and that `DigiaProvider.tsx` renders. Matching this
/// shape (rather than the Android nested `bubble/overlay/content` shape) is what
/// makes the Flutter guide UI line up with React Native.
///
/// Parsed straight from `templateConfig` (`templateType`, `steps[]`,
/// `outsideTapBehavior`); no transformation, mirroring RN's `_parseTemplateConfig`.

// ── Action ──────────────────────────────────────────────────────────────────

enum GuideActionStyle { primary, secondary, ghost }

enum GuideActionType { dismiss, next, back, prev, deepLink, openUrl, fireEvent }

GuideActionStyle _styleFrom(String raw) {
  switch (raw.trim().toLowerCase()) {
    case 'secondary':
      return GuideActionStyle.secondary;
    case 'ghost':
      return GuideActionStyle.ghost;
    case 'primary':
    default:
      return GuideActionStyle.primary;
  }
}

GuideActionType _actionTypeFrom(String raw) {
  switch (raw.trim().toLowerCase()) {
    case 'next':
      return GuideActionType.next;
    case 'back':
      return GuideActionType.back;
    case 'prev':
      return GuideActionType.prev;
    case 'deep_link':
      return GuideActionType.deepLink;
    case 'open_url':
      return GuideActionType.openUrl;
    case 'fire_event':
      return GuideActionType.fireEvent;
    case 'dismiss':
    default:
      return GuideActionType.dismiss;
  }
}

/// A guide button, mirroring RN's `Action` discriminated union.
class GuideAction {
  final GuideActionType type;
  final String label;
  final GuideActionStyle style;
  final String? scope; // dismiss: 'self' | 'all'
  final String? url; // deep_link / open_url
  final String? fallbackUrl; // deep_link
  final String? presentation; // open_url: 'external' | 'in_app'
  final String? eventName; // fire_event
  final Map<String, dynamic>? properties; // fire_event

  const GuideAction({
    required this.type,
    required this.label,
    required this.style,
    this.scope,
    this.url,
    this.fallbackUrl,
    this.presentation,
    this.eventName,
    this.properties,
  });

  static GuideAction fromJson(Map<String, dynamic> json) => GuideAction(
        type: _actionTypeFrom(optString(json, 'type', 'dismiss')),
        label: optString(json, 'label'),
        style: _styleFrom(optString(json, 'style', 'primary')),
        scope: _optStringOrNull(json, 'scope'),
        url: _optStringOrNull(json, 'url'),
        fallbackUrl: _optStringOrNull(json, 'fallback_url'),
        presentation: _optStringOrNull(json, 'presentation'),
        eventName: _optStringOrNull(json, 'event_name'),
        properties: optMap(json, 'properties'),
      );
}

// ── Tooltip step ──────────────────────────────────────────────────────────────

class TooltipStep {
  final String anchorKey;
  final int? delayInMs;
  final String title;
  final String body;
  final String placement; // top|bottom|left|right|auto
  final Color backgroundColor;
  final Color borderColor;
  final double borderWidth;
  final double cornerRadius;
  final bool shadow;
  final double maxWidth;
  final double padding;
  final bool showArrow;
  final Color? arrowColor;
  final Color? arrowBorderColor;
  final double arrowSize;
  final Color titleColor;
  final double titleSize;
  final FontWeight titleWeight;
  final Color bodyColor;
  final double bodySize;
  final Color buttonPrimaryBackgroundColor;
  final Color buttonPrimaryTextColor;
  final Color buttonGhostTextColor;
  final List<GuideAction> actions;

  const TooltipStep({
    required this.anchorKey,
    required this.delayInMs,
    required this.title,
    required this.body,
    required this.placement,
    required this.backgroundColor,
    required this.borderColor,
    required this.borderWidth,
    required this.cornerRadius,
    required this.shadow,
    required this.maxWidth,
    required this.padding,
    required this.showArrow,
    required this.arrowColor,
    required this.arrowBorderColor,
    required this.arrowSize,
    required this.titleColor,
    required this.titleSize,
    required this.titleWeight,
    required this.bodyColor,
    required this.bodySize,
    required this.buttonPrimaryBackgroundColor,
    required this.buttonPrimaryTextColor,
    required this.buttonGhostTextColor,
    required this.actions,
  });

  static TooltipStep? fromJson(Map<String, dynamic> json) {
    final anchorKey = optString(json, 'anchorKey');
    if (anchorKey.isEmpty) return null;
    return TooltipStep(
      anchorKey: anchorKey,
      delayInMs: json.containsKey('delayInMs') ? optInt(json, 'delayInMs', 0) : null,
      title: optString(json, 'title'),
      body: optString(json, 'body'),
      placement: optString(json, 'placement', 'bottom'),
      backgroundColor:
          colorOr(optString(json, 'backgroundColor'), const Color(0xFFFFFFFF)),
      borderColor: colorOr(optString(json, 'borderColor'), const Color(0x00000000)),
      borderWidth: optDouble(json, 'borderWidth', 0),
      cornerRadius: optDouble(json, 'cornerRadius', 8),
      shadow: optBool(json, 'shadow', true),
      maxWidth: optDouble(json, 'maxWidth', 280),
      padding: optDouble(json, 'padding', 12),
      showArrow: optBool(json, 'showArrow', true),
      arrowColor: _color(json, 'arrowColor'),
      arrowBorderColor: _color(json, 'arrowBorderColor'),
      arrowSize: optDouble(json, 'arrowSize', 8),
      titleColor: colorOr(optString(json, 'titleColor'), const Color(0xFF111111)),
      titleSize: optDouble(json, 'titleSize', 15),
      titleWeight: _weight(optString(json, 'titleWeight', '700')),
      bodyColor: colorOr(optString(json, 'bodyColor'), const Color(0xFF444444)),
      bodySize: optDouble(json, 'bodySize', 13),
      buttonPrimaryBackgroundColor: colorOr(
          optString(json, 'buttonPrimaryBackgroundColor'), const Color(0xFF2563EB)),
      buttonPrimaryTextColor: colorOr(
          optString(json, 'buttonPrimaryTextColor'), const Color(0xFFFFFFFF)),
      buttonGhostTextColor: colorOr(
          optString(json, 'buttonGhostTextColor'), const Color(0xFF2563EB)),
      actions: _actions(json),
    );
  }
}

// ── Spotlight step ────────────────────────────────────────────────────────────

class SpotlightStep {
  final String anchorKey;
  final int? delayInMs;
  final String title;
  final String body;
  final String calloutPosition; // above|below|left|right|auto
  final double calloutGap;
  final Color overlayColor;
  final double overlayOpacity;
  final String highlightShape; // rect|circle|pill
  final double highlightCornerRadius;
  final double highlightPadding;
  final Color highlightGlowColor;
  final double highlightGlowWidth;
  final Color calloutBackgroundColor;
  final double calloutCornerRadius;
  final double calloutMaxWidth;
  final double calloutPadding;
  final bool calloutShadow;
  final Color calloutBorderColor;
  final double calloutBorderWidth;
  final bool showArrow;
  final Color? arrowColor;
  final Color? arrowBorderColor;
  final double arrowSize;
  final Color titleColor;
  final double titleSize;
  final FontWeight titleWeight;
  final Color bodyColor;
  final double bodySize;
  final Color buttonPrimaryBackgroundColor;
  final Color buttonPrimaryTextColor;
  final Color buttonGhostTextColor;
  final List<GuideAction> actions;

  const SpotlightStep({
    required this.anchorKey,
    required this.delayInMs,
    required this.title,
    required this.body,
    required this.calloutPosition,
    required this.calloutGap,
    required this.overlayColor,
    required this.overlayOpacity,
    required this.highlightShape,
    required this.highlightCornerRadius,
    required this.highlightPadding,
    required this.highlightGlowColor,
    required this.highlightGlowWidth,
    required this.calloutBackgroundColor,
    required this.calloutCornerRadius,
    required this.calloutMaxWidth,
    required this.calloutPadding,
    required this.calloutShadow,
    required this.calloutBorderColor,
    required this.calloutBorderWidth,
    required this.showArrow,
    required this.arrowColor,
    required this.arrowBorderColor,
    required this.arrowSize,
    required this.titleColor,
    required this.titleSize,
    required this.titleWeight,
    required this.bodyColor,
    required this.bodySize,
    required this.buttonPrimaryBackgroundColor,
    required this.buttonPrimaryTextColor,
    required this.buttonGhostTextColor,
    required this.actions,
  });

  static SpotlightStep? fromJson(Map<String, dynamic> json) {
    final anchorKey = optString(json, 'anchorKey');
    if (anchorKey.isEmpty) return null;
    return SpotlightStep(
      anchorKey: anchorKey,
      delayInMs: json.containsKey('delayInMs') ? optInt(json, 'delayInMs', 0) : null,
      title: optString(json, 'title'),
      body: optString(json, 'body'),
      calloutPosition: optString(json, 'calloutPosition', 'below'),
      calloutGap: optDouble(json, 'calloutGap', 8),
      overlayColor: colorOr(optString(json, 'overlayColor'), const Color(0xFF000000)),
      overlayOpacity: optDouble(json, 'overlayOpacity', 0.7),
      highlightShape: optString(json, 'highlightShape', 'rect'),
      highlightCornerRadius: optDouble(json, 'highlightCornerRadius', 8),
      highlightPadding: optDouble(json, 'highlightPadding', 8),
      highlightGlowColor:
          colorOr(optString(json, 'highlightGlowColor'), const Color(0x00000000)),
      highlightGlowWidth: optDouble(json, 'highlightGlowWidth', 0),
      calloutBackgroundColor: colorOr(
          optString(json, 'calloutBackgroundColor'), const Color(0xFFFFFFFF)),
      calloutCornerRadius: optDouble(json, 'calloutCornerRadius', 8),
      calloutMaxWidth: optDouble(json, 'calloutMaxWidth', 280),
      calloutPadding: optDouble(json, 'calloutPadding', 12),
      calloutShadow: optBool(json, 'calloutShadow', true),
      calloutBorderColor:
          colorOr(optString(json, 'calloutBorderColor'), const Color(0x00000000)),
      calloutBorderWidth: optDouble(json, 'calloutBorderWidth', 0),
      showArrow: optBool(json, 'showArrow', true),
      arrowColor: _color(json, 'arrowColor'),
      arrowBorderColor: _color(json, 'arrowBorderColor'),
      arrowSize: optDouble(json, 'arrowSize', 8),
      titleColor: colorOr(optString(json, 'titleColor'), const Color(0xFF111111)),
      titleSize: optDouble(json, 'titleSize', 15),
      titleWeight: _weight(optString(json, 'titleWeight', '700')),
      bodyColor: colorOr(optString(json, 'bodyColor'), const Color(0xFF444444)),
      bodySize: optDouble(json, 'bodySize', 13),
      buttonPrimaryBackgroundColor: colorOr(
          optString(json, 'buttonPrimaryBackgroundColor'), const Color(0xFF2563EB)),
      buttonPrimaryTextColor: colorOr(
          optString(json, 'buttonPrimaryTextColor'), const Color(0xFFFFFFFF)),
      buttonGhostTextColor: colorOr(
          optString(json, 'buttonGhostTextColor'), const Color(0xFF2563EB)),
      actions: _actions(json),
    );
  }
}

// ── Guide config (tooltip | spotlight) ─────────────────────────────────────────

sealed class GuideConfig {
  final String? templateId;
  final String outsideTapBehavior; // dismiss | next | nothing

  const GuideConfig({this.templateId, this.outsideTapBehavior = 'next'});

  int get stepCount;
  String get firstAnchorKey;
  List<String> get anchorKeys;

  /// Parses a guide from a campaign's JSON, mirroring RN's `_parseTemplateConfig`
  /// (flat `templateConfig` with `templateType` `tooltip`/`spotlight`).
  static GuideConfig? fromCampaignJson(Map<String, dynamic> json) {
    final tc = optMap(json, 'templateConfig');
    if (tc == null) return null;
    final type = optString(tc, 'templateType');
    final rawSteps = optList(tc, 'steps') ?? const [];
    final templateId = _optStringOrNull(tc, 'templateId');
    final outsideTap = optString(tc, 'outsideTapBehavior', 'next');

    if (type == 'tooltip') {
      final steps = <TooltipStep>[];
      for (final raw in rawSteps) {
        if (raw is Map) {
          final s = TooltipStep.fromJson(raw.cast<String, dynamic>());
          if (s != null) steps.add(s);
        }
      }
      if (steps.isEmpty) return null;
      return TooltipGuideConfig(
          steps: steps, templateId: templateId, outsideTapBehavior: outsideTap);
    }
    if (type == 'spotlight') {
      final steps = <SpotlightStep>[];
      for (final raw in rawSteps) {
        if (raw is Map) {
          final s = SpotlightStep.fromJson(raw.cast<String, dynamic>());
          if (s != null) steps.add(s);
        }
      }
      if (steps.isEmpty) return null;
      return SpotlightGuideConfig(
          steps: steps, templateId: templateId, outsideTapBehavior: outsideTap);
    }
    return null;
  }
}

class TooltipGuideConfig extends GuideConfig {
  final List<TooltipStep> steps;

  const TooltipGuideConfig({
    required this.steps,
    super.templateId,
    super.outsideTapBehavior,
  });

  @override
  int get stepCount => steps.length;
  @override
  String get firstAnchorKey => steps.first.anchorKey;
  @override
  List<String> get anchorKeys => [for (final s in steps) s.anchorKey];
}

class SpotlightGuideConfig extends GuideConfig {
  final List<SpotlightStep> steps;

  const SpotlightGuideConfig({
    required this.steps,
    super.templateId,
    super.outsideTapBehavior,
  });

  @override
  int get stepCount => steps.length;
  @override
  String get firstAnchorKey => steps.first.anchorKey;
  @override
  List<String> get anchorKeys => [for (final s in steps) s.anchorKey];
}

// ── shared parse helpers ───────────────────────────────────────────────────────

String? _optStringOrNull(Map<String, dynamic> json, String key) {
  final v = optString(json, key);
  return v.isEmpty ? null : v;
}

Color? _color(Map<String, dynamic> json, String key) {
  final v = optString(json, key);
  if (v.isEmpty) return null;
  return colorOr(v, const Color(0xFF000000));
}

List<GuideAction> _actions(Map<String, dynamic> json) {
  final list = optList(json, 'actions') ?? const [];
  final result = <GuideAction>[];
  for (final raw in list) {
    if (raw is Map) result.add(GuideAction.fromJson(raw.cast<String, dynamic>()));
  }
  return result;
}

FontWeight _weight(String raw) {
  switch (raw.trim()) {
    case '700':
      return FontWeight.w700;
    case '600':
      return FontWeight.w600;
    case '500':
      return FontWeight.w500;
    case '400':
    default:
      return FontWeight.w400;
  }
}
