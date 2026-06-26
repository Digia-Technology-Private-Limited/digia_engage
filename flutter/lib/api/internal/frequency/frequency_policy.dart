/// Typed frequency-capping policy + state models.
///
/// Dart port of the cross-platform frequency core (Android
/// `FrequencyEvaluator.kt` / `FrequencyManager.kt`, RN, iOS). The wire format is
/// camelCase — `maxTotal`, `maxPerWindow { count, window }`, `stopOn` — and a
/// campaign carries it under a top-level `frequency` object. Old snake_case
/// payloads have no recognised keys and parse to a no-constraint (uncapped)
/// policy.
library;

/// `{ count, window }` — at most [count] shows within one [window]. [window] is
/// one of `session` / `day` / `week` / `month` (kept as the raw wire string so
/// the golden matrix matches the other platforms exactly).
class FrequencyWindow {
  final int count;
  final String window;

  const FrequencyWindow({required this.count, required this.window});

  @override
  bool operator ==(Object other) =>
      other is FrequencyWindow &&
      other.count == count &&
      other.window == window;

  @override
  int get hashCode => Object.hash(count, window);
}

/// A campaign's server-configured frequency policy. Any field may be absent;
/// [hasConstraint] is `false` when none are set, in which case the campaign is
/// never capped.
class FrequencyPolicy {
  final int? maxTotal;
  final FrequencyWindow? maxPerWindow;

  /// The only recognised value is `experienceCompleted` — permanently stop the
  /// campaign once it reports a "Digia Experience Completed".
  final String? stopOn;

  const FrequencyPolicy({
    this.maxTotal,
    this.maxPerWindow,
    this.stopOn,
  });

  /// True when the policy carries at least one active constraint.
  bool get hasConstraint =>
      maxTotal != null || maxPerWindow != null || stopOn != null;

  @override
  String toString() {
    final window = maxPerWindow;
    final parts = <String>[
      if (maxTotal != null) 'maxTotal=$maxTotal',
      if (window != null) 'maxPerWindow=${window.count}/${window.window}',
      if (stopOn != null) 'stopOn=$stopOn',
    ];
    return 'FrequencyPolicy(${parts.join(', ')})';
  }

  /// Parses the campaign-level `frequency` block, or `null` when it is absent or
  /// carries no recognised constraint (mirrors Android's `parsePolicy`).
  static FrequencyPolicy? fromJson(Map<String, dynamic>? json) {
    if (json == null) return null;

    final maxTotalRaw = json['maxTotal'];
    final maxTotal = maxTotalRaw is num ? maxTotalRaw.toInt() : null;

    FrequencyWindow? maxPerWindow;
    final windowRaw = json['maxPerWindow'];
    if (windowRaw is Map) {
      final countRaw = windowRaw['count'];
      final windowName = windowRaw['window'];
      if (countRaw is num &&
          windowName is String &&
          windowName.trim().isNotEmpty) {
        maxPerWindow =
            FrequencyWindow(count: countRaw.toInt(), window: windowName);
      }
    }

    final stopOnRaw = json['stopOn'];
    final stopOn =
        stopOnRaw is String && stopOnRaw.trim().isNotEmpty ? stopOnRaw : null;

    final policy = FrequencyPolicy(
      maxTotal: maxTotal,
      maxPerWindow: maxPerWindow,
      stopOn: stopOn,
    );
    return policy.hasConstraint ? policy : null;
  }
}

/// Persisted per-campaign capping state. [total] (lifetime) and [windowCount]
/// (current window) are tracked independently so a per-window cap never
/// double-counts against [FrequencyPolicy.maxTotal]. Mirrors Android's
/// `FrequencyState`.
class FrequencyState {
  final int total;
  final int windowCount;

  /// ms timestamp anchoring the current time-based window (null for session /
  /// total-only policies).
  final int? windowAnchorAt;

  /// The session that owns the current `session`-window count (null otherwise).
  final String? sessionId;

  /// ms timestamp of the permanent stop (null while the campaign is still live).
  final int? stoppedAt;

  const FrequencyState({
    this.total = 0,
    this.windowCount = 0,
    this.windowAnchorAt,
    this.sessionId,
    this.stoppedAt,
  });

  FrequencyState copyWith({
    int? total,
    int? windowCount,
    int? windowAnchorAt,
    String? sessionId,
    int? stoppedAt,
  }) =>
      FrequencyState(
        total: total ?? this.total,
        windowCount: windowCount ?? this.windowCount,
        windowAnchorAt: windowAnchorAt ?? this.windowAnchorAt,
        sessionId: sessionId ?? this.sessionId,
        stoppedAt: stoppedAt ?? this.stoppedAt,
      );

  Map<String, dynamic> toJson() => {
        'total': total,
        'windowCount': windowCount,
        if (windowAnchorAt != null) 'windowAnchorAt': windowAnchorAt,
        if (sessionId != null) 'sessionId': sessionId,
        if (stoppedAt != null) 'stoppedAt': stoppedAt,
      };

  static FrequencyState fromJson(Map<String, dynamic> json) {
    final total = json['total'];
    final windowCount = json['windowCount'];
    final windowAnchorAt = json['windowAnchorAt'];
    final sessionId = json['sessionId'];
    final stoppedAt = json['stoppedAt'];
    return FrequencyState(
      total: total is num ? total.toInt() : 0,
      windowCount: windowCount is num ? windowCount.toInt() : 0,
      windowAnchorAt: windowAnchorAt is num ? windowAnchorAt.toInt() : null,
      sessionId: sessionId is String ? sessionId : null,
      stoppedAt: stoppedAt is num ? stoppedAt.toInt() : null,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is FrequencyState &&
      other.total == total &&
      other.windowCount == windowCount &&
      other.windowAnchorAt == windowAnchorAt &&
      other.sessionId == sessionId &&
      other.stoppedAt == stoppedAt;

  @override
  int get hashCode =>
      Object.hash(total, windowCount, windowAnchorAt, sessionId, stoppedAt);
}

/// Why an evaluation blocked a campaign (`null` when allowed).
enum FrequencySkipReason { maxTotal, window, stopped }

/// The result of evaluating a policy against a state. Mirrors Android's
/// `FrequencyEvalResult`.
class FrequencyEvalResult {
  final bool allow;
  final FrequencySkipReason? reason;

  const FrequencyEvalResult(this.allow, [this.reason]);

  static const allowed = FrequencyEvalResult(true);
}
