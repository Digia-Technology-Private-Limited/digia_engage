import 'package:digia_engage/api/internal/frequency/frequency_evaluator.dart';
import 'package:digia_engage/api/internal/frequency/frequency_policy.dart';
import 'package:flutter_test/flutter_test.dart';

/// Golden matrix for frequency capping — mirrors Android's `FrequencyManagerTest`
/// (and the RN/iOS suites) exactly. `now` and `sessionId` are injected for
/// determinism.
void main() {
  const hour = 3600000;
  const day = 86400000;

  FrequencyWindow win(int count, String window) =>
      FrequencyWindow(count: count, window: window);

  // Drive a policy through show attempts, threading state the way the real
  // wiring does: evaluate → if allowed, recordShow → if complete, recordStop.
  List<bool> run(FrequencyPolicy policy, List<(int, String, bool)> attempts) {
    FrequencyState? state;
    final allowed = <bool>[];
    for (final (now, sessionId, complete) in attempts) {
      final res = FrequencyEvaluator.evaluate(policy, state, now, sessionId);
      allowed.add(res.allow);
      if (res.allow) {
        state = FrequencyEvaluator.recordShow(policy, state, now, sessionId);
      }
      if (complete) state = FrequencyEvaluator.recordStop(state, now);
    }
    return allowed;
  }

  test('1 - no-constraint policy is treated as uncapped', () {
    expect(const FrequencyPolicy().hasConstraint, isFalse);
    expect(const FrequencyPolicy(maxTotal: 1).hasConstraint, isTrue);
  });

  test('2 - maxTotal 3 allows three then blocks', () {
    const policy = FrequencyPolicy(maxTotal: 3);
    expect(
      run(policy, [(0, 's1', false), (hour, 's1', false), (2 * hour, 's1', false), (3 * hour, 's1', false)]),
      [true, true, true, false],
    );
  });

  test('3 - session window same session blocks second', () {
    final policy = FrequencyPolicy(maxPerWindow: win(1, 'session'));
    expect(run(policy, [(0, 's1', false), (hour, 's1', false)]), [true, false]);
  });

  test('4 - session window rotated session allows both', () {
    final policy = FrequencyPolicy(maxPerWindow: win(1, 'session'));
    expect(run(policy, [(0, 's1', false), (hour, 's2', false)]), [true, true]);
  });

  test('5 - two per day blocks third within 24h', () {
    final policy = FrequencyPolicy(maxPerWindow: win(2, 'day'));
    expect(
      run(policy, [(0, 's1', false), (hour, 's1', false), (2 * hour, 's1', false)]),
      [true, true, false],
    );
  });

  test('6 - one per day rolls over after 25h', () {
    final policy = FrequencyPolicy(maxPerWindow: win(1, 'day'));
    expect(run(policy, [(0, 's1', false), (25 * hour, 's1', false)]), [true, true]);
  });

  test('7 - weekly and monthly roll over after their window', () {
    final week = FrequencyPolicy(maxPerWindow: win(1, 'week'));
    expect(run(week, [(0, 's1', false), (8 * day, 's1', false)]), [true, true]);
    expect(run(week, [(0, 's1', false), (6 * day, 's1', false)]), [true, false]);
    final month = FrequencyPolicy(maxPerWindow: win(1, 'month'));
    expect(run(month, [(0, 's1', false), (31 * day, 's1', false)]), [true, true]);
    expect(run(month, [(0, 's1', false), (29 * day, 's1', false)]), [true, false]);
  });

  test('8 - stopOn experienceCompleted blocks forever after completion', () {
    const policy = FrequencyPolicy(stopOn: 'experienceCompleted');
    expect(
      run(policy, [(0, 's1', true), (hour, 's1', false), (2 * day, 's2', false)]),
      [true, false, false],
    );
  });

  test('9 - maxTotal plus per-day: per-day wins same day', () {
    final policy = FrequencyPolicy(maxTotal: 5, maxPerWindow: win(1, 'day'));
    expect(run(policy, [(0, 's1', false), (hour, 's1', false)]), [true, false]);
  });

  test('10 - maxTotal ignores session rotation', () {
    const policy = FrequencyPolicy(maxTotal: 2);
    expect(
      run(policy, [(0, 's1', false), (hour, 's2', false), (2 * hour, 's3', false)]),
      [true, true, false],
    );
  });

  test('11 - session window cold start allows both', () {
    final policy = FrequencyPolicy(maxPerWindow: win(1, 'session'));
    expect(run(policy, [(0, 'cold-1', false), (day, 'cold-2', false)]), [true, true]);
  });

  test('12 - blocked attempt does not advance state', () {
    const policy = FrequencyPolicy(maxTotal: 1);
    FrequencyState? state;
    expect(FrequencyEvaluator.evaluate(policy, state, 0, 's1').allow, isTrue);
    state = FrequencyEvaluator.recordShow(policy, state, 0, 's1');
    expect(state.total, 1);
    expect(FrequencyEvaluator.evaluate(policy, state, hour, 's1').allow, isFalse);
    expect(state.total, 1);
  });

  test('recordShow re-anchors time window only on rollover', () {
    final policy = FrequencyPolicy(maxPerWindow: win(5, 'day'));
    var s = FrequencyEvaluator.recordShow(policy, null, 1000, 's1');
    expect(s, const FrequencyState(total: 1, windowCount: 1, windowAnchorAt: 1000));
    s = FrequencyEvaluator.recordShow(policy, s, 1000 + hour, 's1');
    expect(s.total, 2);
    expect(s.windowCount, 2);
    expect(s.windowAnchorAt, 1000);
    s = FrequencyEvaluator.recordShow(policy, s, 1000 + 25 * hour, 's1');
    expect(s.total, 3);
    expect(s.windowCount, 1);
    expect(s.windowAnchorAt, 1000 + 25 * hour);
  });

  test('recordStop is idempotent', () {
    var s = FrequencyEvaluator.recordStop(null, 500);
    expect(s.stoppedAt, 500);
    s = FrequencyEvaluator.recordStop(s, 900);
    expect(s.stoppedAt, 500);
  });

  group('FrequencyPolicy.fromJson', () {
    test('reads camelCase and rejects empty / snake_case', () {
      expect(FrequencyPolicy.fromJson(null), isNull);
      expect(FrequencyPolicy.fromJson(const {}), isNull);
      final p = FrequencyPolicy.fromJson({
        'maxTotal': 3,
        'maxPerWindow': {'count': 2, 'window': 'day'},
        'stopOn': 'experienceCompleted',
      })!;
      expect(p.maxTotal, 3);
      expect(p.maxPerWindow, win(2, 'day'));
      expect(p.stopOn, 'experienceCompleted');
      // Old snake_case payload has no recognised keys → uncapped (null).
      expect(FrequencyPolicy.fromJson(const {'max_total': 3, 'stop_on': 'click'}),
          isNull);
    });
  });
}
