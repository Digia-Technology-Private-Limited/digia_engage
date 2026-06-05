import 'package:flutter/material.dart';

import '../campaign/json_util.dart';
import 'nudge_config.dart';
import 'nudge_content.dart';

/// Decodes a nudge `templateConfig` (`{ container, layout }`) into the pure
/// [NudgeConfig] model.
///
/// This is the single place that knows the wire format. The model
/// (`nudge_content.dart`) and the renderer (`nudge_view.dart`) stay decoupled
/// from JSON, so the wire shape can evolve here without rippling outward (SRP).
///
/// Node decoding is dispatched through a registry ([_nodeParsers]) keyed by the
/// native widget type. Supporting a new nudge widget is an open/closed change:
/// add one entry to the map; nothing else in the parser is touched.
class NudgeParser {
  const NudgeParser();

  /// Returns `null` when the content tree is missing — such a campaign has
  /// nothing to show, so it is dropped rather than presented empty.
  NudgeConfig? parse(Map<String, dynamic> templateConfig) {
    final layout = optMap(templateConfig, 'layout');
    if (layout == null || layout.isEmpty) return null;

    return NudgeConfig(
      surface: _surface(optMap(templateConfig, 'container')),
      content: _column(layout),
    );
  }

  // ─── surface ───────────────────────────────────────────────────────────────

  NudgeSurface _surface(Map<String, dynamic>? json) {
    final map = json ?? const <String, dynamic>{};
    return NudgeSurface(
      displayType: optString(map, 'displayType', 'bottom_sheet') == 'dialog'
          ? NudgeDisplayType.dialog
          : NudgeDisplayType.bottomSheet,
      backgroundColor: _color(optString(map, 'backgroundColor')),
      cornerRadius: optDouble(map, 'cornerRadius', 18),
      padding: optDouble(map, 'padding', 20),
      backdropDismissible: optBool(map, 'backdropDismissible', true),
      showCloseButton: optBool(map, 'showCloseButton', false),
      showHandle: optBool(map, 'showHandle', true),
      draggable: optBool(map, 'draggable', true),
      // Stored as a 0…100 percentage; normalise to a 0…1 fraction.
      widthFraction: (optDouble(map, 'widthPct', 86) / 100).clamp(0.3, 1.0),
    );
  }

  // ─── column + children ───────────────────────────────────────────────────────

  NudgeColumn _column(Map<String, dynamic> layout) {
    final props = optMap(layout, 'props') ?? const <String, dynamic>{};
    return NudgeColumn(
      crossAxisAlignment: _crossAxis(optString(props, 'crossAxisAlignment', 'start')),
      mainAxisAlignment: _mainAxis(optString(props, 'mainAxisAlignment', 'start')),
      spacing: optDouble(props, 'spacing', 0),
      children: _childNodes(layout).map(_node).whereType<NudgeNode>().toList(),
    );
  }

  /// Reads a node's children whether they arrive as a bare list
  /// (`children: [...]`) or a child-group map (`children: { children: [...] }` /
  /// `childGroups: { children: [...] }`) — agnostic to which form was emitted.
  List<Map<String, dynamic>> _childNodes(Map<String, dynamic> node) {
    Object? raw = node['children'] ?? node['childGroups'];
    if (raw is Map) raw = raw['children'];
    if (raw is! List) return const [];
    return raw.whereType<Map>().map((m) => m.cast<String, dynamic>()).toList();
  }

  NudgeNode? _node(Map<String, dynamic> node) {
    final parse = _nodeParsers[optString(node, 'type')];
    if (parse == null) return null;
    final box = _box(optMap(node, 'containerProps'));
    return parse(box, optMap(node, 'props') ?? const <String, dynamic>{});
  }

  // ─── node parser registry (Strategy) ─────────────────────────────────────────

  static final Map<String, NudgeNode Function(NudgeBox, Map<String, dynamic>)>
      _nodeParsers = {
    'digia/text': _text,
    'digia/image': _image,
    'digia/button': _button,
    'fw/sized_box': _gap,
    'digia/styledHorizontalDivider': _divider,
    'digia/lottie': _lottie,
  };

  static NudgeNode _text(NudgeBox box, Map<String, dynamic> props) {
    final style = optMap(props, 'textStyle') ?? const {};
    final font = optMap(optMap(style, 'fontToken') ?? const {}, 'font') ?? const {};
    return NudgeText(
      box,
      text: optString(props, 'text'),
      fontSize: optDouble(font, 'size', 16),
      weight: _fontWeight(optString(font, 'weight', '400')),
      color: _color(optString(style, 'textColor')) ?? const Color(0xFF111111),
      align: _textAlign(optString(props, 'alignment', 'left')),
    );
  }

  static NudgeNode _image(NudgeBox box, Map<String, dynamic> props) => NudgeImage(
        box,
        url: optString(optMap(props, 'src') ?? const {}, 'imageSrc'),
        fit: _boxFit(optString(props, 'fit', 'cover')),
      );

