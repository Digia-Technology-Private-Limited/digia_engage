import 'inline_carousel_config.dart';
import 'json_util.dart';

/// The parsed, typed config for a campaign.
///
/// Dart port of the Android `CampaignConfigModel` sealed interface. This
/// Flutter SDK only renders inline carousels, so every non-carousel campaign
/// type (guide, nudge, survey, inline-story) is represented by
/// [UnsupportedCampaignConfig] — it is still kept in the store for routing/
/// diagnostics, but is never rendered.
sealed class CampaignConfigModel {
  const CampaignConfigModel();
}

/// An inline carousel campaign — the only renderable type in this SDK.
class InlineCarouselCampaignConfig extends CampaignConfigModel {
  final InlineCarouselConfig inlineConfig;

  const InlineCarouselCampaignConfig(this.inlineConfig);
}

/// A campaign whose type is recognised but not rendered by the Flutter SDK.
class UnsupportedCampaignConfig extends CampaignConfigModel {
  final String reason;

  const UnsupportedCampaignConfig(this.reason);
}

/// A single campaign fetched from the Digia backend.
///
/// Dart port of the Android `CampaignModel`. Campaigns are keyed by
/// [campaignKey] in [CampaignStore] and routed by [campaignType] when the CEP
/// plugin triggers them.
class CampaignModel {
  final String id;
  final String campaignKey;
  final String campaignType;
  final CampaignConfigModel config;

  const CampaignModel({
    required this.id,
    required this.campaignKey,
    required this.campaignType,
    required this.config,
  });

  /// Parses one campaign object from the `getCampaigns` response.
  ///
  /// Returns `null` for entries missing required identity fields or with a
  /// malformed inline carousel config. Non-inline campaign types are kept as
  /// [UnsupportedCampaignConfig] rather than dropped, so the store mirrors the
  /// full set of campaigns the backend returned.
  static CampaignModel? fromJson(Map<String, dynamic> json) {
    var id = optString(json, 'id');
    if (id.isEmpty) id = optString(json, '_id');
    if (id.isEmpty) return null;

    final campaignKey = optString(json, 'campaignKey');
    if (campaignKey.isEmpty) return null;

    final campaignType = optString(json, 'campaignType');
    if (campaignType.isEmpty) return null;

    late final CampaignConfigModel config;
    switch (campaignType) {
      case 'inline':
        final templateConfig = optMap(json, 'templateConfig');
        if (templateConfig == null) return null;
        final templateType = optString(templateConfig, 'templateType', 'carousel');
        if (templateType == 'story') {
          config = const UnsupportedCampaignConfig(
            'inline story rendering is not supported in the Flutter SDK',
          );
        } else {
          final inline = InlineCarouselConfig.fromJson(templateConfig);
          if (inline == null) return null;
          config = InlineCarouselCampaignConfig(inline);
        }
        break;
      default:
        // guide / nudge / survey — recognised but intentionally not rendered.
        config = UnsupportedCampaignConfig(
          '$campaignType rendering is not supported in the Flutter SDK (inline only)',
        );
    }

    return CampaignModel(
      id: id,
      campaignKey: campaignKey,
      campaignType: campaignType,
      config: config,
    );
  }
}
