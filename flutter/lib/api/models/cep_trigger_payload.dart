/// The translation contract between a CEP plugin and Digia's rendering engine.
///
/// Plugin authors map their CEP's native callback into this struct.
/// Digia core never imports CleverTap, MoEngage, or WebEngage types.
class CEPTriggerPayload {
  // --- CEP Context ---

  /// The CEP's own identifier for this campaign instance.
  /// Opaque to Digia Core — passed through for analytics correlation.
  final String cepCampaignId;

  /// Any additional metadata the CEP passes through (e.g. UTM params,
  /// user segment label, CEP-specific tracking fields).
  /// Core does not interpret these — forwarded as-is in ExperienceEvents.
  final Map<String, dynamic> cepMetadata;

  // --- Digia Fields ---

  /// The coupling key that links this CEP campaign to a Digia campaign.
  /// Core uses this to look up the matching WidgetConfig in CampaignStore.
  final String campaignKey;

  /// Optional runtime variables to interpolate into the WidgetConfig.
  /// e.g. { "user_name": "Priya", "offer_value": "20%" }
  /// Keys must match variable placeholders declared in the Digia dashboard.
  final Map<String, String>? variables;

  // --- Trigger attribution (analytics matrix) ---

  /// How this campaign was triggered: `event` | `screen_view` | `manual` |
  /// `app_open`. Surfaced first-class so first-party analytics can record
  /// `trigger_type` on the experience's first event. Null when the plugin/host
  /// did not classify the trigger.
  final String? triggerType;

  /// For `event`/`screen_view` triggers, the originating event or screen name
  /// (matrix `trigger_event`). Null otherwise.
  final String? triggerEvent;

  const CEPTriggerPayload({
    required this.cepCampaignId,
    required this.cepMetadata,
    required this.campaignKey,
    this.variables,
    this.triggerType,
    this.triggerEvent,
  });
}
