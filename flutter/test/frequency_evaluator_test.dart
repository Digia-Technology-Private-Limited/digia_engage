import 'package:digia_engage/api/internal/frequency/frequency_evaluator.dart';
import 'package:digia_engage/api/internal/frequency/frequency_policy.dart';
import 'package:flutter_test/flutter_test.dart';

/// Mirrors the React Native SDK's frequency semantics (`frequencyEvaluator.ts`):
/// a null state always allows; a stopped campaign is blocked; `max_total` and a
/// `max_per_window` count both cap the show count; an elapsed window blocks.
void main() {
  const oneDayMs = 86400000;

  group('FrequencyPolicy.fromJson', () {
    test('returns null when absent', () {
      expect(FrequencyPolicy.fromJson(null), isNull);
    });

    test('parses max_total / max_per_window / stop_on', () {
      final policy = FrequencyPolicy.fromJson({
        'max_total': 3,
        'max_per_window': {'count': 2, 'window': 'week'},
        'stop_on': 'any_action',
        'min_gap_ms': 1000,
      })!;
      expect(policy.maxTotal, 3);
      expect(policy.maxPerWindow!.count, 2);
      expect(policy.maxPerWindow!.window, FrequencyWindowUnit.week);
      expect(policy.stopOn, FrequencyStopOn.anyAction);
      expect(policy.minGapMs, 1000);
      expect(policy.hasPolicy, isTrue);
      expect(policy.isSession, isFalse);
    });

    test('isSession is true for a session window', () {
      final policy = FrequencyPolicy.fromJson({
        'max_per_window': {'count': 1, 'window': 'session'},
      })!;
      expect(policy.isSession, isTrue);
    });

    test('hasPolicy is false for an empty / unrecognised policy', () {
      expect(FrequencyPolicy.fromJson({'foo': 'bar'})!.hasPolicy, isFalse);
      // A malformed window (missing count) is dropped, leaving no limit.
      expect(
        FrequencyPolicy.fromJson({
          'max_per_window': {'window': 'day'},
        })!
            .hasPolicy,
        isFalse,
      );
    });
  });

  group('FrequencyEvaluator.evaluate', () {
    const maxTotal2 = FrequencyPolicy(maxTotal: 2);

    test('allows when there is no state yet', () {
      final result = FrequencyEvaluator.evaluate(maxTotal2, null, 0);
      expect(result.allow, isTrue);
      expect(result.reason, isNull);
    });

    test('blocks once shown_count reaches max_total', () {
      final result = FrequencyEvaluator.evaluate(
        maxTotal2,
        const FrequencyState(shownCount: 2, firstShownAt: 0),
        oneDayMs,
      );
      expect(result.allow, isFalse);
      expect(result.reason, FrequencySkipReason.maxTotal);
    });

    test('blocks a stopped campaign regardless of counts', () {
      final result = FrequencyEvaluator.evaluate(
        const FrequencyPolicy(stopOn: FrequencyStopOn.click),
        const FrequencyState(shownCount: 0, stoppedAt: 5, stoppedReason: 'click'),
        oneDayMs,
      );
      expect(result.allow, isFalse);
      expect(result.reason, FrequencySkipReason.stopped);
    });

    group('max_per_window', () {
      const dailyTwice = FrequencyPolicy(
        maxPerWindow: FrequencyWindow(count: 2, window: FrequencyWindowUnit.day),
      );

      test('allows within the window and under the count', () {
        final result = FrequencyEvaluator.evaluate(
          dailyTwice,
          const FrequencyState(shownCount: 1, firstShownAt: 0),
          oneDayMs ~/ 2,
        );
        expect(result.allow, isTrue);
      });

      test('blocks once the window has elapsed since first show', () {
        final result = FrequencyEvaluator.evaluate(
          dailyTwice,
          const FrequencyState(shownCount: 1, firstShownAt: 0),
          oneDayMs + 1,
        );
        expect(result.allow, isFalse);
        expect(result.reason, FrequencySkipReason.window);
      });

      test('blocks once the per-window count is reached', () {
        final result = FrequencyEvaluator.evaluate(
          dailyTwice,
          const FrequencyState(shownCount: 2, firstShownAt: 0),
          1,
        );
        expect(result.allow, isFalse);
        expect(result.reason, FrequencySkipReason.maxTotal);
      });

      test('a session window has no wall-clock expiry', () {
        const sessionPolicy = FrequencyPolicy(
          maxPerWindow:
              FrequencyWindow(count: 1, window: FrequencyWindowUnit.session),
        );
        // Far in the "future": session windows are bounded by the process, not
        // time, so the count is the only gate.
        final allowed = FrequencyEvaluator.evaluate(
          sessionPolicy,
          const FrequencyState(shownCount: 0, firstShownAt: 0),
          oneDayMs * 365,
        );
        expect(allowed.allow, isTrue);
        final blocked = FrequencyEvaluator.evaluate(
          sessionPolicy,
          const FrequencyState(shownCount: 1, firstShownAt: 0),
          oneDayMs * 365,
        );
        expect(blocked.allow, isFalse);
        expect(blocked.reason, FrequencySkipReason.maxTotal);
      });
    });
  });
}
