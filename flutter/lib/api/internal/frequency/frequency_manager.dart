import 'dart:async';
import 'dart:convert';

import '../../../src/preferences_store.dart';
import '../campaign/campaign_model.dart';
import 'frequency_evaluator.dart';
import 'frequency_policy.dart';

/// Applies frequency capping for natively-rendered campaigns (nudge, survey, and
/// guide). Dart port of Android's `FrequencyManager`: wraps the pure
/// [FrequencyEvaluator] with persistence (one [FrequencyState] per campaignKey)
/// and pulls the current session id so `session` windows reset exactly when the
/// session rotates (cold start / idle timeout / user change).
///
/// Persists via [PreferencesStore] (SharedPreferences), whose reads are
/// synchronous once initialized — so the route-time gate ([isAllowed]) stays
/// synchronous. Writes are fire-and-forget; SharedPreferences updates its
/// in-memory cache synchronously, so a later read sees the new value at once.
///
/// [sessionIdProvider] returns the current session id; [clock] is injectable for
/// tests and defaults to wall-clock.
class FrequencyManager {
  static const String _keyPrefix = 'freq.';

  final PreferencesStore _store;
  final String? Function() _sessionIdProvider;
  final int Function() _clock;

  FrequencyManager(
    this._store,
    this._sessionIdProvider, {
    int Function()? clock,
  }) : _clock = clock ?? (() => DateTime.now().millisecondsSinceEpoch);

  /// Gate: whether [campaign] may show now. Capping-free policies always pass.
  bool isAllowed(CampaignModel campaign) => blockReason(campaign) == null;

  /// The reason [campaign] is currently blocked, or `null` when it may show.
  /// `isAllowed` delegates to this so the gate can log *why* it dropped — one
  /// evaluation, no duplicated logic (mirrors Android's `blockReason`).
  FrequencySkipReason? blockReason(CampaignModel campaign) {
    final policy = campaign.frequency;
    if (policy == null || !policy.hasConstraint) return null;
    final result = FrequencyEvaluator.evaluate(
      policy,
      _load(campaign.campaignKey),
      _clock(),
      _sessionIdProvider(),
    );
    return result.allow ? null : result.reason;
  }

  /// Record one show on "Digia Experience Viewed".
  void recordShow(CampaignModel campaign) {
    final policy = campaign.frequency;
    if (policy == null || !policy.hasConstraint) return;
    final next = FrequencyEvaluator.recordShow(
      policy,
      _load(campaign.campaignKey),
      _clock(),
      _sessionIdProvider(),
    );
    _save(campaign.campaignKey, next);
  }

  /// Apply the permanent stop on "Digia Experience Completed" (survey + guide),
  /// only when the policy opted into `stopOn: experienceCompleted`.
  void recordCompleted(CampaignModel campaign) {
    if (campaign.frequency?.stopOn != stopOnExperienceCompleted) return;
    final prev = _load(campaign.campaignKey);
    if (prev?.stoppedAt != null) return;
    _save(campaign.campaignKey, FrequencyEvaluator.recordStop(prev, _clock()));
  }

  // ── Persistence ─────────────────────────────────────────────────────────────

  String _keyFor(String campaignKey) => '$_keyPrefix$campaignKey';

  FrequencyState? _load(String campaignKey) {
    final raw = _store.read<String>(_keyFor(campaignKey));
    if (raw == null) return null;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map) {
        return FrequencyState.fromJson(decoded.cast<String, dynamic>());
      }
    } catch (_) {
      // A corrupt record reads as "no state".
    }
    return null;
  }

  void _save(String campaignKey, FrequencyState state) {
    unawaited(_store.write(_keyFor(campaignKey), jsonEncode(state.toJson())));
  }

  /// The only recognised `stopOn` value — permanently stop on completion.
  static const String stopOnExperienceCompleted = 'experienceCompleted';
}
