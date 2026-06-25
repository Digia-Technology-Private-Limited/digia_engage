import 'frequency_policy.dart';

/// Frequency-capping core — pure, platform-free logic. Dart port of Android's
/// `FrequencyEvaluator`, sharing the identical golden matrix across platforms.
/// `now` and `sessionId` are injected so the logic is fully deterministic and
/// unit-testable.
class FrequencyEvaluator {
  const FrequencyEvaluator._();

  static const int _dayMs = 86400000;
  static const Map<String, int> _windowMs = {
    'day': _dayMs,
    'week': 7 * _dayMs,
    'month': 30 * _dayMs,
  };

  /// Pure eligibility check. Blocks if ANY active constraint is exceeded:
  /// permanent stop, lifetime total, or the current window.
  static FrequencyEvalResult evaluate(
    FrequencyPolicy policy,
    FrequencyState? state,
    int now,
    String? sessionId,
  ) {
    if (state == null) return FrequencyEvalResult.allowed;
    if (state.stoppedAt != null) {
      return const FrequencyEvalResult(false, FrequencySkipReason.stopped);
    }
    final maxTotal = policy.maxTotal;
    if (maxTotal != null && state.total >= maxTotal) {
      return const FrequencyEvalResult(false, FrequencySkipReason.maxTotal);
    }
    final w = policy.maxPerWindow;
    if (w != null) {
      final effective =
          _isWindowExpired(w, state, now, sessionId) ? 0 : state.windowCount;
      if (effective >= w.count) {
        return const FrequencyEvalResult(false, FrequencySkipReason.window);
      }
    }
    return FrequencyEvalResult.allowed;
  }

  /// Record one show on "Digia Experience Viewed". Bumps the lifetime total and
  /// the window count, re-anchoring the window when it has rolled over.
  static FrequencyState recordShow(
    FrequencyPolicy policy,
    FrequencyState? state,
    int now,
    String? sessionId,
  ) {
    final prev = state ?? const FrequencyState();
    final w = policy.maxPerWindow;
    final fresh = w == null || _isWindowExpired(w, prev, now, sessionId);
    final isTimeWindow = w != null && w.window != 'session';
    final isSessionWindow = w != null && w.window == 'session';
    return FrequencyState(
      total: prev.total + 1,
      windowCount: fresh ? 1 : prev.windowCount + 1,
      windowAnchorAt: isTimeWindow
          ? (fresh ? now : prev.windowAnchorAt)
          : prev.windowAnchorAt,
      sessionId: isSessionWindow ? sessionId : prev.sessionId,
      stoppedAt: prev.stoppedAt,
    );
  }

  /// Permanently stop the campaign on "Digia Experience Completed" when the
  /// policy opted into stopOn. Idempotent: the first stop timestamp wins.
  static FrequencyState recordStop(FrequencyState? state, int now) {
    final prev = state ?? const FrequencyState();
    return prev.stoppedAt != null ? prev : prev.copyWith(stoppedAt: now);
  }

  static bool _isWindowExpired(
    FrequencyWindow w,
    FrequencyState state,
    int now,
    String? sessionId,
  ) {
    if (w.window == 'session') return state.sessionId != sessionId;
    final ms = _windowMs[w.window];
    if (ms == null) return true;
    final anchor = state.windowAnchorAt;
    if (anchor == null) return true;
    return now - anchor >= ms;
  }
}
