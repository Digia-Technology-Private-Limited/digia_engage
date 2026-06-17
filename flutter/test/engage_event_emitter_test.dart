import 'package:digia_engage/api/internal/event/engage_event_emitter.dart';
import 'package:digia_engage/api/internal/event/engage_event_router.dart';
import 'package:digia_engage/api/models/cep_trigger_payload.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('analytics() forwards event name, payload, properties and flush', () {
    String? capturedName;
    CEPTriggerPayload? capturedPayload;
    Map<String, dynamic>? capturedProps;
    bool? capturedFlush;

    final emitter = EngageEventEmitter(
      EngageEventRouter(const []),
      analytics: (name, payload, {properties = const {}, flush = false}) {
        capturedName = name;
        capturedPayload = payload;
        capturedProps = properties;
        capturedFlush = flush;
      },
    );

    const payload = CEPTriggerPayload(
      cepCampaignId: 'c1',
      cepMetadata: {},
      campaignKey: 'k1',
    );

    emitter.analytics(
      'Digia Step Viewed',
      payload,
      properties: const {'item_index': 1, 'item_total': 3},
      flush: true,
    );

    expect(capturedName, equals('Digia Step Viewed'));
    expect(capturedPayload?.campaignKey, equals('k1'));
    expect(capturedProps, equals({'item_index': 1, 'item_total': 3}));
    expect(capturedFlush, isTrue);
  });

  test('analytics() is a no-op when no dispatch is wired', () {
    final emitter = EngageEventEmitter(EngageEventRouter(const []));
    const payload = CEPTriggerPayload(
      cepCampaignId: 'c1',
      cepMetadata: {},
      campaignKey: 'k1',
    );
    // Should not throw.
    emitter.analytics('Digia Experience Viewed', payload);
  });

  test('digiaImpressionOnce fires a rich analytics event once per campaign '
      'when an event name is given', () {
    final calls = <Map<String, dynamic>>[];
    final emitter = EngageEventEmitter(
      EngageEventRouter(const []),
      analytics: (name, payload, {properties = const {}, flush = false}) =>
          calls.add({'name': name, 'props': properties}),
    );
    const payload = CEPTriggerPayload(
      cepCampaignId: 'c1',
      cepMetadata: {},
      campaignKey: 'k1',
    );

    emitter.digiaImpressionOnce(
      payload,
      eventName: 'Digia Experience Viewed',
      properties: const {'display_style': 'carousel', 'item_total': 3},
    );
    // Second call for the same campaign is deduped.
    emitter.digiaImpressionOnce(
      payload,
      eventName: 'Digia Experience Viewed',
      properties: const {'display_style': 'carousel', 'item_total': 3},
    );

    expect(calls, hasLength(1));
    expect(calls.single['name'], equals('Digia Experience Viewed'));
    expect(calls.single['props'],
        equals({'display_style': 'carousel', 'item_total': 3}));
  });
}
