import 'interfaces/digia_cep_plugin.dart';
import 'internal/digia_instance.dart';
import 'models/digia_config.dart';

/// Public static facade for the Digia SDK.
///
/// All integration starts here. There is no instance to create or hold —
/// every call is a static method on [Digia].
///
/// ## Quick start
/// ```dart
/// // 1. Initialize once at app startup
/// Digia.initialize(DigiaConfig(apiKey: 'prod_xxxx'));
///
/// // 2. Register your CEP plugin after it is ready
/// Digia.register(CleverTapPlugin(instance: cleverTapInstance));
///
/// // 3. Wrap your app root with DigiaHost (in MaterialApp.builder)
/// MaterialApp(
///   navigatorObservers: [DigiaNavigatorObserver()],
///   builder: (context, child) => DigiaHost(child: child!),
/// )
///
/// // 4. Track screens (or use DigiaNavigatorObserver for automatic tracking)
/// Digia.setCurrentScreen('checkout');
/// ```
///
/// The internal singleton lives inside the SDK and is never exposed to
/// app developers — there is no [Digia] instance to null-check or hold.
abstract final class Digia {
  Digia._();

  /// Initializes the Digia SDK with the provided configuration.
  ///
  /// Call this once at application startup — typically in `main()` before
  /// `runApp()` — and `await` the result before any other Digia call.
  ///
  /// Internally this starts the real src services: SharedPreferences,
  /// NetworkClient, DSL config loading (remote or local asset depending on
  /// the [DigiaConfig.flavor]), and wires [DigiaUIManager].
  ///
  /// The SDK enters degraded mode if [register] is not subsequently called:
  /// all calls are accepted and logged, but no experiences are displayed.
  ///
  /// ```dart
  /// Future<void> main() async {
  ///   WidgetsFlutterBinding.ensureInitialized();
  ///   await Digia.initialize(DigiaConfig(apiKey: 'prod_xxxx'));
  ///   runApp(const MyApp());
  /// }
  /// ```
  static Future<void> initialize(DigiaConfig config) {
    return DigiaInstance.instance.initialize(config);
  }

  /// Registers a CEP plugin with the SDK.
  ///
  /// Call this after [initialize] and after the CEP SDK is ready.
  /// Decoupled from initialization deliberately — CEP SDKs initialize
  /// asynchronously and may be owned by a different team.
  ///
  /// Registering a new plugin automatically tears down the previously
  /// registered one.
  ///
  /// ```dart
  /// Digia.register(CleverTapPlugin(instance: cleverTapInstance));
  /// ```
  static void register(DigiaCEPPlugin plugin) {
    DigiaInstance.instance.register(plugin);
  }

  /// Informs Digia that the user has navigated to a new screen.
  ///
  /// This call is forwarded internally to the registered CEP plugin, which
  /// calls its own screen-tracking API (e.g. CleverTap.recordScreenView).
  /// Replace any existing CEP screen-tracking calls with this single call.
  ///
  /// For automatic screen tracking, add [DigiaNavigatorObserver] to
  /// [MaterialApp.navigatorObservers] instead.
  ///
  /// ```dart
  /// Digia.setCurrentScreen('checkout');
  /// ```
  static void setCurrentScreen(String name) {
    DigiaInstance.instance.setCurrentScreen(name);
  }
}
