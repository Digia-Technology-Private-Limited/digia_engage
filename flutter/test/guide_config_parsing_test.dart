import 'package:digia_engage/api/internal/campaign/campaign_model.dart';
import 'package:digia_engage/api/internal/guide/guide_config_model.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('GuideConfigModel.fromCampaignJson', () {
    test('parses flat templateConfig (tooltip) into a guide campaign', () {
      final json = <String, dynamic>{
        'id': 'camp_1',
        'campaignKey': 'onboarding_guide',
        'campaignType': 'guide',
        'templateConfig': {
          'templateType': 'tooltip',
          'templateId': 'guide_42',
          'variables': [
            {'name': 'userName', 'fallbackValue': 'there'},
          ],
          'steps': [
            {
              'anchorKey': 'signup_button',
              'sequenceOrder': 0,
              'advanceTrigger': 'tap',
              'bubble': {
                'background_color': '#1E40AF',
                'corner_radius': 12,
                'arrow': {'visible': true, 'preferred_direction': 'top', 'size': 8},
              },
              'overlay': {'visible': false},
              'content': {
                'title': {
                  'text': 'Hi {{userName}}',
                  'textStyle': {'textColor': '#FFFFFF'},
                },
                'body': {'text': 'Tap to sign up'},
                'step_indicator': {'visible': true, 'color': '#FFFFFFAA'},
              },
              'actions': [
                {'id': 'next', 'label': 'Next', 'action_type': 'NEXT'},
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
      expect(guide.id, 'guide_42');
      expect(guide.multiStep, isFalse); // single step
      expect(guide.steps, hasLength(1));

      final step = guide.steps.first;
      expect(step.anchorKey, 'signup_button');
      expect(step.displayStyle, 'tooltip');
      expect(step.widgetConfig.overlay.visible, isFalse); // tooltip
      expect(step.widgetConfig.bubble.arrow.preferredDirection, 'top');
      expect(step.widgetConfig.content.title?.text, 'Hi {{userName}}');
      expect(step.widgetConfig.content.title?.color, const Color(0xFFFFFFFF));
      expect(step.widgetConfig.actions, hasLength(1));
      expect(step.widgetConfig.actions.first.actionType, GuideActionType.next);

      // Dashboard variable default flows through to the campaign config.
      expect(config.defaultVariables['userName'], 'there');
    });

    test('parses nested guideConfig (spotlight) and sorts steps', () {
      final json = <String, dynamic>{
        'id': 'camp_2',
        'campaignKey': 'feature_tour',
        'campaignType': 'guide',
        'guideConfig': {
          'id': 'tour_1',
          'multiStep': true,
          'steps': [
            {
              'anchorKey': 'b',
              'sequenceOrder': 1,
              'displayStyle': 'spotlight',
              'widgetConfig': {
                'overlay': {
                  'visible': true,
                  'alpha': 0.7,
                  'cutout': {'shape': 'circle', 'padding': 6},
                },
              },
            },
            {
              'anchorKey': 'a',
              'sequenceOrder': 0,
              'displayStyle': 'spotlight',
              'widgetConfig': {
                'overlay': {'visible': true},
              },
            },
          ],
        },
      };

      final campaign = CampaignModel.fromJson(json);
      final guide = (campaign!.config as GuideCampaignConfig).guideConfig;
      expect(guide.multiStep, isTrue);
      // Sorted by sequenceOrder: a (0) before b (1).
      expect(guide.steps.map((s) => s.anchorKey).toList(), ['a', 'b']);
      expect(guide.steps[1].widgetConfig.overlay.visible, isTrue);
      expect(guide.steps[1].widgetConfig.overlay.cutout.shape, 'circle');
    });

    test('returns UnsupportedCampaignConfig when no guide payload present', () {
      final json = <String, dynamic>{
        'id': 'camp_3',
        'campaignKey': 'broken_guide',
        'campaignType': 'guide',
        'templateConfig': {'templateType': 'carousel'}, // not a guide template
      };
      final campaign = CampaignModel.fromJson(json);
      // No usable guide payload → builder returns null → campaign dropped.
      expect(campaign, isNull);
    });
  });
}
