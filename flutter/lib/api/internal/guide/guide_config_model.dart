import 'dart:ui' show Color;

import '../campaign/json_util.dart';
import 'guide_color.dart';

/// Typed config holders for a guide (tooltip / spotlight) campaign.
///
/// Dart port of the Android guide models (`GuideConfigModel`, `GuideStepModel`,
/// `GuideStepWidgetConfig` in `internal/model/`). Field names, defaults and the
/// two accepted JSON shapes (nested `guideConfig` and flat `templateConfig`)
/// mirror `CampaignModel.parseGuideConfig` / `GuideStepWidgetConfig.fromJson`
/// exactly, so the same backend payload renders identically across platforms.
///
/// Like the other `internal/model` holders these stay free of widget/Compose
/// imports — only `dart:ui`'s [Color] — so parsing has no UI dependency.

// ── Default colours (match Android GuideStepWidgetConfig) ────────────────────
const Color _defaultBubbleBg = Color(0xFF1E40AF);
const Color _defaultArrowColor = Color(0xFF1E40AF);
const Color _defaultOverlay = Color(0xFF000000);
const Color _defaultBtnBg = Color(0xFFFFFFFF);
const Color _defaultBtnText = Color(0xFF1E40AF);
const Color _defaultBodyColor = Color(0xCCFFFFFF);
const Color _defaultTitleColor = Color(0xFFFFFFFF);

// Android default step colour is parseColor("#FFFFFFAA") → ARGB(0xAAFFFFFF).
const Color _stepIndicatorDefault = Color(0xAAFFFFFF);

/// How a guide action button advances the flow.
enum GuideActionType { dismiss, next, prev }

GuideActionType _actionTypeFrom(String raw) {
  switch (raw.trim().toUpperCase()) {
    case 'NEXT':
      return GuideActionType.next;
    case 'PREV':
      return GuideActionType.prev;
    case 'DISMISS':
    default:
      return GuideActionType.dismiss;
  }
}

/// A button rendered inside the guide bubble.
class GuideAction {
  final String id;
  final String label;
  final String style; // "filled" | "ghost"
  final GuideActionType actionType;
  final Color backgroundColor;
  final Color textColor;
  final double cornerRadius;

  const GuideAction({
    required this.id,
    required this.label,
    required this.style,
    required this.actionType,
    required this.backgroundColor,
    required this.textColor,
    required this.cornerRadius,
  });

  static GuideAction fromJson(Map<String, dynamic> json, int index) {
    final typeRaw = optString(json, 'action_type').isNotEmpty
        ? optString(json, 'action_type')
        : optString(json, 'type', 'dismiss');
    return GuideAction(
      id: optString(json, 'id', 'btn_$index'),
      label: optString(json, 'label'),
      style: optString(json, 'style', 'filled'),
      actionType: _actionTypeFrom(typeRaw),
      backgroundColor: colorOr(optString(json, 'background_color'), _defaultBtnBg),
      textColor: colorOr(optString(json, 'text_color'), _defaultBtnText),
      cornerRadius: optDouble(json, 'corner_radius', 8),
    );
  }
}

/// Arrow pointer styling for the bubble.
class ArrowConfig {
  final bool visible;
  final String preferredDirection; // "top"|"bottom"|"start"|"end"|"auto"
  final double size;
  final Color color;

  const ArrowConfig({
    required this.visible,
    required this.preferredDirection,
    required this.size,
    required this.color,
  });

  static ArrowConfig fromJson(Map<String, dynamic> json) => ArrowConfig(
        visible: optBool(json, 'visible', true),
        preferredDirection: optString(json, 'preferred_direction', 'auto'),
        size: optDouble(json, 'size', 10),
        color: colorOr(optString(json, 'color'), _defaultArrowColor),
      );
}

/// Tooltip card styling.
class BubbleConfig {
  final Color backgroundColor;
  final double cornerRadius;
  final double paddingHorizontal;
  final double paddingVertical;
  final double maxWidth;
  final double elevation;
  final String entranceAnimation;
  final ArrowConfig arrow;

  const BubbleConfig({
    required this.backgroundColor,
    required this.cornerRadius,
    required this.paddingHorizontal,
    required this.paddingVertical,
    required this.maxWidth,
    required this.elevation,
    required this.entranceAnimation,
    required this.arrow,
  });

  static BubbleConfig fromJson(Map<String, dynamic> json) {
    final arrow = ArrowConfig.fromJson(optMap(json, 'arrow') ?? const {});
    return BubbleConfig(
      backgroundColor:
          colorOr(optString(json, 'background_color'), _defaultBubbleBg),
      cornerRadius: optDouble(json, 'corner_radius', 12),
      paddingHorizontal: optDouble(json, 'padding_horizontal', 16),
      paddingVertical: optDouble(json, 'padding_vertical', 12),
      maxWidth: optDouble(json, 'max_width', 280),
      elevation: optDouble(json, 'elevation', 6),
      entranceAnimation: optString(json, 'entrance_animation', 'elastic'),
      arrow: arrow,
    );
  }
}

