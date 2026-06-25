import 'package:digia_engage/api/internal/campaign/campaign_model.dart';
import 'package:digia_engage/api/internal/frequency/frequency_manager.dart';
import 'package:digia_engage/api/internal/frequency/frequency_policy.dart';
import 'package:digia_engage/src/preferences_store.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Mirrors Android's `FrequencyManager` persistence tests: state survives a
/// fresh manager over the same store, session rotation resets a session window,
/// and `recordCompleted` only stops `experienceCompleted` policies.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  CampaignModel campaign(String key, FrequencyPolicy? policy) => CampaignModel(
        id: 'id_$key',
        campaignKey: key,
        campaignType: 'nudge',
        config: const UnsupportedCampaignConfig('test'),
        frequency: policy,
      );

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await PreferencesStore.instance.initialize();
  });

  test('persists state across reloads and caps; session rotation resets', () {
    var now = 0;
    var session = 's1';
    final policy = FrequencyPolicy(maxPerWindow: FrequencyWindow(count: 1, window: 'session'));
    final camp = campaign('camp', policy);

    final mgr = FrequencyManager(
      PreferencesStore.instance,
      () => session,
      clock: () => now,
    );

    expect(mgr.isAllowed(camp), isTrue);
    mgr.recordShow(camp);
    now += 3600000;
    expect(mgr.isAllowed(camp), isFalse); // same session → capped

    // A fresh manager over the same store must see the persisted state.
    final mgr2 = FrequencyManager(
      PreferencesStore.instance,
      () => session,
      clock: () => now,
    );
    expect(mgr2.isAllowed(camp), isFalse);

    session = 's2'; // rotate session → window resets
    expect(mgr2.isAllowed(camp), isTrue);
  });

  test('recordCompleted stops only experienceCompleted policies', () {
    final mgr = FrequencyManager(PreferencesStore.instance, () => 's1', clock: () => 0);

    // No stopOn → recordCompleted is a no-op.
    final a = campaign('a', const FrequencyPolicy(maxTotal: 5));
    mgr.recordCompleted(a);
    expect(mgr.isAllowed(a), isTrue);

    final b = campaign('b', const FrequencyPolicy(stopOn: 'experienceCompleted'));
    mgr.recordCompleted(b);
    expect(mgr.isAllowed(b), isFalse);
  });

  test('uncapped campaigns always pass and never persist', () {
    final mgr = FrequencyManager(PreferencesStore.instance, () => 's1', clock: () => 0);
    final camp = campaign('none', null);
    expect(mgr.isAllowed(camp), isTrue);
    mgr.recordShow(camp);
    expect(PreferencesStore.instance.contains('freq.none'), isFalse);
  });
}
