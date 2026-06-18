import 'dart:async';
import 'dart:math';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:uuid/uuid.dart';

import '../../api/internal/digia_endpoints.dart';
import '../../api/models/analytics_config.dart';
import '../../api/models/cep_trigger_payload.dart';
import '../../api/models/digia_experience_event.dart';
import '../sdk_version.dart';
import 'analytics_device_info.dart';
import 'analytics_identity_manager.dart';
import 'analytics_queue.dart';

/// Main analytics service for Digia engagement tracking.
///
/// Handles event capture, local persistence, batching, and server dispatch
/// with automatic retry logic and session management.
class DigiaAnalyticsService {
  DigiaAnalyticsService._();

  static final DigiaAnalyticsService instance = DigiaAnalyticsService._();

  final _uuid = const Uuid();
  final _queue = AnalyticsQueue();
  final _identity = AnalyticsIdentityManager();

  bool _initialized = false;
  bool _enabled = true;
  DigiaAnalyticsConfig _config = const DigiaAnalyticsConfig();
  Map<String, dynamic> _staticContext = const {};
  String? _apiKey;

  Timer? _flushTimer;
  Timer? _retryTimer;
  bool _isDispatching = false;
  int _retryAttempt = 0;

  @visibleForTesting
  Dio Function()? dioFactory;

  @visibleForTesting
  List<int>? retryScheduleMs;

  /// Initializes analytics service with configuration and API key
  Future<void> initialize(
    DigiaAnalyticsConfig config,
    String apiKey,
  ) async {
    _config = config;
    _enabled = _config.enabled;
    _apiKey = apiKey;

    if (!_enabled || _initialized) return;

    try {
      _staticContext = await _buildStaticContext();
      await _identity.initialize(_config.sessionTimeoutMs);
      _identity.onSessionRotated = _reportSession;
      _initialized = true;

      if (await _queue.length() > 0) {
        _scheduleTimer();
      }
      unawaited(_reportSession());
    } catch (error) {
      _log('[Digia Analytics] initialize failed: $error');
    }
  }

  @visibleForTesting
  Future<void> resetForTest() async {
    _flushTimer?.cancel();
    _retryTimer?.cancel();
    _flushTimer = null;
    _retryTimer = null;
    _isDispatching = false;
    _retryAttempt = 0;
    _initialized = false;
    _enabled = true;
    _apiKey = null;
  }

  bool get isEnabled => _enabled;

  String getAnonymousId() {
    return _identity.anonymousId;
  }

  String? get userId => _identity.userId;

  String get sessionId => _identity.sessionId;

  Future<int> getQueueLength() async {
    try {
      return await _queue.length();
    } catch (_) {
      return 0;
    }
  }

  Future<List<Map<String, dynamic>>> debugQueueEntries() async {
    try {
      return (await _queue.peek(_config.queueMaxEvents))
          .map((entry) => entry.toJson())
          .toList(growable: false);
    } catch (_) {
      return const [];
    }
  }

  /// Sets the user ID for analytics attribution
  Future<void> setUserId(String userId) async {
    if (!_enabled) return;
    try {
      await _identity.setUserId(userId);
    } catch (error) {
      _log('[Digia Analytics] setUserId failed: $error');
    }
  }

  /// Clears the user ID and resets session
  Future<void> clearUserId() async {
    if (!_enabled) return;
    try {
      await _identity.clearUserId();
    } catch (error) {
      _log('[Digia Analytics] clearUserId failed: $error');
    }
  }

  /// Immediately flushes all pending events to server
  Future<void> flush() async {
    if (!_enabled) return;
    try {
      await _cancelTimers();
      await _dispatchPending();
    } catch (error) {
      _log('[Digia Analytics] flush failed: $error');
    }
  }

  /// Responds to app lifecycle changes for session and dispatch management
  Future<void> appLifecycleChanged(AppLifecycleState state) async {
    if (!_enabled) return;
    try {
      if (state == AppLifecycleState.resumed) {
        await _identity.maybeExpireSession();
      }

      if (state == AppLifecycleState.paused ||
          state == AppLifecycleState.inactive ||
          state == AppLifecycleState.detached) {
        await _dispatchPending();
      }
    } catch (error) {
      _log('[Digia Analytics] appLifecycleChanged failed: $error');
    }
  }

  /// Captures an experience event with campaign context
  Future<void> captureExperienceEvent(
    DigiaExperienceEvent event,
    CEPTriggerPayload payload, {
    String? campaignType,
    String? campaignId,
  }) async {
    if (!_enabled) return;

    try {
      final occurredAt = DateTime.now().toUtc().toIso8601String();
      final eventName = event.analyticsEventName;
      final eventId = _uuid.v4();
      await _identity.captureEventTime();

      final eventPayload = _buildEventPayload(
        eventId: eventId,
        eventName: eventName,
        occurredAt: occurredAt,
        payload: payload,
        event: event,
        campaignType: campaignType,
        campaignId: campaignId,
      );

      await _queue.appendEvent(eventPayload, _config.queueMaxEvents);

      if (await _queue.length() >= _config.flushBatchSize) {
        await flush();
      } else {
        _scheduleTimer();
      }
    } catch (error) {
      _log('[Digia Analytics] captureExperienceEvent failed: $error');
    }
  }

