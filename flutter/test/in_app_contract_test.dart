import 'package:digia_engage/api/internal/digia_instance.dart';
import 'package:digia_engage/digia_engage.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final instance = DigiaInstance.instance;

  tearDown(() {
    instance.controller.dismiss();
    instance.controller.clearSlots();
  });

  test('stores inline campaigns by placementKey', () {
    const payload = InAppPayload(
      id: 'campaign-inline',
      content: <String, dynamic>{
        'command': 'SHOW_INLINE',
        'placementKey': 'hero_banner',
        'viewId': 'hero_component',
        'args': <String, dynamic>{'theme': 'dark'},
      },
      cepContext: <String, dynamic>{},
    );

    instance.onCampaignTriggered(payload);

    expect(instance.getInlineCampaign('hero_banner'), same(payload));

    instance.onCampaignInvalidated('campaign-inline');
    expect(instance.getInlineCampaign('hero_banner'), isNull);
  });

  test('routes nudge campaigns to the overlay controller', () {
    const payload = InAppPayload(
      id: 'campaign-nudge',
      content: <String, dynamic>{
        'command': 'SHOW_DIALOG',
        'viewId': 'welcome_modal',
        'args': <String, dynamic>{'title': 'Hello'},
      },
      cepContext: <String, dynamic>{},
    );

    instance.onCampaignTriggered(payload);

    expect(instance.controller.activePayload, same(payload));
  });
}
