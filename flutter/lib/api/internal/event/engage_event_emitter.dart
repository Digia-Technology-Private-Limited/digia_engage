import '../../models/cep_trigger_payload.dart';
import '../../models/digia_experience_event.dart';
import 'engage_event_router.dart';
import 'event_sink.dart';

/// Resolves a [payload] to its campaign attribution and forwards a fully-named
/// matrix event (with caller-assembled snake_case [properties]) to Digia's
/// first-party analytics. Injected at the composition root so the emitter stays
/// decoupled from [DigiaAnalyticsService] and [CampaignStore].
typedef AnalyticsDispatch = void Function(
  String eventName,
  CEPTriggerPayload payload, {
  Map<String, dynamic> properties,
  bool flush,
});

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

  /// Forwards rich matrix events to Digia analytics. Null in lightweight test
  /// setups, in which case [analytics] is a no-op.
  final AnalyticsDispatch? _analytics;

  EngageEventEmitter(this._router, {AnalyticsDispatch? analytics})
      : _analytics = analytics;

  /// Emits a fully-named Digia Engage matrix event ([eventName]) with
  /// caller-assembled [properties] to first-party analytics only. CEP plugins
  /// never see these granular events. Pass [flush] for terminal events
  /// (Experience Dismissed / Completed) that must dispatch immediately.
  void analytics(
    String eventName,
    CEPTriggerPayload payload, {
    Map<String, dynamic> properties = const {},
    bool flush = false,
  }) =>
      _analytics?.call(eventName, payload, properties: properties, flush: flush);

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

  /// Emits a Digia-only first-render impression for [payload], deduped by
  /// `cepCampaignId`. CEP is impressed separately and instantly at route time.
  ///
  /// With no [eventName] this fires the coarse [ExperienceImpressed] (legacy
  /// path). When [eventName] is given it instead emits that rich matrix event
  /// (e.g. `Digia Experience Viewed`) with caller-assembled [properties] — used
  /// by inline slots so first-party analytics get full container context while
  /// keeping the once-per-campaign dedup.
  void digiaImpressionOnce(
    CEPTriggerPayload payload, {
    String? eventName,
    Map<String, dynamic> properties = const {},
  }) {
    if (!_digiaImpressed.add(payload.cepCampaignId)) return;
    if (eventName != null) {
      analytics(eventName, payload, properties: properties);
    } else {
      toDigia(const ExperienceImpressed(), payload);
    }
  }

  /// Forgets the impression mark for [cepCampaignId] so a later re-trigger of
  /// the same campaign impresses afresh. Called on slot invalidation.
  void resetImpression(String cepCampaignId) =>
      _digiaImpressed.remove(cepCampaignId);

  /// Forgets every impression mark.
  void clearImpressions() => _digiaImpressed.clear();
}
