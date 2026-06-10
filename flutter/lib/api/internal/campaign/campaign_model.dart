import '../nudge/nudge_config.dart';
import '../nudge/nudge_parser.dart';
import '../survey/survey_config.dart';
import 'inline_carousel_config.dart';
import 'inline_story_config.dart';
import 'json_util.dart';

/// The parsed, typed config for a campaign.
///
/// Dart port of the Android `CampaignConfigModel` sealed interface. This
/// Flutter SDK renders inline carousels, inline stories, and nudges (bottom
/// sheet / dialog); every other type (guide, survey) is represented by
/// [UnsupportedCampaignConfig] — still kept in the store for routing/
/// diagnostics, but never rendered.
sealed class CampaignConfigModel {
  /// Dashboard-declared variable defaults (`templateConfig.variables`), shared
  /// by every campaign type. When a render scope is built, the CEP trigger
  /// payload's variables are layered on top of these (CEP wins), so a `{{ }}`
  /// placeholder falls back to its dashboard default when the trigger omits it.
  /// Values keep their JSON type and are stringified only at substitution.
  final Map<String, dynamic> defaultVariables;

  const CampaignConfigModel({
    this.defaultVariables = const <String, dynamic>{},
  });
}

/// An inline carousel campaign, surfaced through a [DigiaSlot].
class InlineCarouselCampaignConfig extends CampaignConfigModel {
  final InlineCarouselConfig inlineConfig;

  const InlineCarouselCampaignConfig(
    this.inlineConfig, {
    super.defaultVariables,
  });
}

/// An inline story campaign, surfaced through a [DigiaSlot] as a row of tappable
/// story cards that open a full-screen viewer.
class InlineStoryCampaignConfig extends CampaignConfigModel {
  final InlineStoryConfig storyConfig;

  const InlineStoryCampaignConfig(
    this.storyConfig, {
    super.defaultVariables,
  });
}

/// A nudge campaign — an overlay (bottom sheet / dialog) presented over the app.
class NudgeCampaignConfig extends CampaignConfigModel {
  final NudgeConfig nudgeConfig;

  const NudgeCampaignConfig(
    this.nudgeConfig, {
    super.defaultVariables,
  });
}

/// A survey campaign — a branching questionnaire presented as an overlay
/// (bottom sheet / dialog) via the [SurveyOrchestrator] and `SurveyRenderer`.
class SurveyCampaignConfig extends CampaignConfigModel {
  final SurveyConfigModel surveyConfig;

  const SurveyCampaignConfig(
    this.surveyConfig, {
    super.defaultVariables,
  });
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
  /// Returns `null` for entries missing required identity fields, or whose
  /// (supported) config is malformed. Unrecognised campaign types are kept as
  /// [UnsupportedCampaignConfig] rather than dropped, so the store mirrors the
  /// full set of campaigns the backend returned.
  ///
  /// Config construction is delegated to [CampaignConfigFactory] (a registry of
  /// per-type builders) so there is no `switch` to grow when a type is added.
  static CampaignModel? fromJson(Map<String, dynamic> json) {
    var id = optString(json, 'id');
    if (id.isEmpty) id = optString(json, '_id');
    if (id.isEmpty) return null;

    final campaignKey = optString(json, 'campaignKey');
    if (campaignKey.isEmpty) return null;

    final campaignType = optString(json, 'campaignType');
    if (campaignType.isEmpty) return null;

    final builder = CampaignConfigFactory.builderFor(campaignType);
    // A registered builder yields a config, or `null` to drop a malformed one.
    // An unknown type is recognised-but-unsupported (kept for diagnostics).
    final config = builder == null
        ? UnsupportedCampaignConfig(
            '$campaignType rendering is not supported in the Flutter SDK')
        : builder(json);
    if (config == null) return null;

    return CampaignModel(
      id: id,
      campaignKey: campaignKey,
      campaignType: campaignType,
      config: config,
    );
  }
}

/// Builds a [CampaignConfigModel] from a campaign's JSON, or returns `null` to
/// signal the campaign should be dropped (its config is missing/invalid).
typedef CampaignConfigBuilder = CampaignConfigModel? Function(
    Map<String, dynamic> json);

/// Factory (registry) mapping a `campaignType` to the builder that decodes its
/// config. Supporting a new renderable type is an open/closed change: add one
/// entry here — `CampaignModel.fromJson` is untouched, and unknown types keep
/// falling through to [UnsupportedCampaignConfig].
class CampaignConfigFactory {
  const CampaignConfigFactory._();

  static final Map<String, CampaignConfigBuilder> _builders = {
    'inline': _inline,
    'nudge': _nudge,
    'survey': _survey,
  };

  /// The builder for [campaignType], or `null` when the type is unknown.
  static CampaignConfigBuilder? builderFor(String campaignType) =>
      _builders[campaignType];

  static CampaignConfigModel? _inline(Map<String, dynamic> json) {
    final templateConfig = optMap(json, 'templateConfig');
    if (templateConfig == null) return null;
    final variables = _declaredVariables(templateConfig);
    if (optString(templateConfig, 'templateType', 'carousel') == 'story') {
      final story = InlineStoryConfig.fromJson(templateConfig);
      return story == null
          ? null
          : InlineStoryCampaignConfig(story, defaultVariables: variables);
    }
    final inline = InlineCarouselConfig.fromJson(templateConfig);
    return inline == null
        ? null
        : InlineCarouselCampaignConfig(inline, defaultVariables: variables);
  }

  static CampaignConfigModel? _nudge(Map<String, dynamic> json) {
    final templateConfig = optMap(json, 'templateConfig');
    if (templateConfig == null) return null;
    final nudge = const NudgeParser().parse(templateConfig);
    return nudge == null
        ? null
        : NudgeCampaignConfig(nudge,
            defaultVariables: _declaredVariables(templateConfig));
  }

  static CampaignConfigModel? _survey(Map<String, dynamic> json) {
    // The survey schema arrives as `surveyConfig`, or a `templateConfig` whose
    // `templateType == "survey"` (mirrors Android's `parseSurveyConfig`).
    final templateConfig = optMap(json, 'templateConfig');
    if (templateConfig == null) return null;
    final survey = SurveyConfigModel.fromJson(templateConfig);
    if (survey == null) return null;
    return SurveyCampaignConfig(survey,
        defaultVariables: _declaredVariables(templateConfig));
  }

  /// The dashboard-declared variable defaults block on a `templateConfig`, or an
  /// empty map when absent. Values keep their JSON type (stringified later, at
  /// interpolation), so a declared `count: 3` survives as a number here.
  static Map<String, dynamic> _declaredVariables(
      Map<String, dynamic> templateConfig) {
    final list = optList(templateConfig, 'variables');
    if (list != null) {
      final result = <String, dynamic>{};
      for (final entry in list) {
        if (entry is Map) {
          final name = entry['name'];
          if (name is String && name.isNotEmpty) {
            result[name] = entry.containsKey('fallbackValue')
                ? entry['fallbackValue']
                : entry['sampleValue'];
          }
        }
      }
      return result;
    }
    return const <String, dynamic>{};
  }
}
