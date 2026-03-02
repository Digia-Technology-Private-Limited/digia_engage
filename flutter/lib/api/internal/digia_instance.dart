import 'package:flutter/widgets.dart';

import '../../digia_ui.dart';
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

  NavigatorState? _navigator;

  void attachNavigator(NavigatorState navigator) {
    _navigator = navigator;
  }

  NavigatorState? get navigator => _navigator;

  DigiaConfig? _config;
  DigiaCEPPlugin? _activePlugin;
  bool _hostMounted = false;
  bool _initialized = false;

  /// Shared with [DigiaHost]. Created once, lives for the app lifetime.
  final DigiaOverlayController _controller = DigiaOverlayController();

  /// Controller for inline campaigns, notifies when they change.
  final InlineCampaignController inlineController = InlineCampaignController();

  /// Exposed so [DigiaHost] can subscribe at mount time.
  DigiaOverlayController get controller => _controller;

  /// Gets the active inline campaign for a given placement key, if any.
  InAppPayload? getInlineCampaign(String placementKey) =>
      inlineController.getCampaign(placementKey);

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
    // Initialize the Digia UI manager with the provided configuration
    DigiaUIManager().initialize(digiaUI);

    // Initialize global app state with configuration from DSL
    DUIAppState().init(digiaUI.dslConfig.appState ?? []);

    // Set up the UI factory with custom resources and providers
    DUIFactory().initialize(
        // pageConfigProvider: pageConfigProvider,
        // icons: icons,
        // images: {
        //   ...(DigiaUIManager().assetImages.asMap().map((k, v) => MapEntry(
        //       v.assetData.localPath,
        //       NetworkImage(
        //           '${v.assetData.image?.baseUrl}${v.assetData.image?.path}')))),
        //   ...?images
        // },
        // fontFactory: fontFactory,
        );

    // Apply environment variables from DigiaUIApp if provided
    // if (widget.environmentVariables != null) {
    //   DUIFactory().setEnvironmentVariables(widget.environmentVariables!);
    // }

    // Wire the event callback — when DigiaHost reports a user interaction,
    // route it to the active plugin.
    _controller.onEvent = (event, payload) {
      _activePlugin?.notifyEvent(event, payload);
    };

    // Wire the same callback for inline campaigns reported by DigiaSlot.
    inlineController.onEvent = (event, payload) {
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

    final displayType =
        (payload.content['type'] as String? ?? 'inline').toLowerCase();
    final placementId = payload.content['placementId'] as String?;

    if (displayType == 'inline' && placementId != null) {
      // Store inline campaign for DigiaSlot to render.
      inlineController.setCampaign(placementId, payload);
    } else {
      // Modal campaigns: route to DigiaHost via the shared controller.
      _controller.show(payload);
    }
  }

  @override
  void onExperienceInvalidated(String payloadId) {
    // Check if it's an active overlay.
    if (_controller.activePayload?.id == payloadId) {
      _controller.dismiss();
    }
    // Check if it's an inline campaign.
    inlineController.removeCampaign(payloadId);
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

/// Internal controller for inline campaigns.
class InlineCampaignController extends ChangeNotifier {
  final Map<String, InAppPayload> _campaigns = {};

  /// Set by [DigiaInstance] at init time.
  /// [DigiaSlot] calls this when a user interaction event occurs
  /// (impression, dismiss). [DigiaInstance] forwards it to the active plugin.
  void Function(DigiaExperienceEvent, InAppPayload)? onEvent;

  InAppPayload? getCampaign(String placementKey) => _campaigns[placementKey];

  void setCampaign(String placementKey, InAppPayload payload) {
    _campaigns[placementKey] = payload;
    notifyListeners();
  }

  /// Server-driven removal — called from [DigiaInstance.onExperienceInvalidated].
  /// Matches by placement key (which equals the payloadId stored by the server).
  void removeCampaign(String placementId) {
    _campaigns.removeWhere((key, payload) => key == placementId);
    notifyListeners();
  }

  /// User-driven removal — called by [DigiaSlot] when the user explicitly
  /// closes the inline content (e.g., taps a close CTA inside the campaign).
  void dismissCampaign(String placementKey) {
    _campaigns.remove(placementKey);
    notifyListeners();
  }
}
