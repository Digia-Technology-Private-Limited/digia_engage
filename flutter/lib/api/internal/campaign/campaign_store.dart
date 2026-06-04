import 'campaign_model.dart';

/// In-memory cache of campaigns keyed by `campaignKey`.
///
/// Dart port of the Android `CampaignStore`. Populated once during
/// [DigiaInstance.initialize] and consulted on every CEP-triggered campaign.
/// Not persisted across launches — the SDK refetches on each cold start.
class CampaignStore {
  final Map<String, CampaignModel> _campaigns = <String, CampaignModel>{};

  /// Replaces the entire cache with [list], keyed by `campaignKey`.
  void populate(List<CampaignModel> list) {
    _campaigns.clear();
    for (final campaign in list) {
      _campaigns[campaign.campaignKey] = campaign;
    }
  }

  CampaignModel? find(String campaignKey) => _campaigns[campaignKey];

  CampaignModel? findById(String campaignId) {
    for (final campaign in _campaigns.values) {
      if (campaign.id == campaignId) return campaign;
    }
    return null;
  }

  bool get isEmpty => _campaigns.isEmpty;

  void clear() => _campaigns.clear();
}
