import 'package:digia_engage/api/internal/event/engage_analytics_event.dart';
import 'package:digia_engage/api/models/analytics_config.dart';
import 'package:digia_engage/api/models/cep_trigger_payload.dart';
import 'package:digia_engage/api/models/digia_experience_event.dart';
import 'package:digia_engage/src/analytics/analytics_service.dart';
import 'package:digia_engage/src/preferences_store.dart';
import 'package:dio/dio.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeDio implements Dio {
  _FakeDio(this.onPost);

  final Future<Response<Object?>> Function(
    String path, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) onPost;

  @override
  Future<Response<T>> post<T>(
    String path, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) {
    return onPost(
      path,
      data: data,
      queryParameters: queryParameters,
      options: options,
      cancelToken: cancelToken,
      onSendProgress: onSendProgress,
      onReceiveProgress: onReceiveProgress,
    ) as Future<Response<T>>;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await PreferencesStore.instance.initialize();
    PackageInfo.setMockInitialValues(
      appName: 'TestApp',
      packageName: 'com.test.app',
      version: '1.0.0',
      buildNumber: '1',
      buildSignature: '',
    );
    await DigiaAnalyticsService.instance.resetForTest();
  });

  Response<Object?> _makeResponse({
    required int statusCode,
    required Map<String, dynamic> data,
  }) {
    return Response<Object?>(
      requestOptions: RequestOptions(path: ''),
      statusCode: statusCode,
      data: data,
    );
  }

  Response<Object?> _successResponse() {
    return _makeResponse(
      statusCode: 200,
      data: {'accepted': 1, 'rejected': 0, 'errors': []},
    );
  }

  Response<Object?> _serverErrorResponse() {
    return _makeResponse(
      statusCode: 500,
      data: {'message': 'internal server error'},
    );
  }

  Response<Object?> _partialFailureResponse(String failedEventId) {
    return _makeResponse(
      statusCode: 200,
      data: {
        'accepted': 1,
        'rejected': 1,
        'errors': [
          {'event_id': failedEventId, 'reason': 'invalid campaign_type'}
        ],
      },
    );
  }

  test('initializes analytics and persists anonymous id', () async {
    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(),
      'digia_test',
    );

    final anonymousId = DigiaAnalyticsService.instance.getAnonymousId();
    expect(anonymousId, isNotEmpty);

    final secondId = DigiaAnalyticsService.instance.getAnonymousId();
    expect(secondId, equals(anonymousId));
  });

  test('setUserId and clearUserId persist and reset session', () async {
    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(),
      'digia_test',
    );

    await DigiaAnalyticsService.instance.setUserId('user-123');
    expect(DigiaAnalyticsService.instance.userId, equals('user-123'));

    final sessionBefore = DigiaAnalyticsService.instance.sessionId;
    await DigiaAnalyticsService.instance.clearUserId();
    expect(DigiaAnalyticsService.instance.userId, isNull);
    expect(DigiaAnalyticsService.instance.sessionId, isNotEmpty);
    expect(
        DigiaAnalyticsService.instance.sessionId, isNot(equals(sessionBefore)));
  });

  test('queue drops oldest events when capacity is exceeded', () async {
    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(queueMaxEvents: 3),
      'digia_test',
    );

    for (var i = 0; i < 5; i++) {
      final payload = _buildPayload('event-$i');
      await DigiaAnalyticsService.instance.capture(
        const CarouselViewed(),
        payload,
      );
      await Future<void>.delayed(const Duration(milliseconds: 1));
    }

    final queueLength = await DigiaAnalyticsService.instance.getQueueLength();
    expect(queueLength, equals(3));

    final entries = await DigiaAnalyticsService.instance.debugQueueEntries();
    expect(entries.length, equals(3));

    final createdAtValues = entries
        .map((entry) => entry['created_at'] as int)
        .toList(growable: false);
    final ordered = [...createdAtValues]..sort();
    expect(createdAtValues, equals(ordered));
  });

  test('builds event payload with static context and identity fields',
      () async {
    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(),
      'digia_test',
    );

    final payload = _buildPayload('payload-1');
    await DigiaAnalyticsService.instance.capture(
      const CarouselViewed(),
      payload,
      campaignId: 'example-campaign',
    );

    final entries = await DigiaAnalyticsService.instance.debugQueueEntries();
    expect(entries, isNotEmpty);
    final event = entries.first['payload'] as Map<String, dynamic>;

    expect(event['event_name'], equals('Digia Experience Viewed'));
    expect(event['campaign_id'], equals('example-campaign'));
    expect((event['properties'] as Map)['sdk_version'], isNotNull);
    expect(event['anonymous_id'], isNotEmpty);
    expect(event['session_id'], isNotEmpty);
  });

  test('experience event names and flush behavior live in the event model', () {
    expect(const ExperienceImpressed().analyticsEventName,
        equals('Digia Experience Viewed'));
    expect(const ExperienceClicked(elementId: 'cta').analyticsEventName,
        equals('Digia Experience Clicked'));
    expect(const ExperienceDismissed().analyticsEventName,
        equals('Digia Experience Dismissed'));
    expect(const ExperienceCompleted().analyticsEventName,
        equals('Digia Experience Completed'));

    expect(const ExperienceImpressed().flushOnCapture, isFalse);
    expect(const ExperienceClicked().flushOnCapture, isFalse);
    expect(const ExperienceDismissed().flushOnCapture, isTrue);
    expect(const ExperienceCompleted().flushOnCapture, isTrue);
  });

  test('flushes immediately when queue reaches flushBatchSize', () async {
    var postCount = 0;
    final fakeDio = _FakeDio(
      (path,
          {data,
          queryParameters,
          options,
          cancelToken,
          onSendProgress,
          onReceiveProgress}) async {
        postCount += 1;
        return _makeResponse(
          statusCode: 200,
          data: {'accepted': 2, 'rejected': 0, 'errors': []},
        );
      },
    );

    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(flushBatchSize: 2, flushIntervalMs: 10000),
      'digia_test',
    );
    DigiaAnalyticsService.instance.dioFactory = () => fakeDio;

    await DigiaAnalyticsService.instance.capture(
      const CarouselViewed(),
      _buildPayload('payload-1'),
    );
    await DigiaAnalyticsService.instance.capture(
      const CarouselViewed(),
      _buildPayload('payload-2'),
    );

    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(0));
    expect(postCount, equals(1));
  });

  test('schedules flush after flushIntervalMs when threshold is not reached',
      () async {
    var postCount = 0;
    final fakeDio = _FakeDio(
      (path,
          {data,
          queryParameters,
          options,
          cancelToken,
          onSendProgress,
          onReceiveProgress}) async {
        postCount += 1;
        return _successResponse();
      },
    );

    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(flushBatchSize: 10, flushIntervalMs: 50),
      'digia_test',
    );
    DigiaAnalyticsService.instance.dioFactory = () => fakeDio;

    await DigiaAnalyticsService.instance.capture(
      const CarouselViewed(),
      _buildPayload('payload-1'),
    );

    await Future<void>.delayed(const Duration(milliseconds: 200));

    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(0));
    expect(postCount, equals(1));
  });

  test('explicit flush() dispatches pending events', () async {
    var postCount = 0;
    final fakeDio = _FakeDio(
      (path,
          {data,
          queryParameters,
          options,
          cancelToken,
          onSendProgress,
          onReceiveProgress}) async {
        postCount += 1;
        return _successResponse();
      },
    );

    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(flushBatchSize: 10, flushIntervalMs: 10000),
      'digia_test',
    );
    DigiaAnalyticsService.instance.dioFactory = () => fakeDio;

    await DigiaAnalyticsService.instance.capture(
      const CarouselViewed(),
      _buildPayload('payload-1'),
    );
    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(1));

    await DigiaAnalyticsService.instance.flush();

    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(0));
    expect(postCount, equals(1));
  });

  test('retries transient failures and preserves the event until success',
      () async {
    var callCount = 0;
    final fakeDio = _FakeDio(
      (path,
          {data,
          queryParameters,
          options,
          cancelToken,
          onSendProgress,
          onReceiveProgress}) async {
        callCount += 1;
        if (callCount == 1) {
          return _serverErrorResponse();
        }
        return _successResponse();
      },
    );

    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(flushBatchSize: 10, flushIntervalMs: 10000),
      'digia_test',
    );
    DigiaAnalyticsService.instance.dioFactory = () => fakeDio;
    DigiaAnalyticsService.instance.retryScheduleMs = [10, 20];

    await DigiaAnalyticsService.instance.capture(
      const CarouselViewed(),
      _buildPayload('payload-1'),
    );

    await DigiaAnalyticsService.instance.flush();
    expect(DigiaAnalyticsService.instance.retryAttempt, equals(1));
    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(1));

    await Future<void>.delayed(const Duration(milliseconds: 100));

    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(0));
    expect(DigiaAnalyticsService.instance.retryAttempt, equals(0));
    expect(callCount, equals(2));
  });

  test('appLifecycleChanged paused flushes pending events', () async {
    var postCount = 0;
    final fakeDio = _FakeDio(
      (path,
          {data,
          queryParameters,
          options,
          cancelToken,
          onSendProgress,
          onReceiveProgress}) async {
        postCount += 1;
        return _successResponse();
      },
    );

    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(flushBatchSize: 10, flushIntervalMs: 10000),
      'digia_test',
    );
    DigiaAnalyticsService.instance.dioFactory = () => fakeDio;

    await DigiaAnalyticsService.instance.capture(
      const CarouselViewed(),
      _buildPayload('payload-1'),
    );
    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(1));

    await DigiaAnalyticsService.instance
        .appLifecycleChanged(AppLifecycleState.paused);

    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(0));
    expect(postCount, equals(1));
  });

  test('initialize schedules flush timer when persisted queue exists',
      () async {
    var postCount = 0;
    final fakeDio = _FakeDio(
      (path,
          {data,
          queryParameters,
          options,
          cancelToken,
          onSendProgress,
          onReceiveProgress}) async {
        postCount += 1;
        return _successResponse();
      },
    );

    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(flushBatchSize: 10, flushIntervalMs: 50),
      'digia_test',
    );
    DigiaAnalyticsService.instance.dioFactory = () => fakeDio;

    await DigiaAnalyticsService.instance.capture(
      const CarouselViewed(),
      _buildPayload('payload-opened'),
    );
    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(1));

    await DigiaAnalyticsService.instance.resetForTest();

    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(flushBatchSize: 10, flushIntervalMs: 50),
      'digia_test',
    );
    DigiaAnalyticsService.instance.dioFactory = () => fakeDio;

    await Future<void>.delayed(const Duration(milliseconds: 150));

    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(0));
    expect(postCount, equals(1));
  });

  test('dismissed event does not flush inside analytics service', () async {
    var postCount = 0;
    final fakeDio = _FakeDio(
      (path,
          {data,
          queryParameters,
          options,
          cancelToken,
          onSendProgress,
          onReceiveProgress}) async {
        postCount += 1;
        return _successResponse();
      },
    );

    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(flushBatchSize: 100, flushIntervalMs: 10000),
      'digia_test',
    );
    DigiaAnalyticsService.instance.dioFactory = () => fakeDio;

    await DigiaAnalyticsService.instance.capture(
      const SurveyDismissed(),
      _buildPayload('payload-dismissed'),
    );

    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(1));
    expect(postCount, equals(0));
  });

  test('partial failure response removes accepted and rejected events',
      () async {
    var callCount = 0;
    String failedEventId = '';
    final fakeDio = _FakeDio(
      (path,
          {data,
          queryParameters,
          options,
          cancelToken,
          onSendProgress,
          onReceiveProgress}) async {
        callCount += 1;
        final body = data as Map<String, dynamic>;
        final eventIds = (body['events'] as List)
            .map((event) => event['event_id'] as String)
            .toList();
        failedEventId = eventIds.first;
        return _partialFailureResponse(failedEventId);
      },
    );

    await DigiaAnalyticsService.instance.initialize(
      const DigiaAnalyticsConfig(flushBatchSize: 10, flushIntervalMs: 10000),
      'digia_test',
    );
    DigiaAnalyticsService.instance.dioFactory = () => fakeDio;

    await DigiaAnalyticsService.instance.capture(
      const CarouselViewed(),
      _buildPayload('payload-1'),
    );
    await DigiaAnalyticsService.instance.capture(
      const CarouselClicked(elementId: 'cta'),
      _buildPayload('payload-2'),
      campaignId: 'digia-campaign-123',
    );

    await DigiaAnalyticsService.instance.flush();

    expect(await DigiaAnalyticsService.instance.getQueueLength(), equals(0));
    expect(callCount, equals(1));
  });
}

CEPTriggerPayload _buildPayload(String eventKey) {
  return CEPTriggerPayload(
    cepCampaignId: 'example-campaign',
    cepMetadata: {
      'key': eventKey,
      'type': 'guide',
      'displayStyle': 'modal',
      'anchorKey': 'button-1',
      'slotKey': 'slot-1',
    },
    campaignKey: eventKey,
    variables: {'user_name': 'test-user'},
  );
}
