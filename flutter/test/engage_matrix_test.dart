import 'package:digia_engage/api/internal/action/engage_action.dart';
import 'package:digia_engage/api/internal/event/engage_matrix.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('nudge matrix properties', () {
    test('clicked props derive cta_position, action_type and action_url '
        'from a primary url button', () {
      final props = nudgeClickedProperties(
        label: 'Shop now',
        isPrimary: true,
        actions: const [OpenUrlAction('https://shop.example.com')],
        timeToActionMs: 1500,
      );

      expect(props['element_id'], equals('cta_primary'));
      expect(props['cta_label'], equals('Shop now'));
      expect(props['cta_position'], equals('primary'));
      expect(props['action_type'], equals('url'));
      expect(props['action_url'], equals('https://shop.example.com'));
      expect(props['time_to_action_ms'], equals(1500));
    });

    test('clicked props map a deeplink secondary button without a url for '
        'dismiss/share actions', () {
      final deeplink = nudgeClickedProperties(
        label: 'Open',
        isPrimary: false,
        actions: const [OpenDeeplinkAction('myapp://route/42')],
      );
      expect(deeplink['element_id'], equals('cta_secondary'));
      expect(deeplink['cta_position'], equals('secondary'));
      expect(deeplink['action_type'], equals('deeplink'));
      expect(deeplink['action_url'], equals('myapp://route/42'));
      expect(deeplink.containsKey('time_to_action_ms'), isFalse);

      final dismiss = nudgeClickedProperties(
        label: 'Maybe later',
        isPrimary: false,
        actions: const [HideAction()],
      );
      expect(dismiss['action_type'], equals('dismiss'));
      expect(dismiss.containsKey('action_url'), isFalse);

      final share = nudgeClickedProperties(
        label: 'Share',
        isPrimary: true,
        actions: const [ShareAction('hi')],
      );
      expect(share['action_type'], equals('custom'));
      expect(share.containsKey('action_url'), isFalse);
    });

    test('viewed props carry display_style and optional context only when set',
        () {
      final full = nudgeViewedProperties(
        displayStyle: 'bottom_sheet',
        screenName: 'home',
        triggerType: 'event',
        triggerEvent: 'add_to_cart',
      );
      expect(full, equals({
        'display_style': 'bottom_sheet',
        'screen_name': 'home',
        'trigger_type': 'event',
        'trigger_event': 'add_to_cart',
      }));

      final minimal = nudgeViewedProperties(displayStyle: 'dialog');
      expect(minimal, equals({'display_style': 'dialog'}));
    });

    test('dismissed props carry reason and timing only when set', () {
      expect(
        nudgeDismissedProperties(
          dismissReason: 'backdrop',
          timeToDismissMs: 4200,
        ),
        equals({'dismiss_reason': 'backdrop', 'time_to_dismiss_ms': 4200}),
      );
      expect(nudgeDismissedProperties(), equals(const {}));
    });
  });

  group('inline matrix properties', () {
    test('experience-viewed props stamp the container display_style and total',
        () {
      expect(
        inlineViewedProperties(
          displayStyle: 'carousel',
          itemTotal: 3,
          screenName: 'home',
        ),
        equals({
          'display_style': 'carousel',
          'item_total': 3,
          'screen_name': 'home',
        }),
      );
      // story rail viewed, no screen context known
      expect(
        inlineViewedProperties(displayStyle: 'story', itemTotal: 5),
        equals({'display_style': 'story', 'item_total': 5}),
      );
    });

    test('step-viewed props carry the 0-based index, total and optional id', () {
      expect(
        inlineStepProperties(
          displayStyle: 'carousel',
          itemIndex: 1,
          itemTotal: 3,
          itemId: 'slide_b',
        ),
        equals({
          'display_style': 'carousel',
          'item_index': 1,
          'item_total': 3,
          'item_id': 'slide_b',
        }),
      );
      // no server id → item_id omitted
      expect(
        inlineStepProperties(displayStyle: 'story', itemIndex: 0, itemTotal: 4),
        equals({'display_style': 'story', 'item_index': 0, 'item_total': 4}),
      );
    });

    test('step-clicked props add the resolved action target', () {
      expect(
        inlineStepProperties(
          displayStyle: 'carousel',
          itemIndex: 2,
          itemTotal: 3,
          actionType: 'deeplink',
          actionUrl: 'myapp://pdp/9',
        ),
        equals({
          'display_style': 'carousel',
          'item_index': 2,
          'item_total': 3,
          'action_type': 'deeplink',
          'action_url': 'myapp://pdp/9',
        }),
      );
    });

    test('completed props carry total and optional time_to_complete_ms', () {
      expect(
        inlineCompletedProperties(
          displayStyle: 'story',
          itemTotal: 4,
          timeToCompleteMs: 12000,
        ),
        equals({
          'display_style': 'story',
          'item_total': 4,
          'time_to_complete_ms': 12000,
        }),
      );
      expect(
        inlineCompletedProperties(displayStyle: 'story', itemTotal: 4),
        equals({'display_style': 'story', 'item_total': 4}),
      );
    });
  });
}
