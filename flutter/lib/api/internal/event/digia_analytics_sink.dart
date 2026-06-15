import '../../../src/analytics/analytics_service.dart';
import '../../models/cep_trigger_payload.dart';
import '../../models/digia_experience_event.dart';
import '../campaign/campaign_store.dart';
import 'event_sink.dart';

/// Delivers experience events to Digia's first-party analytics backend.
///
/// Resolves the campaign from [CampaignStore] for attribution context, then
/// hands the event to [DigiaAnalyticsService]. Honours the event's
/// `flushOnCapture` flag.
class DigiaAnalyticsSink implements EngageEventSink {
  final DigiaAnalyticsService _analytics;
  final CampaignStore _store;

  DigiaAnalyticsSink(this._analytics, this._store);

  @override
  EventSinkId get id => EventSinkId.digia;

  @override
  Future<void> deliver(
    DigiaExperienceEvent event,
    CEPTriggerPayload payload,
  ) async {
    final campaign = _store.find(payload.campaignKey);
    await _analytics.captureExperienceEvent(
      event,
      payload,
      campaignType: campaign?.campaignType,
      campaignId: campaign?.id,
    );
    if (event.flushOnCapture) {
      await _analytics.flush();
    }
  }
}
