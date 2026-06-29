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
              'lineHeight': 1.4,
            },
          },
        ],
      });

      expect(text.spans, hasLength(2));

      final first = text.spans[0];
      expect(first.text, 'Welcome ');
      expect(first.weight, FontWeight.w700);
      // Unset fields inherit the base (null on the span).
      expect(first.fontSize, isNull);
      expect(first.color, isNull);

      final second = text.spans[1];
      expect(second.text, 'back');
      expect(second.fontSize, 18);
      expect(second.color, const Color(0xFFFF0000));
      expect(second.highlightColor, const Color(0xFFFFE08A));
      expect(second.lineHeight, 1.4);
      expect(second.weight, isNull);
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
      expect(text.spans.single.weight, isNull);
    });
  });
}
