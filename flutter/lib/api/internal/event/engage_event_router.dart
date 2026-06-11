import '../../models/cep_trigger_payload.dart';
import '../../models/digia_experience_event.dart';
import 'event_sink.dart';

/// Routes [DigiaExperienceEvent]s to a selected subset of registered sinks.
///
/// Acts as a Mediator between emitters and sinks: emitters never reference a
/// sink, sinks never reference an emitter. The router depends only on the
/// [EngageEventSink] abstraction (DIP) — it knows nothing about analytics
/// transport or CEP plugins, so adding a destination is a new sink plus one
/// registration entry, with no change here (OCP).
class EngageEventRouter {
  final Map<EventSinkId, EngageEventSink> _sinks;

  EngageEventRouter(List<EngageEventSink> sinks)
      : _sinks = {for (final sink in sinks) sink.id: sink};

  /// Delivers [event] to each sink named in [targets].
  ///
  /// Unknown targets (no sink registered for the id) are skipped silently.
  /// Deliveries are awaited in order so a `flushOnCapture` event settles
  /// before the call returns.
  Future<void> dispatch(
    DigiaExperienceEvent event,
    CEPTriggerPayload payload,
    Set<EventSinkId> targets,
  ) async {
    for (final id in targets) {
      await _sinks[id]?.deliver(event, payload);
    }
  }
}
