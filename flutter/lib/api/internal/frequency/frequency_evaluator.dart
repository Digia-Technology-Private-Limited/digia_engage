import 'frequency_policy.dart';

/// Pure eligibility check — no side effects. Dart port of the React Native
/// SDK's `frequencyEvaluator.ts` (`evaluate`).
///
/// Semantics for `max_per_window { count, window }`:
///   - [FrequencyWindow.count] shows are allowed, measured from `first_shown_at`.
///   - Once the window duration has elapsed since `first_shown_at`, the campaign
///     is permanently blocked ([FrequencySkipReason.window]).
///   - Once `shown_count >= count`, it is blocked ([FrequencySkipReason.maxTotal]).
///   - A `session` window has no wall-clock duration; the caller scopes its state
///     to the app process (in-memory), and the same count check applies.
class FrequencyEvaluator {
  const FrequencyEvaluator._();

  static FrequencyEvalResult evaluate(
    FrequencyPolicy policy,
    FrequencyState? state,
    int nowMs,
  ) {
    if (state == null) return FrequencyEvalResult.allowed;

    if (state.stoppedAt != null) {
      return const FrequencyEvalResult(
          allow: false, reason: FrequencySkipReason.stopped);
    }

    final maxTotal = policy.maxTotal;
    if (maxTotal != null && state.shownCount >= maxTotal) {
      return const FrequencyEvalResult(
          allow: false, reason: FrequencySkipReason.maxTotal);
    }

    final window = policy.maxPerWindow;
    if (window != null) {
      final windowMs = window.window.durationMs;
      final firstShownAt = state.firstShownAt;
      if (windowMs != null && firstShownAt != null) {
        if (nowMs - firstShownAt > windowMs) {
          return const FrequencyEvalResult(
              allow: false, reason: FrequencySkipReason.window);
        }
      }

      if (state.shownCount >= window.count) {
        return const FrequencyEvalResult(
            allow: false, reason: FrequencySkipReason.maxTotal);
      }
    }

    return FrequencyEvalResult.allowed;
  }
}