  static NudgeNode _button(NudgeBox box, Map<String, dynamic> props) {
    final text = optMap(props, 'text') ?? const {};
    final textStyle = optMap(text, 'textStyle') ?? const {};
    return NudgeButton(
      box,
      label: optString(text, 'text', 'Button'),
      background: _color(optString(optMap(props, 'defaultStyle') ?? const {}, 'backgroundColor')) ??
          const Color(0xFF4945FF),
      textColor: _color(optString(textStyle, 'textColor')) ?? const Color(0xFFFFFFFF),
      radius: optDouble(optMap(props, 'shape') ?? const {}, 'borderRadius', 8),
      onClick: optMap(props, 'onClick'),
    );
  }

  static NudgeNode _gap(NudgeBox box, Map<String, dynamic> props) =>
      NudgeGap(box, height: optDouble(props, 'height', 8));

  static NudgeNode _divider(NudgeBox box, Map<String, dynamic> props) => NudgeDivider(
        box,
        thickness: optDouble(props, 'thickness', 1),
        indent: optDouble(props, 'indent', 0),
        endIndent: optDouble(props, 'endIndent', 0),
        color: _color(optString(optMap(props, 'colorType') ?? const {}, 'color')) ??
            const Color(0xFFE0E0E0),
      );

  static NudgeNode _lottie(NudgeBox box, Map<String, dynamic> props) => NudgeLottie(
        box,
        url: optString(optMap(props, 'src') ?? const {}, 'lottiePath'),
        height: optDouble(props, 'height', 160),
        loop: optString(props, 'animationType', 'loop') != 'once',
        autoplay: optBool(props, 'animate', true),
      );

  // ─── common box ──────────────────────────────────────────────────────────────

  NudgeBox _box(Map<String, dynamic>? containerProps) {
    final cp = containerProps ?? const <String, dynamic>{};
    final style = optMap(cp, 'style') ?? const <String, dynamic>{};
    final border = optMap(style, 'border');

    final widthStr = optString(style, 'width');
    return NudgeBox(
      fillWidth: widthStr == '100%',
      fixedWidth: widthStr == '100%' ? null : double.tryParse(widthStr),
      fixedHeight: double.tryParse(optString(style, 'height')),
      background: _color(optString(style, 'bgColor', optString(style, 'backgroundColor'))),
      padding: optDouble(style, 'padding', 0),
      margin: optDouble(style, 'margin', 0),
      borderRadius: optDouble(style, 'borderRadius', 0),
      borderColor: border == null ? null : _color(optString(border, 'borderColor')),
      borderWidth: border == null ? 0 : optDouble(border, 'borderWidth', 0),
      selfAlign: _selfAlign(optString(cp, 'align')),
    );
  }
}

// ─── enum + colour mapping ─────────────────────────────────────────────────────

CrossAxisAlignment _crossAxis(String value) => switch (value) {
      'center' => CrossAxisAlignment.center,
      'end' => CrossAxisAlignment.end,
      _ => CrossAxisAlignment.start,
    };

MainAxisAlignment _mainAxis(String value) => switch (value) {
      'center' => MainAxisAlignment.center,
      'end' => MainAxisAlignment.end,
      'spaceBetween' => MainAxisAlignment.spaceBetween,
      'spaceAround' => MainAxisAlignment.spaceAround,
      'spaceEvenly' => MainAxisAlignment.spaceEvenly,
      _ => MainAxisAlignment.start,
    };

TextAlign _textAlign(String value) => switch (value) {
      'center' => TextAlign.center,
      'right' || 'end' => TextAlign.right,
      _ => TextAlign.left,
    };

BoxFit _boxFit(String value) => switch (value) {
      'contain' => BoxFit.contain,
      'fill' => BoxFit.fill,
      _ => BoxFit.cover,
    };

FontWeight _fontWeight(String value) => switch (value) {
      '500' => FontWeight.w500,
      '600' => FontWeight.w600,
      '700' => FontWeight.w700,
      _ => FontWeight.w400,
    };

NudgeSelfAlign? _selfAlign(String value) => switch (value) {
      'start' => NudgeSelfAlign.start,
      'center' => NudgeSelfAlign.center,
      'end' => NudgeSelfAlign.end,
      _ => null,
    };

/// Parses `#RGB` / `#RRGGBB` / `#AARRGGBB`; empty/invalid → null.
Color? _color(String hex) {
  var value = hex.trim();
  if (value.isEmpty) return null;
  if (value.startsWith('#')) value = value.substring(1);
  if (value.length == 3) value = value.split('').map((c) => '$c$c').join();
  if (value.length == 6) value = 'FF$value';
  if (value.length != 8) return null;
  final intValue = int.tryParse(value, radix: 16);
  return intValue == null ? null : Color(intValue);
}
