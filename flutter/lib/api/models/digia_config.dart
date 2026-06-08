import '../../../src/dui_dev_config.dart';
import '../../../src/init/environment.dart';
import '../../../src/init/flavor.dart';
import '../../../src/init/options.dart';
import '../internal/action/engage_action_context.dart';

/// Log verbosity levels for the Digia SDK.
enum DigiaLogLevel {
  /// No logs emitted.
  none,

  /// Only error-level messages.
  error,

  /// All messages including debug info.
  verbose,
}

/// Target environment for the SDK.
enum DigiaEnvironment {
  /// Live, user-facing environment.
  production,

  /// Testing and integration environment.
  sandbox,
}

/// Configuration parameters required to initialize the Digia SDK.
///
/// Pass an instance to [Digia.initialize] at application startup. The property
/// set mirrors the native (Android / iOS) SDKs.
///
/// ## Minimal setup
/// ```dart
/// await Digia.initialize(DigiaConfig(apiKey: 'prod_xxxx'));
/// ```
///
/// ## With overrides
/// ```dart
/// await Digia.initialize(DigiaConfig(
///   apiKey: 'dev_xxxx',
///   environment: DigiaEnvironment.sandbox,
///   baseUrl: 'https://dev.digia.tech',
///   fontFamily: 'Inter',
/// ));
/// ```
class DigiaConfig {
  /// Environment-specific API key from the Digia dashboard.
  final String apiKey;

  /// Log verbosity. Defaults to [DigiaLogLevel.error].
  final DigiaLogLevel logLevel;

  /// Target environment. Defaults to [DigiaEnvironment.production].
  final DigiaEnvironment environment;

  /// Optional override for the engage API host (e.g.
  /// `https://app.digia.tech`). When null, the host is derived from
  /// [environment]. Mirrors Android's `DigiaConfig.baseUrl`.
  final String? baseUrl;

  /// Optional global font family applied to all Digia-rendered text
  /// (e.g. nudge titles, bodies, and button labels). Resolved as a Flutter
  /// font family registered in the host app's `pubspec.yaml`.
  /// Mirrors Android's `DigiaConfig.fontFamily`.
  final String? fontFamily;

  /// Optional host override for engage **navigation** actions — open URL / deep
  /// link ([LinkAction], with their target). Inspect it + the
  /// [EngageActionContext] and return `true` to handle the link yourself (the
  /// SDK then skips opening it); return `false`/`null` to let the default run.
  /// Hide/share are always handled by the SDK and never reach this hook.
  /// Mirrors React Native's `onAction`.
  final EngageActionInterceptor? onAction;

  DigiaConfig({
    required this.apiKey,
    this.logLevel = DigiaLogLevel.error,
    this.environment = DigiaEnvironment.production,
    this.baseUrl,
    this.fontFamily,
    this.onAction,
  });

  /// Builds the internal [DigiaUIOptions] used by [DigiaUI.initialize].
  ///
  /// This is an internal conversion — app developers never call this. The DSL
  /// layer's flavor and developer config are derived from [environment]; they
  /// are not part of the public surface.
  DigiaUIOptions toOptions() {
    return DigiaUIOptions.internal(
      accessKey: apiKey,
      flavor: Flavor.debug(
        environment: environment == DigiaEnvironment.production
            ? Environment.production
            : Environment.development,
      ),
      developerConfig: const DeveloperConfig(),
    );
  }
}
