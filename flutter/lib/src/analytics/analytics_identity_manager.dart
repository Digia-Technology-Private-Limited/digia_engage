import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import '../engage_settings.dart';
import '../preferences_store.dart';

const _kAnalyticsUserIdKey = 'digia_analytics.user_id';
const _kAnalyticsSessionIdKey = 'digia_analytics.session_id';
const _kAnalyticsSessionLastEventAtKey =
    'digia_analytics.session_last_event_at_ms';

/// Manages analytics identity: anonymous ID (device), user ID, and session ID
class AnalyticsIdentityManager {
  String? _userId;
  String? _sessionId;
  String? _anonymousId;
  int? _lastEventAtMs;
  late int _sessionTimeoutMs;

  String get anonymousId => _anonymousId ?? '';
  String? get userId => _userId;
  String get sessionId => _sessionId ?? _generateSessionId();

  Future<void> initialize(int sessionTimeoutMs) async {
    try {
      _sessionTimeoutMs = sessionTimeoutMs;

      _anonymousId = EngageSettings.instance.getUuid();

      _userId = PreferencesStore.instance.read<String>(_kAnalyticsUserIdKey);
      _sessionId =
          PreferencesStore.instance.read<String>(_kAnalyticsSessionIdKey);
      _lastEventAtMs =
          PreferencesStore.instance.read<int>(_kAnalyticsSessionLastEventAtKey);

      if (_sessionId == null || _sessionId!.isEmpty) {
        _sessionId = _generateSessionId();
        await PreferencesStore.instance
            .write<String>(_kAnalyticsSessionIdKey, _sessionId!);
      }

      if (_isSessionExpired()) {
        await _resetSession();
      }
    } catch (error) {
      debugPrint('[Digia Analytics] identity initialize failed: $error');
    }
  }

  Future<void> setUserId(String userId) async {
    try {
      _userId = userId;
      await PreferencesStore.instance
          .write<String>(_kAnalyticsUserIdKey, userId);
    } catch (error) {
      debugPrint('[Digia Analytics] identity setUserId failed: $error');
    }
  }

  Future<void> clearUserId() async {
    try {
      _userId = null;
      await PreferencesStore.instance.delete(_kAnalyticsUserIdKey);
      await _resetSession();
    } catch (error) {
      debugPrint('[Digia Analytics] identity clearUserId failed: $error');
    }
  }

  Future<void> captureEventTime() async {
    try {
      if (_isSessionExpired()) {
        await _resetSession();
      }
      _lastEventAtMs = DateTime.now().millisecondsSinceEpoch;
      await PreferencesStore.instance.write<int>(
        _kAnalyticsSessionLastEventAtKey,
        _lastEventAtMs!,
      );
    } catch (error) {
      debugPrint('[Digia Analytics] identity captureEventTime failed: $error');
    }
  }

  Future<void> maybeExpireSession() async {
    try {
      if (_isSessionExpired()) {
        await _resetSession();
      }
    } catch (error) {
      debugPrint('[Digia Analytics] identity maybeExpireSession failed: $error');
    }
  }

  bool _isSessionExpired() {
    if (_lastEventAtMs == null) {
      return false;
    }
    final elapsed = DateTime.now().millisecondsSinceEpoch - _lastEventAtMs!;
    return elapsed >= _sessionTimeoutMs;
  }

  Future<void> _resetSession() async {
    _sessionId = _generateSessionId();
    _lastEventAtMs = DateTime.now().millisecondsSinceEpoch;
    await PreferencesStore.instance
        .write<String>(_kAnalyticsSessionIdKey, _sessionId!);
    await PreferencesStore.instance.write<int>(
      _kAnalyticsSessionLastEventAtKey,
      _lastEventAtMs!,
    );
  }

  String _generateSessionId() => const Uuid().v4();
}
