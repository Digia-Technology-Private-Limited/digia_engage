import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../../api/internal/digia_endpoints.dart';

/// Reports a session to the Digia backend (the `/session` endpoint).
///
/// This is session *telemetry* — distinct from the [SessionManager], which owns
/// the session *lifecycle* (when a session starts, expires, or rotates). The
/// reporter is wired by `DigiaInstance` as a [SessionManager] rotation listener
/// (plus an initial report at startup), and only when analytics is enabled — so
/// turning analytics off also stops session telemetry.
///
/// Its inputs are supplied as providers so it stays decoupled from where the
/// session id, device context, and identity actually live (analytics owns the
/// device context + identity; the config owns the api key).
class SessionReporter {
  final String? Function() apiKey;
  final String Function() sessionId;
  final String Function() anonymousId;
  final String? Function() userId;

  /// The static device/app/SDK context attached to the report (the same context
  /// analytics stamps on every event).
  final Map<String, dynamic> Function() context;

  /// Test seam for the HTTP client. Production uses a fresh [Dio].
  final Dio Function()? dioFactory;

  SessionReporter({
    required this.apiKey,
    required this.sessionId,
    required this.anonymousId,
    required this.userId,
    required this.context,
    this.dioFactory,
  });

  Future<void> report() async {
    try {
      final user = userId();
      final body = <String, dynamic>{
        'session_id': sessionId(),
        'anonymous_id': anonymousId(),
        if (user != null) 'user_id': user,
        'occurred_at': DateTime.now().toUtc().toIso8601String(),
        'properties': context(),
      };
      final dio = dioFactory?.call() ?? Dio();
      final response = await dio.post<dynamic>(
        DigiaEndpoints.session,
        data: body,
        options: Options(
          headers: {
            'Content-Type': 'application/json',
            'X-Digia-Project-Id': apiKey(),
          },
          validateStatus: (_) => true,
        ),
      );
      if (kDebugMode) {
        debugPrint(
            '[Digia] session reported: HTTP ${response.statusCode} sessionId=${sessionId()} anonymousId=${anonymousId()}');
      }
    } catch (e) {
      if (kDebugMode) debugPrint('[Digia] session report failed: $e');
    }
  }
}
