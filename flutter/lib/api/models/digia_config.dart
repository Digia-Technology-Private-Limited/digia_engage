import '../../../src/dui_dev_config.dart';
import '../../../src/init/environment.dart';
import '../../../src/init/flavor.dart';
import '../../../src/init/options.dart';
import '../../../src/network/netwok_config.dart';

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
/// Pass an instance to [Digia.initialize] at application startup.
///
/// ## Minimal setup (debug / development)
/// ```dart
/// await Digia.initialize(DigiaConfig(apiKey: 'prod_xxxx'));
/// ```
///
/// ## Production / release setup (local bundle assets)
/// ```dart
/// await Digia.initialize(DigiaConfig(
///   apiKey: 'prod_xxxx',
///   flavor: Flavor.release(
///     initStrategy: NetworkFirstStrategy(),
///     appConfigPath: 'assets/app_config.json',
///     functionsPath: 'assets/functions.json',
///   ),
/// ));
/// ```
///
/// ## Advanced — custom developer config, proxy, inspector
/// ```dart
/// await Digia.initialize(DigiaConfig(
///   apiKey: 'dev_xxxx',
///   environment: DigiaEnvironment.sandbox,
///   developerConfig: DeveloperConfig(
///     proxyUrl: '192.168.1.100:8888',
///     baseUrl: 'https://staging.digia.tech/api/v1',
///   ),
/// ));
/// ```
class DigiaConfig {
  /// Environment-specific API key from the Digia dashboard.
  final String apiKey;

  /// Log verbosity. Defaults to [DigiaLogLevel.error].
  final DigiaLogLevel logLevel;

  /// Target environment. Defaults to [DigiaEnvironment.production].
  final DigiaEnvironment environment;

  /// Flavor that controls how the SDK loads its DSL configuration.
  ///
  /// - [Flavor.debug] — fetches config from the server on every launch
  ///   (default for development).
  /// - [Flavor.staging] — staging server, remote config.
  /// - [Flavor.versioned] — pin to a specific DSL config version.
  /// - [Flavor.release] — loads config from bundled local assets
  ///   (recommended for production builds).
  ///
  /// Defaults to [Flavor.debug] when omitted.
  final Flavor flavor;

  /// Optional network configuration — timeouts, default HTTP headers.
  /// Defaults to [NetworkConfiguration.withDefaults] when omitted.
  final NetworkConfiguration? networkConfiguration;

  /// Optional developer configuration for proxies, inspectors, and
  /// custom backend URLs. Defaults to [DeveloperConfig] with production
  /// base URL when omitted.
  final DeveloperConfig? developerConfig;

  DigiaConfig({
    required this.apiKey,
    this.logLevel = DigiaLogLevel.error,
    this.environment = DigiaEnvironment.production,
    Flavor? flavor,
    this.networkConfiguration,
    this.developerConfig,
  }) : flavor = flavor ??
            Flavor.debug(
              environment: environment == DigiaEnvironment.production
                  ? Environment.production
                  : Environment.development,
            );

  /// Builds the internal [DigiaUIOptions] used by [DigiaUI.initialize].
  ///
  /// This is an internal conversion — app developers never call this.
  DigiaUIOptions toOptions() {
    return DigiaUIOptions.internal(
      accessKey: apiKey,
      flavor: flavor,
      networkConfiguration: networkConfiguration,
      developerConfig: developerConfig ?? const DeveloperConfig(),
    );
  }
}
