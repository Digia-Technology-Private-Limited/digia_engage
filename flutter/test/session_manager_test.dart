import 'package:digia_engage/src/preferences_store.dart';
import 'package:digia_engage/src/session/session_manager.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Verifies the shared session's relaunch behaviour: a live session is reused
/// across an app restart, while an expired one rotates to a fresh id.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await PreferencesStore.instance.initialize();
    SessionManager.instance.resetForTest();
  });

  test('reuses the persisted session id across a relaunch within the timeout',
      () async {
    await SessionManager.instance.initialize(const Duration(hours: 1).inMilliseconds);
    final first = SessionManager.instance.sessionId;
    expect(first, isNotEmpty);

    // Simulate a process restart: drop in-memory state, keep persisted prefs.
    SessionManager.instance.resetForTest();
    await SessionManager.instance.initialize(const Duration(hours: 1).inMilliseconds);

    expect(SessionManager.instance.sessionId, equals(first));
  });

  test('starts a new session when the previous one has expired', () async {
    // A zero timeout means any elapsed time counts as expired.
    await SessionManager.instance.initialize(0);
    final first = SessionManager.instance.sessionId;

    SessionManager.instance.resetForTest();
    await SessionManager.instance.initialize(0);

    expect(SessionManager.instance.sessionId, isNotEmpty);
    expect(SessionManager.instance.sessionId, isNot(equals(first)));
  });

  test('reset rotates the session and notifies listeners', () async {
    await SessionManager.instance.initialize(const Duration(hours: 1).inMilliseconds);
    final before = SessionManager.instance.sessionId;

    var rotations = 0;
    SessionManager.instance.addRotationListener(() => rotations += 1);

    await SessionManager.instance.reset();

    expect(SessionManager.instance.sessionId, isNot(equals(before)));
    expect(rotations, equals(1));
  });
}
