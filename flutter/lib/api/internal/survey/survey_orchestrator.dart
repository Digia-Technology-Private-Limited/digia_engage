import 'package:flutter/foundation.dart';

import '../../models/cep_trigger_payload.dart';
import '../campaign/campaign_model.dart';
import 'survey_config.dart';

/// The survey campaign currently routed for display. [token] is unique per
/// showing so the renderer can key a fresh in-progress state to it.
class ActiveSurveyState {
  final CampaignModel campaign;
  final CEPTriggerPayload payload;
  final int token;
  final int startedAtMs;
  final SurveyConfigModel config;

  ActiveSurveyState({
    required this.campaign,
    required this.payload,
    required this.token,
    required this.startedAtMs,
  }) : config = (campaign.config as SurveyCampaignConfig).surveyConfig;
}

/// Holds the active survey (one at a time), mirroring the Android
/// `SurveyOrchestrator`. The in-progress answer state lives in the renderer's
/// [SurveyController]; this only tracks which survey (if any) is on screen.
class SurveyOrchestrator extends ChangeNotifier {
  ActiveSurveyState? _state;
  ActiveSurveyState? get state => _state;

  int _tokenCounter = 0;

  /// Returns true if the survey was started, false if preconditions fail or one
  /// is already showing.
  bool start(CampaignModel campaign, CEPTriggerPayload payload, {required int nowMs}) {
    final config = campaign.config;
    if (campaign.campaignType != 'survey' || config is! SurveyCampaignConfig) {
      return false;
    }
    final survey = config.surveyConfig;
    if (survey.nodes.isEmpty || survey.blocks.isEmpty) return false;
    if (_state != null) return false;
    _state = ActiveSurveyState(
      campaign: campaign,
      payload: payload,
      token: ++_tokenCounter,
      startedAtMs: nowMs,
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
