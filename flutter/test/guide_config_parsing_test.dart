import 'package:digia_engage/api/internal/campaign/campaign_model.dart';
import 'package:digia_engage/api/internal/guide/guide_config_model.dart';
import 'package:flutter/painting.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('GuideConfig.fromCampaignJson (RN flat shape)', () {
    test('parses a tooltip guide campaign', () {
      final json = <String, dynamic>{
        'id': 'camp_1',
        'campaignKey': 'onboarding_guide',
        'campaignType': 'guide',
        'templateConfig': {
          'templateType': 'tooltip',
          'templateId': 'guide_42',
          'outsideTapBehavior': 'dismiss',
          'variables': [
            {'name': 'userName', 'fallbackValue': 'there'},
          ],
          'steps': [
            {
              'anchorKey': 'signup_button',
              'title': 'Hi {{userName}}',
              'body': 'Tap to sign up',
              'placement': 'top',
              'backgroundColor': '#1E40AF',
              'cornerRadius': 12,
              'showArrow': true,
              'titleColor': '#FFFFFF',
              'titleWeight': '600',
              'actions': [
                {'type': 'next', 'label': 'Next', 'style': 'primary'},
              ],
            },
          ],
        },
      };

      final campaign = CampaignModel.fromJson(json);
      expect(campaign, isNotNull);
      final config = campaign!.config;
      expect(config, isA<GuideCampaignConfig>());

      final guide = (config as GuideCampaignConfig).guideConfig;
      expect(guide, isA<TooltipGuideConfig>());
      expect(guide.templateId, 'guide_42');
      expect(guide.outsideTapBehavior, 'dismiss');
      expect(guide.stepCount, 1);
      expect(guide.firstAnchorKey, 'signup_button');

      final step = (guide as TooltipGuideConfig).steps.first;
      expect(step.title, 'Hi {{userName}}');
      expect(step.placement, 'top');
      expect(step.backgroundColor, const Color(0xFF1E40AF));
      expect(step.titleWeight, FontWeight.w600);
      expect(step.actions.single.type, GuideActionType.next);
      expect(step.actions.single.style, GuideActionStyle.primary);
    });

    test('parses a spotlight guide campaign with multiple steps', () {
      final json = <String, dynamic>{
        'id': 'camp_2',
        'campaignKey': 'feature_tour',
        'campaignType': 'guide',
        'templateConfig': {
          'templateType': 'spotlight',
          'steps': [
            {
              'anchorKey': 'a',
              'title': 'One',
              'highlightShape': 'circle',
              'overlayColor': '#000000',
              'overlayOpacity': 0.7,
              'calloutPosition': 'below',
            },
            {
              'anchorKey': 'b',
              'title': 'Two',
              'highlightShape': 'pill',
              'highlightPadding': 6,
            },
          ],
        },
      };

      final campaign = CampaignModel.fromJson(json);
      final guide = (campaign!.config as GuideCampaignConfig).guideConfig;
      expect(guide, isA<SpotlightGuideConfig>());
      expect(guide.anchorKeys, ['a', 'b']);

      final first = (guide as SpotlightGuideConfig).steps.first;
      expect(first.highlightShape, 'circle');
      expect(first.overlayOpacity, 0.7);
      expect(first.calloutPosition, 'below');
    });

    test('drops a guide campaign with an unknown templateType', () {
      final json = <String, dynamic>{
        'id': 'camp_3',
        'campaignKey': 'broken_guide',
        'campaignType': 'guide',
        'templateConfig': {'templateType': 'carousel'},
      };
      expect(CampaignModel.fromJson(json), isNull);
    });
  });
}
