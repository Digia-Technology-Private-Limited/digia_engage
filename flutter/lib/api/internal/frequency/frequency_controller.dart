import '../campaign/campaign_model.dart';
import 'frequency_evaluator.dart';
import 'frequency_policy.dart';
import 'frequency_store.dart';

/// Ties the [FrequencyStore] and [FrequencyEvaluator] to campaigns. Mirrors the
/// frequency logic that lives directly on the React Native SDK's `Digia` class
/// (`evaluate` gate, `_bumpFrequencyImpression`, `_applyStopOn`), pulled into one
/// object so both [DigiaInstance] and the guide manager can share it.
class FrequencyController {
  final FrequencyStore _store;

  const FrequencyController(this._store);

  /// Evaluates whether [campaign] may show now. Returns [FrequencyEvalResult.allowed]
  /// for campaigns with no policy; the caller logs the [FrequencyEvalResult.reason].
  FrequencyEvalResult evaluate(CampaignModel campaign, int nowMs) {
    final policy = campaign.frequency;
    if (policy == null || !policy.hasPolicy) return FrequencyEvalResult.allowed;
    final state = _store.get(campaign.campaignKey, isSession: policy.isSession);
    return FrequencyEvaluator.evaluate(policy, state, nowMs);
  }

  /// Records one impression — increments `shown_count` and stamps `first_shown_at`
  /// on the first show. No-op for campaigns without a policy.
  void recordImpression(CampaignModel campaign, int nowMs) {
    final policy = campaign.frequency;
    if (policy == null || !policy.hasPolicy) return;
    final isSession = policy.isSession;
    final prev = _store.get(campaign.campaignKey, isSession: isSession);
    final next = FrequencyState(
      shownCount: (prev?.shownCount ?? 0) + 1,
      firstShownAt: prev?.firstShownAt ?? nowMs,
      lastShownAt: nowMs,
      stoppedAt: prev?.stoppedAt,
      stoppedReason: prev?.stoppedReason,
    );
    _store.set(campaign.campaignKey, next, isSession: isSession);
  }

  /// Applies the `stop_on` rule for an [interaction]. When the policy's `stop_on`
  /// matches (or is `any_action`), the campaign is permanently silenced. Idempotent
  /// — a campaign already stopped is left untouched.
  void applyStopOn(
    CampaignModel campaign,
    FrequencyInteraction interaction,
    int nowMs,
  ) {
    final policy = campaign.frequency;
    final stopOn = policy?.stopOn;
    if (stopOn == null) return;
    final matches = stopOn == FrequencyStopOn.anyAction ||
        (stopOn == FrequencyStopOn.click &&
            interaction == FrequencyInteraction.click) ||
        (stopOn == FrequencyStopOn.dismiss &&
            interaction == FrequencyInteraction.dismiss);
    if (!matches) return;

    final isSession = policy!.isSession;
    final prev = _store.get(campaign.campaignKey, isSession: isSession);
    if (prev?.stoppedAt != null) return;
    final next = FrequencyState(
      shownCount: prev?.shownCount ?? 0,
      firstShownAt: prev?.firstShownAt,
      lastShownAt: prev?.lastShownAt,
      stoppedAt: nowMs,
      stoppedReason: interaction.wire,
    );
    _store.set(campaign.campaignKey, next, isSession: isSession);
  }
}
