import '../../models/cep_trigger_payload.dart';
import '../../models/digia_experience_event.dart';
import 'engage_event_router.dart';
import 'event_sink.dart';

/// The SDK's single entry point for emitting [DigiaExperienceEvent]s.
///
/// Wraps [EngageEventRouter] with intent-revealing helpers ([toCep], [toDigia],
/// [toAll]) so call sites name *where* and *when* an event goes without
/// touching sink ids. Also owns the first-render impression dedup, which is
/// an emission concern — not widget-tree state — so it lives here rather than
/// on [DigiaOverlayController].
class EngageEventEmitter {
  final EngageEventRouter _router;

  /// `cepCampaignId`s that have already fired a Digia first-render impression.
  /// Outlives any single [DigiaSlot] widget, so revisiting a sticky slot does
  /// not re-impress to Digia. Reset on slot invalidation via [resetImpression].
  final Set<String> _digiaImpressed = <String>{};

  EngageEventEmitter(this._router);

  /// Emits [event] to the CEP plugin only.
  void toCep(DigiaExperienceEvent event, CEPTriggerPayload payload) =>
      _router.dispatch(event, payload, const {EventSinkId.cep});

  /// Emits [event] to Digia's first-party analytics only.
  void toDigia(DigiaExperienceEvent event, CEPTriggerPayload payload) =>
      _router.dispatch(event, payload, const {EventSinkId.digia});

  /// Emits [event] to both destinations.
  void toAll(DigiaExperienceEvent event, CEPTriggerPayload payload) =>
      _router.dispatch(
        event,
        payload,
        const {EventSinkId.digia, EventSinkId.cep},
      );

  /// Emits a Digia-only [ExperienceImpressed] for [payload] the first time its
  /// campaign renders, deduped by `cepCampaignId`. CEP is impressed separately
  /// and instantly at route time.
  void digiaImpressionOnce(CEPTriggerPayload payload) {
    if (!_digiaImpressed.add(payload.cepCampaignId)) return;
    toDigia(const ExperienceImpressed(), payload);
  }

  /// Forgets the impression mark for [cepCampaignId] so a later re-trigger of
  /// the same campaign impresses afresh. Called on slot invalidation.
  void resetImpression(String cepCampaignId) =>
      _digiaImpressed.remove(cepCampaignId);

  /// Forgets every impression mark.
  void clearImpressions() => _digiaImpressed.clear();
}
