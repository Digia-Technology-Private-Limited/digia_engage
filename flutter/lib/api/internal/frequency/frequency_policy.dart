/// Typed frequency-capping policy + state models.
///
/// Dart port of the React Native SDK's frequency types (`types.ts`). A campaign
/// may carry a server-configured [FrequencyPolicy] that limits how often it is
/// allowed to show; the SDK tracks a [FrequencyState] per campaign and the
/// `FrequencyEvaluator` decides eligibility. The on-the-wire field names are
/// snake_case (matching the RN SDK and the backend), so the JSON keys here stay
/// snake_case for cross-platform parity.
library;

/// How long a `max_per_window` budget lasts, measured from the first show.
enum FrequencyWindowUnit {
  session,
  day,
  week,
  month;

  /// Window length in milliseconds, or `null` for [session] (which is bounded
  /// by the app process, not wall-clock time, and so is checked in-memory).
  int? get durationMs {
    const day = 86400000;
    switch (this) {
      case FrequencyWindowUnit.session:
        return null;
      case FrequencyWindowUnit.day:
        return day;
      case FrequencyWindowUnit.week:
        return 7 * day;
      case FrequencyWindowUnit.month:
        return 30 * day;
    }
  }

  static FrequencyWindowUnit? fromWire(Object? value) {
    switch (value) {
      case 'session':
        return FrequencyWindowUnit.session;
      case 'day':
        return FrequencyWindowUnit.day;
      case 'week':
        return FrequencyWindowUnit.week;
      case 'month':
        return FrequencyWindowUnit.month;
      default:
        return null;
    }
  }
}

/// The interaction that, per `stop_on`, permanently silences a campaign.
enum FrequencyStopOn {
  click,
  dismiss,
  anyAction;

  static FrequencyStopOn? fromWire(Object? value) {
    switch (value) {
      case 'click':
        return FrequencyStopOn.click;
      case 'dismiss':
        return FrequencyStopOn.dismiss;
      case 'any_action':
        return FrequencyStopOn.anyAction;
      default:
        return null;
    }
  }
}

/// A user interaction reported to `FrequencyController.applyStopOn`.
enum FrequencyInteraction {
  click('click'),
  dismiss('dismiss');

  const FrequencyInteraction(this.wire);

  /// The value persisted as `stopped_reason`.
  final String wire;
}

/// Why an evaluation blocked a campaign (`null` when allowed).
enum FrequencySkipReason { maxTotal, window, stopped }

/// `{ count, window }` — at most [count] shows within one [window].
class FrequencyWindow {
  final int count;
  final FrequencyWindowUnit window;

  const FrequencyWindow({required this.count, required this.window});
}

/// A campaign's server-configured frequency policy. Any field may be absent;
/// [hasPolicy] is `false` when none of the limiting fields are set, in which
/// case the campaign is never capped.
class FrequencyPolicy {
  final int? maxTotal;
  final FrequencyWindow? maxPerWindow;
  final FrequencyStopOn? stopOn;

  /// Reserved — not evaluated in v1 (mirrors RN's `min_gap_ms`).
  final int? minGapMs;

  const FrequencyPolicy({
    this.maxTotal,
    this.maxPerWindow,
    this.stopOn,
    this.minGapMs,
  });

  /// Whether this policy imposes any limit. Mirrors RN's `hasPolicy`.
  bool get hasPolicy =>
      maxTotal != null || maxPerWindow != null || stopOn != null;

  /// Whether the per-window budget is bounded by the app session (in-memory)
  /// rather than wall-clock time. Mirrors RN's `isSessionPolicy`.
  bool get isSession => maxPerWindow?.window == FrequencyWindowUnit.session;

  /// Parses the campaign-level `frequency` block, or `null` when absent.
  static FrequencyPolicy? fromJson(Map<String, dynamic>? json) {
    if (json == null) return null;

    final maxTotal = json['max_total'];
    final mpwRaw = json['max_per_window'];
    final minGap = json['min_gap_ms'];

    FrequencyWindow? maxPerWindow;
    if (mpwRaw is Map) {
      final count = mpwRaw['count'];
      final window = FrequencyWindowUnit.fromWire(mpwRaw['window']);
      if (count is num && window != null) {
        maxPerWindow = FrequencyWindow(count: count.toInt(), window: window);
      }
    }

    return FrequencyPolicy(
      maxTotal: maxTotal is num ? maxTotal.toInt() : null,
      maxPerWindow: maxPerWindow,
      stopOn: FrequencyStopOn.fromWire(json['stop_on']),
      minGapMs: minGap is num ? minGap.toInt() : null,
    );
  }
}

/// Per-campaign tracking state. Persisted as snake_case JSON for non-session
/// policies; held in-memory for session policies. Mirrors RN's `FrequencyState`.
class FrequencyState {
  final int shownCount;

  /// ms timestamp — set on the first impression; anchors the window check.
  final int? firstShownAt;

  /// ms timestamp — reserved for `min_gap_ms`.
  final int? lastShownAt;

  final int? stoppedAt;
  final String? stoppedReason;

  const FrequencyState({
    this.shownCount = 0,
    this.firstShownAt,
    this.lastShownAt,
    this.stoppedAt,
    this.stoppedReason,
  });

  Map<String, dynamic> toJson() => {
        'shown_count': shownCount,
        'first_shown_at': firstShownAt,
        'last_shown_at': lastShownAt,
        'stopped_at': stoppedAt,
        'stopped_reason': stoppedReason,
      };

  static FrequencyState fromJson(Map<String, dynamic> json) {
    final shown = json['shown_count'];
    final first = json['first_shown_at'];
    final last = json['last_shown_at'];
    final stopped = json['stopped_at'];
    final reason = json['stopped_reason'];
    return FrequencyState(
      shownCount: shown is num ? shown.toInt() : 0,
      firstShownAt: first is num ? first.toInt() : null,
      lastShownAt: last is num ? last.toInt() : null,
      stoppedAt: stopped is num ? stopped.toInt() : null,
      stoppedReason: reason is String ? reason : null,
    );
  }
}

/// The result of evaluating a policy against a state. Mirrors RN's
/// `FrequencyEvalResult`.
class FrequencyEvalResult {
  final bool allow;
  final FrequencySkipReason? reason;

  const FrequencyEvalResult({required this.allow, required this.reason});

  static const allowed = FrequencyEvalResult(allow: true, reason: null);
}
