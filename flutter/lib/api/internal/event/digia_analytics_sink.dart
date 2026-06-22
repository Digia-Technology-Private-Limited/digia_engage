import '../../../src/analytics/analytics_service.dart';
import '../../models/cep_trigger_payload.dart';
import '../campaign/campaign_store.dart';
import 'engage_analytics_event.dart';

/// Delivers rich [EngageAnalyticsEvent]s to Digia's first-party analytics
/// backend.
///
/// Resolves the campaign from [CampaignStore] by `campaignKey` for attribution
/// context (campaign id/type live on the campaign, not the trigger payload),
/// then hands the event to [DigiaAnalyticsService.capture], which merges
/// [EngageAnalyticsEvent.properties] with the static context.
class DigiaAnalyticsSink {
  final DigiaAnalyticsService _analytics;
  final CampaignStore _store;

  DigiaAnalyticsSink(this._analytics, this._store);

  void deliver(EngageAnalyticsEvent event, CEPTriggerPayload payload) {
    final campaign = _store.find(payload.campaignKey);
    _analytics.capture(
      event,
      payload,
      campaignType: campaign?.campaignType,
      campaignId: campaign?.id,
    );
  }
}
