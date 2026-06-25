import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import '../preferences_store.dart';

const _kSessionIdKey = 'engage.session.id';
const _kSessionLastActivityKey = 'engage.session.last_activity_ms';

/// Owns the SDK's session lifecycle, shared by analytics (event grouping) and
/// frequency capping (session-scoped caps).
///
/// A session is identified by [sessionId] and **persists across app relaunches**:
/// on [initialize] the stored id is reused when the inactivity window hasn't
/// elapsed, and a new id is minted only when there is no live session. The
/// session rotates when it expires (inactivity timeout, checked on activity and
/// app resume) or is explicitly [reset] (e.g. on a user-identity change).
///
/// This was extracted out of `AnalyticsIdentityManager` so the session is a
/// single source of truth rather than an analytics-private concept — both the
/// analytics pipeline and the frequency store read the same id.
class SessionManager {
  SessionManager._();

  static final SessionManager instance = SessionManager._();

  static const _uuid = Uuid();

  String? _sessionId;
  int? _lastActivityMs;
  int _timeoutMs = 30 * 60 * 1000;
  bool _initialized = false;

  final List<VoidCallback> _rotationListeners = <VoidCallback>[];

  /// The current session id. Defensive fallback mints an id if read before
  /// [initialize] (which the SDK always calls during startup).
  String get sessionId => _sessionId ??= _newId();

  /// Registers a callback fired whenever the session rotates mid-life (expiry or
  /// [reset]). Not fired for the initial session resolved in [initialize].
  void addRotationListener(VoidCallback listener) {
    if (!_rotationListeners.contains(listener)) _rotationListeners.add(listener);
  }

  void removeRotationListener(VoidCallback listener) =>
      _rotationListeners.remove(listener);

  /// Resolves the session from storage: reuses the persisted id when it is still
  /// within [timeoutMs] of the last recorded activity (so a relaunch continues
  /// the same session), otherwise starts a fresh one. Idempotent — only the
  /// first call resolves the session.
  Future<void> initialize(int timeoutMs) async {
    if (_initialized) return;
    _timeoutMs = timeoutMs;
    try {
      final storedId = PreferencesStore.instance.read<String>(_kSessionIdKey);
      final storedLast =
          PreferencesStore.instance.read<int>(_kSessionLastActivityKey);
      if (storedId != null &&
          storedId.isNotEmpty &&
          storedLast != null &&
          !_isExpired(storedLast)) {
        _sessionId = storedId;
        _lastActivityMs = storedLast;
      } else {
        // Brand-new session — no listeners are wired yet, so don't notify.
        await _rotate(notify: false);
      }
    } catch (error) {
      debugPrint('[Digia] SessionManager initialize failed: $error');
      _sessionId ??= _newId();
    }
    _initialized = true;
  }

  /// Records session activity (called on each tracked event). Rotates first when
  /// the prior session has gone idle past the timeout, so the activity is
  /// attributed to a fresh session.
  Future<void> touch() async {
    if (_isExpired(_lastActivityMs)) {
      await _rotate(notify: true);
      return;
    }
    _lastActivityMs = DateTime.now().millisecondsSinceEpoch;
    await PreferencesStore.instance
        .write<int>(_kSessionLastActivityKey, _lastActivityMs!);
  }

  /// Rotates the session if it has gone idle past the timeout (e.g. on app
  /// resume). No-op for a session that hasn't seen activity yet.
  Future<void> maybeExpire() async {
    if (_isExpired(_lastActivityMs)) {
      await _rotate(notify: true);
    }
  }

  /// Forces a new session (e.g. when the user identity changes).
  Future<void> reset() => _rotate(notify: true);

  bool _isExpired(int? lastActivityMs) {
    if (lastActivityMs == null) return false;
    return DateTime.now().millisecondsSinceEpoch - lastActivityMs >= _timeoutMs;
  }

  Future<void> _rotate({required bool notify}) async {
    _sessionId = _newId();
    _lastActivityMs = DateTime.now().millisecondsSinceEpoch;
    await PreferencesStore.instance
        .write<String>(_kSessionIdKey, _sessionId!);
    await PreferencesStore.instance
        .write<int>(_kSessionLastActivityKey, _lastActivityMs!);
    if (notify) {
      // Copy first so a listener that (un)registers during dispatch is safe.
      for (final listener in List<VoidCallback>.from(_rotationListeners)) {
        listener();
      }
    }
  }

  String _newId() => _uuid.v4();

  @visibleForTesting
  void resetForTest() {
    _sessionId = null;
    _lastActivityMs = null;
    _initialized = false;
    _rotationListeners.clear();
  }
}
