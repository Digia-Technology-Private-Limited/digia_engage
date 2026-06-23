import '../../interfaces/digia_cep_plugin.dart';
import '../../models/cep_trigger_payload.dart';
import '../../models/digia_experience_event.dart';

/// Delivers coarse [DigiaExperienceEvent]s to the registered CEP plugin.
///
/// The CEP channel is a campaign-agnostic protocol — the plugin only
/// understands Impressed/Clicked/Dismissed. The active plugin is read lazily
/// via [_activePlugin] because a plugin may be registered (or replaced) after
/// the SDK initialises; when none is registered the delivery is a clean no-op,
/// so callers fire unconditionally (SRP: this knows only how to reach the CEP).
class CepPluginSink {
  final DigiaCEPPlugin? Function() _activePlugin;

  CepPluginSink(this._activePlugin);

  void deliver(DigiaExperienceEvent event, CEPTriggerPayload payload) {
    _activePlugin()?.notifyEvent(event, payload);
  }
}
