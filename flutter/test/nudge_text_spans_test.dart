import 'package:digia_engage/api/internal/nudge/nudge_content.dart';
import 'package:digia_engage/api/internal/nudge/nudge_parser.dart';
import 'package:flutter/painting.dart';
import 'package:flutter_test/flutter_test.dart';

/// Builds a minimal nudge `templateConfig` with a single `digia/text` node whose
/// `props` are [textProps], and returns the parsed [NudgeText].
NudgeText parseText(Map<String, dynamic> textProps) {
  final config = <String, dynamic>{
    'layout': {
      'type': 'digia/column',
      'props': const {},
      'children': [
        {'type': 'digia/text', 'props': textProps},
      ],
    },
  };
  final parsed = const NudgeParser().parse(config);
  return parsed!.content.children.single as NudgeText;
}

void main() {
  group('NudgeParser text rich spans', () {
    test('a plain text node has no spans', () {
      final text = parseText({'text': 'Hello'});
      expect(text.text, 'Hello');
      expect(text.spans, isEmpty);
    });

    test('decodes the spans overlay with per-run style overrides', () {
      final text = parseText({
        'text': 'Welcome back',
        'spans': [
          {
            'text': 'Welcome ',
            'style': {'fontWeight': 700},
          },
          {
            'text': 'back',
            'style': {
              'fontSize': 18,
              'textColor': '#FF0000',
              'highlightColor': '#FFE08A',
              'fontStyle': 'italic',
              'decoration': 'underline',
              'decorationColor': '#00FF00',
              'decorationThickness': 2,
            },
          },
        ],
        // Line height is block-level (whole text), not per-span.
        'lineHeight': 1.4,
      });

      // Block-level line height lands on the node, not the spans.
      expect(text.lineHeight, 1.4);
      expect(text.spans, hasLength(2));

      final first = text.spans[0];
      expect(first.text, 'Welcome ');
      expect(first.style.weight, FontWeight.w700);
      // Unset fields inherit the base (null/false on the span).
      expect(first.style.fontSize, isNull);
      expect(first.style.color, isNull);
      expect(first.style.italic, isFalse);
      expect(first.style.decoration, isNull);

      final second = text.spans[1];
      expect(second.text, 'back');
      expect(second.style.fontSize, 18);
      expect(second.style.color, const Color(0xFFFF0000));
      expect(second.style.highlightColor, const Color(0xFFFFE08A));
      expect(second.style.weight, isNull);
      expect(second.style.italic, isTrue);
      // Decoration (underline / colour / thickness) is temporarily disabled in the
      // parser pending cross-platform parity, so it never parses even though the
      // wire above still carries the fields.
      expect(second.style.decoration, isNull);
      expect(second.style.decorationColor, isNull);
      expect(second.style.decorationThickness, isNull);
    });

    test('skips empty/invalid runs and out-of-range weights', () {
      final text = parseText({
        'text': 'x',
        'spans': [
          {'text': '', 'style': {}}, // empty run dropped
          'not-a-map', // non-map dropped
          {
            'text': 'x',
            'style': {'fontWeight': 450}, // not on the 100…900 grid → inherit
          },
        ],
      });

      expect(text.spans, hasLength(1));
      expect(text.spans.single.text, 'x');
      expect(text.spans.single.style.weight, isNull);
    });

    test('parses 400/500/600/700 as DISTINCT FontWeights (no collapse in code)', () {
      final text = parseText({
        'text': 'abcd',
        'spans': [
          {'text': 'a', 'style': {'fontWeight': 400}},
          {'text': 'b', 'style': {'fontWeight': 500}},
          {'text': 'c', 'style': {'fontWeight': 600}},
          {'text': 'd', 'style': {'fontWeight': 700}},
        ],
      });
      // The SDK maps each weight to a distinct FontWeight; any visual collapse
      // (500→400, 600→700) is the configured font lacking those faces, not this.
      expect(text.spans.map((s) => s.style.weight).toList(), [
        FontWeight.w400,
        FontWeight.w500,
        FontWeight.w600,
        FontWeight.w700,
      ]);
    });
  });
}
