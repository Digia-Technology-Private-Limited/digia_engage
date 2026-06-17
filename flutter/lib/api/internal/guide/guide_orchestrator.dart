import 'package:flutter/foundation.dart';

import '../../models/cep_trigger_payload.dart';
import '../campaign/campaign_model.dart';
import 'guide_config_model.dart';

/// The guide campaign currently routed for display. [token] is unique per
/// showing so the renderer can key a fresh overlay session to it.
class ActiveGuideState {
  final CampaignModel campaign;
  final CEPTriggerPayload payload;
  final int token;
  final GuideConfigModel config;

  ActiveGuideState({
    required this.campaign,
    required this.payload,
    required this.token,
  }) : config = (campaign.config as GuideCampaignConfig).guideConfig;
}

/// Holds the active guide (one at a time), mirroring the Android
/// `GuideOrchestrator` and the Flutter `SurveyOrchestrator`. Step progression
/// (current index, next/prev) lives in the renderer's overlay session; this
/// only tracks which guide, if any, is on screen.
class GuideOrchestrator extends ChangeNotifier {
  ActiveGuideState? _state;
  ActiveGuideState? get state => _state;

  int _tokenCounter = 0;

  /// Returns true if the guide was started, false if preconditions fail or one
  /// is already showing.
  bool start(CampaignModel campaign, CEPTriggerPayload payload) {
    final config = campaign.config;
    if (campaign.campaignType != 'guide' || config is! GuideCampaignConfig) {
      return false;
    }
    if (config.guideConfig.steps.isEmpty) return false;
    if (_state != null) return false;
    _state = ActiveGuideState(
      campaign: campaign,
      payload: payload,
      token: ++_tokenCounter,
    );
    notifyListeners();
    return true;
  }

  void dismiss() {
    if (_state == null) return;
    _state = null;
    notifyListeners();
  }
}
