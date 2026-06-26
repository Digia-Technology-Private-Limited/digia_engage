import 'package:flutter/foundation.dart';

import '../engage_settings.dart';
import '../preferences_store.dart';

const _kAnalyticsUserIdKey = 'digia_analytics.user_id';

/// Manages analytics identity: anonymous ID (device) and user ID.
///
/// Session management used to live here too; it now belongs to the shared
/// [SessionManager] so a single session id is shared by analytics and frequency
/// capping. This class is purely about *who* the events belong to.
class AnalyticsIdentityManager {
  String? _userId;
  String? _anonymousId;

  String get anonymousId => _anonymousId ?? '';
  String? get userId => _userId;

  Future<void> initialize() async {
    try {
      _anonymousId = EngageSettings.instance.getUuid();
      _userId = PreferencesStore.instance.read<String>(_kAnalyticsUserIdKey);
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
    } catch (error) {
      debugPrint('[Digia Analytics] identity clearUserId failed: $error');
    }
  }
}
