import '../models/in_app_payload.dart';

/// Implemented by Digia core internally.
/// Plugin holds a reference to this and calls into it when the CEP fires a campaign.
/// Plugin authors never implement this — they only call it.
abstract class DigiaCEPDelegate {
  /// Called when the CEP has evaluated a campaign and it is ready to render.
  /// Digia routes the payload to nudge or inline surfaces based on command.
  void onCampaignTriggered(InAppPayload payload);

  /// Called when a previously delivered campaign is no longer valid.
  /// Digia dismisses the active nudge or inline payload with matching id.
  void onCampaignInvalidated(String campaignId);
}
