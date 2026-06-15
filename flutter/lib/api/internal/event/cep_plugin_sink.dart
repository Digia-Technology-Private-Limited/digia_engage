import '../../interfaces/digia_cep_plugin.dart';
import '../../models/cep_trigger_payload.dart';
import '../../models/digia_experience_event.dart';
import 'event_sink.dart';

/// Delivers experience events to the registered CEP plugin.
///
/// The active plugin is read lazily via [_activePlugin] because a plugin may
/// be registered (or replaced) after the SDK initialises. When no plugin is
/// registered the delivery is a clean no-op — eligibility is the sink's
/// concern, so call sites can target [EventSinkId.cep] unconditionally.
class CepPluginSink implements EngageEventSink {
  final DigiaCEPPlugin? Function() _activePlugin;

  CepPluginSink(this._activePlugin);

  @override
  EventSinkId get id => EventSinkId.cep;

  @override
  Future<void> deliver(
    DigiaExperienceEvent event,
    CEPTriggerPayload payload,
  ) async {
    _activePlugin()?.notifyEvent(event, payload);
  }
}