  Future<void> _cancelTimers() async {
    _flushTimer?.cancel();
    _flushTimer = null;
    _retryTimer?.cancel();
    _retryTimer = null;
  }

  void _scheduleTimer({int? minDelayMs}) {
    if (_flushTimer != null || _isDispatching) {
      return;
    }

    final delayMs =
        max(_config.flushIntervalMs, minDelayMs ?? _config.flushIntervalMs);
    _flushTimer = Timer(Duration(milliseconds: delayMs), () {
      _flushTimer = null;
      _dispatchPending();
    });
  }

  Future<void> _dispatchPending() async {
    if (_isDispatching) {
      return;
    }

    _flushTimer?.cancel();
    _flushTimer = null;

    _isDispatching = true;
    try {
      final batch = await _queue.peek(_config.maxBatchSize);

      if (batch.isEmpty) {
        _retryAttempt = 0;
        return;
      }

      await _queue.incrementAttempt(batch.map((e) => e.eventId).toList());
      final response = await _sendBatch(batch);
      final result = _parseResponse(response);

      if (result != null) {
        await _handleBatchResponse(batch, result);
        _retryAttempt = 0;
        if (await _queue.length() > 0) {
          _scheduleTimer(minDelayMs: 15000);
        }
      } else if (_isServerError(response)) {
        await _scheduleRetry();
      } else {
        await _queue
            .removeByEventIds(batch.map((entry) => entry.eventId).toList());
        _retryAttempt = 0;
        if (await _queue.length() > 0) {
          _scheduleTimer(minDelayMs: 15000);
        }
      }
    } catch (error) {
      _log('[Digia Analytics] Dispatch failed: $error');
      await _scheduleRetry();
    } finally {
      _isDispatching = false;
    }
  }

  Future<void> _scheduleRetry() async {
    _retryTimer?.cancel();
    _retryAttempt += 1;

    final delayMs = _retryDelayMs(_retryAttempt);
    _retryTimer = Timer(Duration(milliseconds: delayMs), () {
      _retryTimer = null;
      _dispatchPending();
    });
  }

  @visibleForTesting
  int get retryAttempt => _retryAttempt;

  int _retryDelayMs(int attempt) {
    if (retryScheduleMs != null) {
      final index = (attempt - 1).clamp(0, retryScheduleMs!.length - 1);
      return retryScheduleMs![index];
    }
    // Exponential backoff: 1s, 2s, 4s, 8s, 16s (capped)
    return min(1000 * (1 << (attempt - 1)), 16000);
  }

  Future<void> _reportSession() async {
    if (!_enabled) return;
    try {
      final body = <String, dynamic>{
        'session_id': _identity.sessionId,
        'anonymous_id': _identity.anonymousId,
        if (_identity.userId != null) 'user_id': _identity.userId,
        'occurred_at': DateTime.now().toUtc().toIso8601String(),
        'properties': _staticContext,
      };
      final dio = dioFactory?.call() ?? Dio();
      final response = await dio.post<dynamic>(
        DigiaEndpoints.session,
        data: body,
        options: Options(
          headers: {
            'Content-Type': 'application/json',
            'X-Digia-Project-Id': _apiKey,
          },
          validateStatus: (_) => true,
        ),
      );
      _log(
          '[Digia Analytics] session reported: HTTP ${response.statusCode} sessionId=${_identity.sessionId} anonymousId=${_identity.anonymousId}');
    } catch (e) {
      _log('[Digia Analytics] session report failed: $e');
    }
  }

  Future<Response<Object?>> _sendBatch(List<AnalyticsQueueEntry> batch) async {
    final dio = dioFactory?.call() ?? Dio();
    final bodyMap = {
      'events': batch.map((entry) => entry.payload).toList(),
    };

    return dio.post(
      DigiaEndpoints.track,
      data: bodyMap,
      options: Options(
        headers: {
          'Content-Type': 'application/json',
          'X-Digia-Project-Id': _apiKey,
          'X-Digia-Device-Id': _identity.anonymousId,
        },
        validateStatus: (_) => true,
      ),
    );
  }

