/// Measures how long an experience was on screen — the `dwell_ms` recorded on
/// dismiss/complete analytics events.
///
/// [markViewed] stamps the view time keyed by `cepCampaignId`; [consumeDwellMs]
/// reads the elapsed time and forgets the mark in one step (the typical dismiss
/// path), while [elapsedMs] peeks without forgetting.
///
/// Mirrors Android's `DwellTracker`.
class DwellTracker {
  final int Function() _now;

  /// [now] is injectable for tests; defaults to wall-clock milliseconds.
  DwellTracker({int Function()? now})
      : _now = now ?? (() => DateTime.now().millisecondsSinceEpoch);

  final Map<String, int> _viewedAtMs = {};

  /// Stamps the moment [cepCampaignId] became visible.
  void markViewed(String cepCampaignId) => _viewedAtMs[cepCampaignId] = _now();

  /// Milliseconds since [cepCampaignId] was viewed, or null if never marked.
  int? elapsedMs(String cepCampaignId) {
    final viewedAt = _viewedAtMs[cepCampaignId];
    return viewedAt == null ? null : _now() - viewedAt;
  }

  /// Like [elapsedMs] but also forgets the mark, so the next showing starts
  /// fresh. Returns null if [cepCampaignId] was never marked.
  int? consumeDwellMs(String cepCampaignId) {
    final viewedAt = _viewedAtMs.remove(cepCampaignId);
    return viewedAt == null ? null : _now() - viewedAt;
  }

  /// Forgets every view mark.
  void clear() => _viewedAtMs.clear();
}
