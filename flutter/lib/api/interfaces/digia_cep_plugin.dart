import '../models/diagnostic_report.dart';
import '../models/digia_experience_event.dart';
import '../models/in_app_payload.dart';
import 'digia_cep_delegate.dart';

/// Implemented by each CEP plugin package.
/// Digia core calls into this. Plugin authors implement this.
///
/// Each CEP plugin (digia_clevertap, digia_moengage, digia_webengage) is a
/// separate installable package. The core Digia SDK depends only on this
/// abstraction — never on any specific CEP SDK.
abstract class DigiaCEPPlugin {
  /// Unique identifier for this plugin.
  /// Used in logs and diagnostics.
  /// Convention: lowercase CEP name — "clevertap", "moengage", "webengage"
  String get identifier;

  /// Called by Digia immediately after Digia.register(plugin).
  ///
  /// Plugin must:
  /// 1. Store the delegate (clear any previously held reference first)
  /// 2. Register for the CEP's in-app callback
  ///    (CleverTap: setTemplatePresenterCallback)
  ///    (MoEngage:  getSelfHandledInAppMessage)
  /// 3. Begin listening for payloads
  void setup(DigiaCEPDelegate delegate);

  /// Called when Digia.setCurrentScreen() is invoked by the app developer.
  ///
  /// Plugin must forward to the CEP's own screen tracking API:
  ///    CleverTap: recordScreenView(name)
  ///    MoEngage:  trackScreen(name)
  ///    WebEngage: webengage.screen(name)
  void forwardScreen(String name);

  /// Called by Digia when a rendered experience emits a lifecycle event.
  ///
  /// Plugin must translate and forward to the CEP's analytics API:
  ///    CleverTap: syncTemplate (viewed/clicked) / dismissTemplate
  ///    MoEngage:  trackSelfHandledImpression / etc.
  ///
  /// payload.cepContext carries CEP-specific identifiers the plugin
  /// wrote during _mapToInAppPayload() — use them here for correlation.
  void notifyEvent(DigiaExperienceEvent event, InAppPayload payload);

  /// Returns plugin health diagnostics for runtime checks.
  DiagnosticReport healthCheck();

  /// Called when Digia is shutting down or a new plugin is being registered
  /// in place of this one.
  ///
  /// Plugin must:
  /// 1. Deregister all CEP callbacks
  /// 2. Clear the delegate reference
  ///
  /// Failure to implement teardown correctly causes dangling callbacks
  /// that fire into a dead delegate after plugin replacement.
  void teardown();
}