/// Spotlight cutout shape around the anchor.
class CutoutConfig {
  final String shape; // "rounded_rect"|"rect"|"circle"
  final double cornerRadius;
  final double padding;

  const CutoutConfig({
    required this.shape,
    required this.cornerRadius,
    required this.padding,
  });

  static CutoutConfig fromJson(Map<String, dynamic> json) => CutoutConfig(
        shape: optString(json, 'shape', 'rounded_rect'),
        cornerRadius: optDouble(json, 'corner_radius', 12),
        padding: optDouble(json, 'padding', 8),
      );
}

/// Background dimming + cutout. [visible] is the tooltip↔spotlight switch:
/// `false` = tooltip (no scrim), `true` = spotlight (dim + cutout).
class OverlayConfig {
  final bool visible;
  final Color color;
  final double alpha;
  final bool dismissOnTap;
  final String entranceAnimation;
  final CutoutConfig cutout;

  const OverlayConfig({
    required this.visible,
    required this.color,
    required this.alpha,
    required this.dismissOnTap,
    required this.entranceAnimation,
    required this.cutout,
  });

  static OverlayConfig fromJson(Map<String, dynamic> json) => OverlayConfig(
        visible: optBool(json, 'visible', false),
        color: colorOr(optString(json, 'color'), _defaultOverlay),
        alpha: optDouble(json, 'alpha', 0.6),
        dismissOnTap: optBool(json, 'dismiss_on_tap', false),
        entranceAnimation: optString(json, 'entrance_animation', 'fade'),
        cutout: CutoutConfig.fromJson(optMap(json, 'cutout') ?? const {}),
      );
}

/// A styled run of text (title or body).
class GuideTextContent {
  final String text;
  final String fontFamily;
  final String fontWeight; // "bold" | "regular" | ...
  final double fontSize;
  final Color color;

  const GuideTextContent({
    required this.text,
    required this.fontFamily,
    required this.fontWeight,
    required this.fontSize,
    required this.color,
  });
}

/// Multi-step "n / total" indicator styling.
class StepIndicatorConfig {
  final bool visible;
  final Color color;

  const StepIndicatorConfig({required this.visible, required this.color});

  static StepIndicatorConfig fromJson(Map<String, dynamic> json) =>
      StepIndicatorConfig(
        visible: optBool(json, 'visible', false),
        color: colorOr(optString(json, 'color'), _stepIndicatorDefault),
      );
}

/// Text + media + step indicator shown inside the bubble.
class GuideContentConfig {
  final GuideTextContent? title;
  final GuideTextContent? body;
  final String? mediaUrl;
  final StepIndicatorConfig stepIndicator;

  const GuideContentConfig({
    required this.title,
    required this.body,
    required this.mediaUrl,
    required this.stepIndicator,
  });
}

/// The full per-step widget config (bubble + overlay + content + actions).
class GuideStepWidgetConfig {
  final BubbleConfig bubble;
  final OverlayConfig overlay;
  final GuideContentConfig content;
  final List<GuideAction> actions;

  const GuideStepWidgetConfig({
    required this.bubble,
    required this.overlay,
    required this.content,
    required this.actions,
  });

  /// Mirrors Android `GuideStepWidgetConfig.fromJson`, including the legacy
  /// flat `title`/`body` string fallback and `action_type`/`type` aliasing.
  static GuideStepWidgetConfig fromJson(Map<String, dynamic> json) {
    final bubble = BubbleConfig.fromJson(optMap(json, 'bubble') ?? const {});
    final overlay = OverlayConfig.fromJson(optMap(json, 'overlay') ?? const {});

    final contentObj = optMap(json, 'content') ?? const {};
    final titleObj = optMap(contentObj, 'title');
    final bodyObj = optMap(contentObj, 'body');
    final mediaObj = optMap(contentObj, 'media');
    final stepInd = StepIndicatorConfig.fromJson(
        optMap(contentObj, 'step_indicator') ?? const {});

    // Legacy flat schema support: top-level "title"/"body" strings.
    final titleText = titleObj != null
        ? optString(titleObj, 'text')
        : (optString(json, 'title').isNotEmpty ? optString(json, 'title') : null);
    final bodyText = bodyObj != null
        ? optString(bodyObj, 'text')
        : (optString(json, 'body').isNotEmpty ? optString(json, 'body') : null);

    final title = (titleText != null && titleText.isNotEmpty)
        ? _textContent(titleObj, titleText,
            defaultWeight: 'bold', defaultSize: 16, defaultColor: _defaultTitleColor)
        : null;
    final body = (bodyText != null && bodyText.isNotEmpty)
        ? _textContent(bodyObj, bodyText,
            defaultWeight: 'regular', defaultSize: 14, defaultColor: _defaultBodyColor)
        : null;

    final content = GuideContentConfig(
      title: title,
      body: body,
      mediaUrl: mediaObj != null ? optString(mediaObj, 'url') : null,
      stepIndicator: stepInd,
    );

    // Actions may live at the widget root or nested under `content`.
    final actionsList =
        optList(json, 'actions') ?? optList(contentObj, 'actions') ?? const [];
    final actions = <GuideAction>[];
    for (var i = 0; i < actionsList.length; i++) {
      final entry = actionsList[i];
      if (entry is Map) {
        actions.add(GuideAction.fromJson(entry.cast<String, dynamic>(), i));
      }
    }

    return GuideStepWidgetConfig(
      bubble: bubble,
      overlay: overlay,
      content: content,
      actions: actions,
    );
  }