  _AnalyticsBatchResponse? _parseResponse(Response<Object?> response) {
    try {
      if (response.statusCode != 200 && response.statusCode != 207) {
        return null;
      }

      final body = response.data;
      if (body is! Map<String, dynamic>) {
        return null;
      }

      final accepted = (body['accepted'] as int?) ?? 0;
      final rejected = (body['rejected'] as int?) ?? 0;
      final errors = <_AnalyticsBatchError>[];

      final errorsList = body['errors'] as List<dynamic>?;
      if (errorsList != null) {
        for (final errorObj in errorsList) {
          if (errorObj is Map<String, dynamic>) {
            final eventId = errorObj['event_id'] as String?;
            final reason = errorObj['reason'] as String?;
            if (eventId != null && reason != null) {
              errors.add(_AnalyticsBatchError(eventId, reason));
            }
          }
        }
      }

      return _AnalyticsBatchResponse(
        accepted: accepted,
        rejected: rejected,
        errors: errors,
      );
    } catch (_) {
      return null;
    }
  }

  bool _isServerError(Response<Object?> response) =>
      response.statusCode != null && response.statusCode! >= 500;

  Future<void> _handleBatchResponse(
    List<AnalyticsQueueEntry> batch,
    _AnalyticsBatchResponse result,
  ) async {
    final eventIds = batch.map((entry) => entry.eventId).toList();

    if (result.errors.isNotEmpty) {
      for (final error in result.errors) {
        _log(
          '[Digia Analytics] Dropping event ${error.eventId}: ${error.reason}',
        );
      }
    }

    await _queue.removeByEventIds(eventIds);
  }

  Map<String, dynamic> _buildEventPayload({
    required String eventId,
    required String eventName,
    required String occurredAt,
    required CEPTriggerPayload payload,
    required DigiaExperienceEvent event,
    String? campaignType,
    String? campaignId,
  }) {
    final eventSpecific = <String, dynamic>{};
    if (event is ExperienceClicked) {
      eventSpecific['element_id'] = event.elementId;
    } else if (event is DigiaQuestionAnswered) {
      eventSpecific['element_id'] = event.stepId;
      if (event.answer.isNotEmpty) eventSpecific['answer'] = event.answer;
    }

    final metadata = payload.cepMetadata;
    if (metadata['displayStyle'] is String) {
      eventSpecific['display_style'] = metadata['displayStyle'];
    }
    if (metadata['anchorKey'] is String) {
      eventSpecific['anchor_key'] = metadata['anchorKey'];
    }
    if (metadata['slotKey'] is String) {
      eventSpecific['slot_key'] = metadata['slotKey'];
    }

    final mergedProperties = <String, dynamic>{
      ...eventSpecific,
      ..._staticContext,
    };

    if (payload.variables != null && payload.variables!.isNotEmpty) {
      mergedProperties['variables'] = payload.variables;
    }

    mergedProperties.removeWhere((_, v) => v == null);

    final payloadMap = {
      'event_id': eventId,
      'event_name': eventName,
      'occurred_at': occurredAt,
      'campaign_id': campaignId,
      'campaign_key': payload.campaignKey,
      'campaign_type': campaignType,
      'properties': mergedProperties,
      'anonymous_id': _identity.anonymousId,
      'session_id': _identity.sessionId,
      if (_identity.userId != null) 'user_id': _identity.userId,
    };

    return payloadMap;
  }

  /// Builds static context with device, OS, and app information
  Future<Map<String, dynamic>> _buildStaticContext() async {
    final packageInfo = await PackageInfo.fromPlatform();
    final appVersion = packageInfo.version;
    final locale = _getLocale();
    final deviceInfo = await AnalyticsDeviceInfo.getDeviceInfo();
    final devicePlatform = AnalyticsDeviceInfo.getDevicePlatform();

    return {
      //   s=schema | b=binding | p=platform | c=core/engine version
      'sdk_version': 's=1|b=flutter|p=$devicePlatform|c=$packageVersion',
      'sdk_platform': 'flutter',
      'device_platform': devicePlatform,
      'app_version': appVersion,
      'app_locale': locale,
      'os_version': AnalyticsDeviceInfo.formatOsVersion(),
      if (deviceInfo['device_make'] != null)
        'device_make': deviceInfo['device_make'],
      if (deviceInfo['device_model'] != null)
        'device_model': deviceInfo['device_model'],
    };
  }

  String _getLocale() {
    try {
      return WidgetsBinding.instance.window.locale.toString();
    } catch (_) {
      return 'unknown';
    }
  }

  void _log(String message) {
    if (kDebugMode) {
      debugPrint(message);
    }
  }
}

class _AnalyticsBatchError {
  final String eventId;
  final String reason;

  _AnalyticsBatchError(this.eventId, this.reason);
}

class _AnalyticsBatchResponse {
  final int accepted;
  final int rejected;
  final List<_AnalyticsBatchError> errors;

  _AnalyticsBatchResponse({
    required this.accepted,
    required this.rejected,
    required this.errors,
  });
}
