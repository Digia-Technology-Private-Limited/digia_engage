import '../models/in_app_payload.dart';

/// Implemented by Digia core internally.
/// Plugin holds a reference to this and calls into it when the CEP fires a campaign.
/// Plugin authors never implement this — they only call it.
abstract class DigiaCEPDelegate {
  /// Called when the CEP has evaluated a campaign and it is ready to render.
  /// Digia will route the payload to DigiaHost for display.
  void onExperienceReady(InAppPayload payload);

  /// Called when a previously delivered payload is no longer valid.
  /// Digia will dismiss the experience if it is currently visible.
  void onExperienceInvalidated(String payloadId);
}