  static GuideTextContent _textContent(
    Map<String, dynamic>? obj,
    String text, {
    required String defaultWeight,
    required double defaultSize,
    required Color defaultColor,
  }) {
    final ts = (obj != null ? optMap(obj, 'textStyle') : null) ??
        const <String, dynamic>{};
    final fontToken = optMap(ts, 'fontToken') ?? const <String, dynamic>{};
    final font = optMap(fontToken, 'font') ?? const <String, dynamic>{};
    return GuideTextContent(
      text: text,
      fontFamily: optString(font, 'fontFamily'),
      fontWeight: optString(font, 'weight', defaultWeight),
      fontSize: optDouble(font, 'size', defaultSize),
      color: colorOr(optString(ts, 'textColor'), defaultColor),
    );
  }
}

/// One step of a guide: which anchor, how to display it, and its widget config.
class GuideStepModel {
  final String id;
  final int sequenceOrder;
  final String anchorKey;
  final String displayStyle; // "tooltip" | "spotlight"
  final GuideStepWidgetConfig widgetConfig;
  final String advanceTrigger; // "tap" | "auto"
  final int? autoDelayMs;

  const GuideStepModel({
    required this.id,
    required this.sequenceOrder,
    required this.anchorKey,
    required this.displayStyle,
    required this.widgetConfig,
    required this.advanceTrigger,
    required this.autoDelayMs,
  });
}

/// A whole guide: an ordered list of steps.
class GuideConfigModel {
  final String id;
  final bool multiStep;
  final List<GuideStepModel> steps;

  const GuideConfigModel({
    required this.id,
    required this.multiStep,
    required this.steps,
  });

  /// Builds a guide config from a raw campaign JSON object, or `null` when no
  /// usable guide payload is present. Mirrors Android `parseGuideConfig`:
  ///
  /// 1. nested `guideConfig` (per-step `widgetConfig`, explicit `displayStyle`),
  /// 2. else flat `templateConfig` whose `templateType` is `tooltip`/`spotlight`
  ///    (the step object itself is the widget config; type drives displayStyle).
  static GuideConfigModel? fromCampaignJson(
      Map<String, dynamic> json, String fallbackId) {
    final guideObj = optMap(json, 'guideConfig');
    if (guideObj != null) {
      return _build(
        guideId: _firstNonEmpty(
            [optString(guideObj, 'id'), optString(guideObj, '_id'), fallbackId]),
        multiStep: optBool(guideObj, 'multiStep', false),
        steps: optList(guideObj, 'steps'),
        displayStyle: null,
        widgetJsonForStep: (step) => optMap(step, 'widgetConfig'),
      );
    }

    final templateObj = optMap(json, 'templateConfig');
    if (templateObj != null) {
      final type = optString(templateObj, 'templateType');
      if (type == 'tooltip' || type == 'spotlight') {
        final steps = optList(templateObj, 'steps');
        return _build(
          guideId: _firstNonEmpty(
              [optString(templateObj, 'templateId'), fallbackId]),
          multiStep: (steps?.length ?? 0) > 1,
          steps: steps,
          displayStyle: type,
          widgetJsonForStep: (step) => step,
        );
      }
    }
    return null;
  }

  static GuideConfigModel? _build({
    required String guideId,
    required bool multiStep,
    required List<dynamic>? steps,
    required String? displayStyle,
    required Map<String, dynamic>? Function(Map<String, dynamic>) widgetJsonForStep,
  }) {
    if (steps == null) return null;
    final result = <GuideStepModel>[];
    for (var i = 0; i < steps.length; i++) {
      final raw = steps[i];
      if (raw is! Map) continue;
      final step = raw.cast<String, dynamic>();
      final anchorKey = optString(step, 'anchorKey');
      if (anchorKey.isEmpty) continue;
      final widgetJson = widgetJsonForStep(step);
      if (widgetJson == null) continue;
      result.add(GuideStepModel(
        id: _firstNonEmpty([optString(step, 'id'), optString(step, '_id')]),
        sequenceOrder: optInt(step, 'sequenceOrder', i),
        anchorKey: anchorKey,
        displayStyle: displayStyle ?? optString(step, 'displayStyle', 'tooltip'),
        widgetConfig: GuideStepWidgetConfig.fromJson(widgetJson),
        advanceTrigger: optString(step, 'advanceTrigger', 'tap'),
        autoDelayMs: step.containsKey('autoDelayMs')
            ? optInt(step, 'autoDelayMs', 0)
            : null,
      ));
    }
    if (result.isEmpty) return null;
    result.sort((a, b) => a.sequenceOrder.compareTo(b.sequenceOrder));
    return GuideConfigModel(id: guideId, multiStep: multiStep, steps: result);
  }

  static String _firstNonEmpty(List<String> values) =>
      values.firstWhere((v) => v.isNotEmpty, orElse: () => '');
}
