import '../../models/cep_trigger_payload.dart';
import '../../models/digia_experience_event.dart';

/// Stable identity for a delivery destination.
///
/// Used by call sites to target an emission and by [EngageEventRouter] to
/// look up the matching [EngageEventSink]. Add a value here only when a new
/// destination is introduced.
enum EventSinkId {
  /// Digia's first-party analytics backend (DigiaAnalyticsService).
  digia,

  /// The registered CEP plugin — CleverTap / MoEngage / WebEngage.
  cep,
}

/// One delivery destination for [DigiaExperienceEvent]s.
///
/// SRP: an implementation knows how to *deliver* an event, nothing else — not
/// when it fires, not which other destinations exist.
///
/// LSP: every destination is substitutable behind this contract, so
/// [EngageEventRouter] can treat them uniformly with no `is`-checks.
///
/// ISP: the contract is intentionally narrow. Contrast `DigiaCEPPlugin`, which
/// also requires screen-tracking, health checks and teardown — none of which a
/// delivery destination should be forced to implement.
abstract interface class EngageEventSink {
  /// Identity used for targeted routing.
  EventSinkId get id;

  /// Delivers [event] for the campaign described by [payload].
  ///
  /// Implementations own their own translation and transport, and are
  /// responsible for gracefully no-op'ing when they cannot currently act
  /// (e.g. the CEP sink when no plugin is registered). Eligibility is the
  /// sink's concern, never the caller's.
  Future<void> deliver(DigiaExperienceEvent event, CEPTriggerPayload payload);
}
