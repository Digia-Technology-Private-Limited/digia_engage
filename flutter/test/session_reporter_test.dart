import 'package:digia_engage/src/session/session_reporter.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

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
  Response<Object?> ok() => Response<Object?>(
        requestOptions: RequestOptions(path: ''),
        statusCode: 200,
        data: const {},
      );

  test('posts the session to the /session endpoint with id, identity, context',
      () async {
    String? postedPath;
    Object? postedBody;
    Options? postedOptions;
    final dio = _FakeDio((path,
        {data,
        queryParameters,
        options,
        cancelToken,
        onSendProgress,
        onReceiveProgress}) async {
      postedPath = path;
      postedBody = data;
      postedOptions = options;
      return ok();
    });

    final reporter = SessionReporter(
      apiKey: () => 'proj_123',
      sessionId: () => 'sess_abc',
      anonymousId: () => 'anon_1',
      userId: () => 'user_9',
      context: () => {'sdk_platform': 'flutter'},
      dioFactory: () => dio,
    );

    await reporter.report();

    expect(postedPath, endsWith('/engage/sdk/session'));
    final body = postedBody as Map<String, dynamic>;
    expect(body['session_id'], 'sess_abc');
    expect(body['anonymous_id'], 'anon_1');
    expect(body['user_id'], 'user_9');
    expect(body['properties'], {'sdk_platform': 'flutter'});
    expect(postedOptions?.headers?['X-Digia-Project-Id'], 'proj_123');
  });

  test('omits user_id when there is no user', () async {
    Object? postedBody;
    final dio = _FakeDio((path,
        {data,
        queryParameters,
        options,
        cancelToken,
        onSendProgress,
        onReceiveProgress}) async {
      postedBody = data;
      return ok();
    });

    final reporter = SessionReporter(
      apiKey: () => 'proj_123',
      sessionId: () => 'sess_abc',
      anonymousId: () => 'anon_1',
      userId: () => null,
      context: () => const {},
      dioFactory: () => dio,
    );

    await reporter.report();

    expect((postedBody as Map<String, dynamic>).containsKey('user_id'), isFalse);
  });

  test('swallows transport errors', () async {
    final dio = _FakeDio((path,
        {data,
        queryParameters,
        options,
        cancelToken,
        onSendProgress,
        onReceiveProgress}) async {
      throw DioException(requestOptions: RequestOptions(path: ''));
    });

    final reporter = SessionReporter(
      apiKey: () => 'proj_123',
      sessionId: () => 'sess_abc',
      anonymousId: () => 'anon_1',
      userId: () => null,
      context: () => const {},
      dioFactory: () => dio,
    );

    // Should not throw.
    await reporter.report();
  });
}
