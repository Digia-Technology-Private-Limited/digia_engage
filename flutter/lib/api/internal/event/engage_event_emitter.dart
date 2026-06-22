import 'package:flutter/foundation.dart';

import '../../models/cep_trigger_payload.dart';
import '../../models/digia_experience_event.dart';
import 'cep_plugin_sink.dart';
import 'digia_analytics_sink.dart';
import 'engage_analytics_event.dart';

/// The SDK's single entry point for emitting events, and the one place every
/// emission is logged.
///
/// Facade over the two delivery channels, which carry deliberately different
/// event models: the CEP plugin gets the coarse [DigiaExperienceEvent] protocol
/// via [toCep]; Digia analytics gets the rich, campaign-grouped
/// [EngageAnalyticsEvent] via [toDigia]. [toBoth] fires a dual signal (e.g. a
/// nudge impression). Also owns the first-render impression dedup, an emission
/// concern rather than widget state.
class EngageEventEmitter {
  final CepPluginSink _cep;
  final DigiaAnalyticsSink _digia;

  /// `cepCampaignId`s that have already fired a Digia first-render impression.
  final Set<String> _digiaImpressed = <String>{};

  /// `cepCampaignId`s that have already fired a Digia first-engagement click.
  final Set<String> _digiaClicked = <String>{};

  EngageEventEmitter(this._cep, this._digia);

  /// Coarse signal to the CEP plugin only.
  void toCep(DigiaExperienceEvent event, CEPTriggerPayload payload) {
    _log(
      "Event fired → CEP: $event | campaignKey=${payload.campaignKey} "
      'cepCampaignId=${payload.cepCampaignId}',
    );
    _cep.deliver(event, payload);
  }

  /// Rich analytics signal to Digia only.
  void toDigia(EngageAnalyticsEvent event, CEPTriggerPayload payload) {
    _log(
      "Event fired → DIGIA: '${event.eventName}' (${event.runtimeType}) | "
      'campaignKey=${payload.campaignKey} cepCampaignId=${payload.cepCampaignId} '
      'properties=${event.properties}',
    );
    _digia.deliver(event, payload);
  }

  /// Fires a coarse CEP signal and its rich Digia counterpart together.
  void toBoth(
    DigiaExperienceEvent cepEvent,
    EngageAnalyticsEvent digiaEvent,
    CEPTriggerPayload payload,
  ) {
    toCep(cepEvent, payload);
    toDigia(digiaEvent, payload);
  }

  /// Records [event] (a campaign "Viewed") to Digia the first time its campaign
  /// renders, deduped by `cepCampaignId`. CEP is impressed separately and
  /// instantly at route time.
  void digiaImpressionOnce(
      CEPTriggerPayload payload, EngageAnalyticsEvent event) {
    if (!_digiaImpressed.add(payload.cepCampaignId)) return;
    toDigia(event, payload);
  }

  /// Records [event] (an experience-level "Clicked") to Digia the first time the
  /// user engages with this campaign, deduped by `cepCampaignId`. Used for
  /// inline widgets where the first item tap is the campaign's engagement
  /// signal.
  void digiaExperienceClickedOnce(
    CEPTriggerPayload payload,
    EngageAnalyticsEvent event,
  ) {
    if (!_digiaClicked.add(payload.cepCampaignId)) return;
    toDigia(event, payload);
  }

  /// Forgets the impression + first-click marks so a later re-trigger re-arms
  /// both.
  void resetImpression(String cepCampaignId) {
    _digiaImpressed.remove(cepCampaignId);
    _digiaClicked.remove(cepCampaignId);
  }

  /// Forgets every impression + first-click mark.
  void clearImpressions() {
    _digiaImpressed.clear();
    _digiaClicked.clear();
  }

  void _log(String message) {
    if (kDebugMode) {
      debugPrint('[Digia] $message');
    }
  }
}
