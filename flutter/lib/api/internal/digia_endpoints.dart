import '../models/digia_config.dart';

/// Centralised URL resolver for all Digia SDK endpoints.
///
/// Call [configure] once at SDK init (before any network call). All endpoint
/// getters then return the fully-qualified URL without needing a [DigiaConfig].
class DigiaEndpoints {
  static const _production = 'https://app.digia.tech';
  static const _sandbox = 'https://dev.digia.tech';

  static String _baseUrl = _production;

  static void configure(DigiaConfig config) {
    final explicit = config.baseUrl;
    if (explicit != null && explicit.isNotEmpty) {
      _baseUrl = explicit.replaceAll(RegExp(r'/+$'), '');
    } else {
      _baseUrl = config.environment == DigiaEnvironment.sandbox
          ? _sandbox
          : _production;
    }
  }

  /// Resets to production default. Use in tests only.
  static void resetForTest([String? baseUrl]) {
    _baseUrl = baseUrl ?? _production;
  }

  static String get campaigns  => '$_baseUrl/api/v1/engage/sdk/getCampaigns';
  static String get track      => '$_baseUrl/api/v1/engage/sdk/track';
  static String get session    => '$_baseUrl/api/v1/engage/sdk/session';
  static String get submission => '$_baseUrl/api/v1/engage/sdk/recordSubmission';
}
