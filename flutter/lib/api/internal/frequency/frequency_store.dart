import 'dart:async';
import 'dart:convert';

import '../../../src/preferences_store.dart';
import 'frequency_policy.dart';

/// Persists per-campaign [FrequencyState]. Dart port of the React Native SDK's
/// `frequencyStore.ts`, extended so session-scoped caps survive an app relaunch.
///
/// All state is persisted via [PreferencesStore] (SharedPreferences), whose reads
/// are synchronous once initialized — so the route-time eligibility gate stays
/// synchronous. Writes are fire-and-forget: SharedPreferences updates its
/// in-memory cache synchronously, so a later read sees the new value immediately.
///
/// Session-scoped state is tagged with the [SessionManager] session id at write
/// time and only honoured on read while that id is still current — so it
/// persists across relaunches within the same session and resets the moment the
/// session rotates (inactivity timeout or user change). Non-session state is
/// plain persisted state with no session tag.
///
/// On an API-key change the persisted state is wiped, so one project's caps
/// never leak into another's (mirrors RN's `checkApiKey`).
class FrequencyStore {
  static const String _keyPrefix = 'engage.freq.';
  static const String _metaKey = 'engage.freq.__meta__';

  /// Field embedded in a session-scoped record to tie it to the session that
  /// wrote it. Underscored so it never collides with a [FrequencyState] field.
  static const String _sessionIdField = '__session_id__';

  final PreferencesStore _prefs;

  /// Resolves the current session id (from the shared `SessionManager`). A
  /// function so the store stays decoupled from session ownership and is trivial
  /// to test with a fake clock/session.
  final String Function() _currentSessionId;

  FrequencyStore(this._prefs, this._currentSessionId);

  String _storeKey(String campaignKey) => '$_keyPrefix$campaignKey';

  /// Drops all persisted frequency state when [apiKey] differs from the last
  /// initialised key. Synchronous — relies on [PreferencesStore]'s in-memory
  /// SharedPreferences cache; the removals flush asynchronously.
  void checkApiKey(String apiKey) {
    try {
      final raw = _prefs.read<String>(_metaKey);
      final meta = raw == null ? null : jsonDecode(raw);
      final storedKey = meta is Map ? meta['apiKey'] : null;
      if (storedKey is String && storedKey != apiKey) {
        for (final key in _prefs.getKeys()) {
          if (key.startsWith(_keyPrefix)) {
            unawaited(_prefs.delete(key));
          }
        }
      }
      unawaited(_prefs.write(_metaKey, jsonEncode({'apiKey': apiKey})));
    } catch (_) {
      // non-fatal — a corrupt meta record just skips the wipe.
    }
  }

  FrequencyState? get(String campaignKey, {required bool isSession}) {
    final raw = _prefs.read<String>(_storeKey(campaignKey));
    if (raw == null) return null;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map) return null;
      final map = decoded.cast<String, dynamic>();
      if (isSession && map[_sessionIdField] != _currentSessionId()) {
        // State belongs to a session that has since ended — treat as fresh.
        return null;
      }
      return FrequencyState.fromJson(map);
    } catch (_) {
      // A corrupt record reads as "no state".
      return null;
    }
  }

  void set(String campaignKey, FrequencyState state, {required bool isSession}) {
    final map = state.toJson();
    if (isSession) map[_sessionIdField] = _currentSessionId();
    unawaited(_prefs.write(_storeKey(campaignKey), jsonEncode(map)));
  }
}
