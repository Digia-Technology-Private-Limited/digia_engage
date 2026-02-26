/// The translation contract between CEP-specific data and Digia's rendering engine.
///
/// Plugin authors are responsible for mapping their CEP's native types
/// into this struct inside their _mapToInAppPayload() private method.
/// Digia core never imports CleverTap, MoEngage, or WebEngage types.
class InAppPayload {
  /// Unique identifier for this campaign instance.
  /// Used for deduplication, invalidation, and event correlation.
  final String id;

  /// Raw content map from the CEP campaign.
  /// Digia's rendering engine reads this to construct the experience.
  /// Schema is defined by the Digia dashboard campaign format.
  final Map<String, dynamic> content;

  /// CEP-specific metadata, opaque to Digia core.
  ///
  /// Plugin writes whatever identifiers it needs here during mapping.
  /// Plugin reads them back in notifyEvent() to call the correct CEP API.
  ///
  /// Example (CleverTap):  {'templateName': 'onboarding_modal'}
  /// Example (MoEngage):   {'campaignId': 'abc123', 'campaignName': 'summer'}
  final Map<String, dynamic> cepContext;

  const InAppPayload({
    required this.id,
    required this.content,
    required this.cepContext,
  });
}
