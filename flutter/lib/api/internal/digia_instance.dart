import 'package:flutter/widgets.dart';

import '../../src/init/digia_ui.dart';
import '../../src/init/digia_ui_manager.dart';
import '../interfaces/digia_cep_delegate.dart';
import '../interfaces/digia_cep_plugin.dart';
import '../models/digia_config.dart';
import '../models/in_app_payload.dart';
import 'digia_overlay_controller.dart';

/// Internal singleton that backs the public [Digia] static facade.
///
/// This is an implementation detail of the SDK. App developers never
/// see or reference this class — all calls flow through [Digia].
class DigiaInstance with WidgetsBindingObserver implements DigiaCEPDelegate {
  DigiaInstance._();

  static final DigiaInstance _instance = DigiaInstance._();

  /// Internal accessor used only by [Digia] and [DigiaHost].
  static DigiaInstance get instance => _instance;

  DigiaConfig? _config;
  DigiaCEPPlugin? _activePlugin;
  bool _hostMounted = false;
  bool _initialized = false;

  /// Shared with [DigiaHost]. Created once, lives for the app lifetime.
  final DigiaOverlayController _controller = DigiaOverlayController();

  /// Exposed so [DigiaHost] can subscribe at mount time.
  DigiaOverlayController get controller => _controller;

  // ─── Lifecycle ─────────────────────────────────────────────────────────────

  Future<void> initialize(DigiaConfig config) async {
    if (_initialized) {
      debugPrint('[Digia] initialize() called more than once — ignored.');
      return;
    }
    _config = config;
    _initialized = true;

    WidgetsBinding.instance.addObserver(this);

    // ── Wire the real src services ────────────────────────────────────────
    // DigiaUI.initialize() sets up PreferencesStore, NetworkClient, and
    // loads the DSL config (app_config / functions) from the server or
    // local assets depending on the chosen Flavor.
    final digiaUI = await DigiaUI.initialize(config.toOptions());

    // DigiaUIManager is the singleton that the rest of the src framework
    // (DigiaUIApp, DigiaUIScope, analytics, font loading, etc.) reads
    // for the active SDK instance.
    DigiaUIManager().initialize(digiaUI);

    // Wire the event callback — when DigiaHost reports a user interaction,
    // route it to the active plugin.
    _controller.onEvent = (event, payload) {
      _activePlugin?.notifyEvent(event, payload);
    };

    _logIfVerbose('Digia initialized with apiKey=${config.apiKey}, '
        'environment=${config.environment.name}');
  }

  void register(DigiaCEPPlugin plugin) {
    if (!_initialized) {
      debugPrint(
        '[Digia] register() called before initialize(). '
        'Call Digia.initialize() first.',
      );
    }
    // Always tear down the previous plugin before replacing.
    _activePlugin?.teardown();
    _activePlugin = plugin;
    plugin.setup(this); // pass self as DigiaCEPDelegate

    _logIfVerbose('Plugin registered: ${plugin.identifier}');
  }

  void setCurrentScreen(String name) {
    if (_activePlugin == null) {
      _logDegradedWarning('setCurrentScreen("$name")');
      return;
    }
    _activePlugin!.forwardScreen(name);
    _logIfVerbose('Screen forwarded to plugin: $name');
  }

  // ─── Host mount tracking ───────────────────────────────────────────────────

  /// Called by [DigiaHost] when it mounts into the widget tree.
  void onHostMounted() {
    _hostMounted = true;
    _logIfVerbose('DigiaHost mounted.');
  }

  /// Called by [DigiaHost] when it is removed from the widget tree.
  void onHostUnmounted() {
    _hostMounted = false;
    _logIfVerbose('DigiaHost unmounted.');
  }

  // ─── DigiaCEPDelegate ──────────────────────────────────────────────────────

  @override
  void onExperienceReady(InAppPayload payload) {
    if (!_hostMounted) {
      debugPrint(
        '[Digia] WARNING: A campaign payload arrived but DigiaHost is not '
        'mounted. Wrap your app root with DigiaHost to display experiences.',
      );
    }
    // Routes payload to DigiaHost via the shared controller.
    _controller.show(payload);
  }

  @override
  void onExperienceInvalidated(String payloadId) {
    if (_controller.activePayload?.id == payloadId) {
      _controller.dismiss();
    }
  }

  // ─── WidgetsBindingObserver ────────────────────────────────────────────────

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // detached = permanent process destruction. Not paused (every background).
    if (state == AppLifecycleState.detached) {
      _activePlugin?.teardown();
      _activePlugin = null;
    }
  }

  // ─── Helpers ───────────────────────────────────────────────────────────────

  void _logIfVerbose(String message) {
    if (_config?.logLevel == DigiaLogLevel.verbose) {
      debugPrint('[Digia] $message');
    }
  }

  void _logDegradedWarning(String call) {
    debugPrint(
      '[Digia] WARNING: $call called but no CEP plugin is registered. '
      'Call Digia.register(plugin) after initialize(). '
      'The SDK is running in degraded mode — no experiences will be shown.',
    );
  }
}
