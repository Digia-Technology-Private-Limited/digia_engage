import '../models/cep_trigger_payload.dart';

/// Implemented by Digia core internally.
/// Plugin holds a reference to this and calls into it when the CEP fires a campaign.
/// Plugin authors never implement this — they only call it.
abstract class DigiaCEPDelegate {
  /// Called when the CEP has evaluated a campaign and it is ready to render.
  /// Digia routes the payload to nudge or inline surfaces based on campaignKey.
  ///
  /// Returns whether Digia accepted the campaign for rendering. `true` means an
  /// experience was (or will be) shown and will emit a terminal lifecycle event
  /// ([ExperienceDismissed]/[ExperienceCompleted]) when it ends. `false` means
  /// the campaign was dropped — SDK not ready, unknown campaign key, unsupported
  /// type, or another experience already on screen — and no terminal event will
  /// fire. A CEP plugin holding a single in-app slot (e.g. CleverTap custom
  /// templates) must release it immediately when this returns `false`.
  bool onCampaignTriggered(CEPTriggerPayload payload);

  /// Called when a previously delivered campaign is no longer valid.
  /// Digia dismisses the active nudge or inline payload with matching id.
  void onCampaignInvalidated(String campaignId);
}
