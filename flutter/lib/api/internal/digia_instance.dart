import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import '../../digia_ui.dart';
import 'digia_overlay_controller.dart';
import 'sdk_state.dart';

/// Internal singleton that backs the public [Digia] static facade.
class DigiaInstance with WidgetsBindingObserver implements DigiaCEPDelegate {
  DigiaInstance._();

  static final DigiaInstance _instance = DigiaInstance._();
  static DigiaInstance get instance => _instance;

  NavigatorState? _navigator;
  DigiaConfig? _config;
  DigiaCEPPlugin? _activePlugin;
  String? _currentScreen;
  InAppPayload? _pendingPayload;
  bool _hostMounted = false;
  bool _initialized = false;

  final ValueNotifier<SDKState> _sdkState =
      ValueNotifier<SDKState>(SDKState.notInitialized);
  ValueListenable<SDKState> get sdkStateListenable => _sdkState;
  SDKState get sdkState => _sdkState.value;

  final DigiaOverlayController _controller = DigiaOverlayController();
  DigiaOverlayController get controller => _controller;

  void attachNavigator(NavigatorState navigator) {
    _navigator = navigator;
  }

  NavigatorState? get navigator => _navigator;

  Future<void> initialize(DigiaConfig config) async {
    if (_initialized) {
      debugPrint('[Digia] initialize() called more than once — ignored.');
      return;
    }
    _setState(SDKState.initializing);
    _config = config;

    WidgetsBinding.instance.addObserver(this);
    _controller.onEvent = (event, payload) {
      _activePlugin?.notifyEvent(event, payload);
    };

    try {
      final digiaUI = await DigiaUI.initialize(config.toOptions());
      DigiaUIManager().initialize(digiaUI);
      DUIAppState().init(digiaUI.dslConfig.appState ?? []);
      DUIFactory().initialize();

      _initialized = true;
      _setState(SDKState.ready);
      _activePlugin?.healthCheck();
      _flushPendingPayloadIfAny();
      _logIfVerbose('Digia initialized with apiKey=${config.apiKey}.');
    } catch (error) {
      _setState(SDKState.failed);
      debugPrint('[Digia] initialize() failed: $error');
    }
  }

  void register(DigiaCEPPlugin plugin) {
    _activePlugin?.teardown();
    _activePlugin = plugin;
    debugPrint('[Digia] register() plugin=${plugin.identifier}');
    plugin.setup(this);
    if (_currentScreen != null) {
      debugPrint(
        '[Digia] register() forwarding existing screen=$_currentScreen',
      );
      plugin.forwardScreen(_currentScreen!);
    }
    plugin.healthCheck();
    _logIfVerbose('Plugin registered: ${plugin.identifier}');
  }

  void setCurrentScreen(String name) {
    _currentScreen = name;
    debugPrint('[Digia] setCurrentScreen($name)');
    _activePlugin?.forwardScreen(name);
  }

  void onHostMounted() {
    _hostMounted = true;
  }

  void onHostUnmounted() {
    _hostMounted = false;
  }

  @override
  void onCampaignTriggered(InAppPayload payload) {
    debugPrint(
      '[Digia] 📨 onCampaignTriggered '
      'id=${payload.id} command=${payload.content['command']} '
      'screenId=${payload.content['screenId']} '
      'current=$_currentScreen '
      'sdkState=$sdkState '
      'hostMounted=$_hostMounted',
    );
    switch (sdkState) {
      case SDKState.notInitialized:
      case SDKState.failed:
        debugPrint('[Digia] ❌ campaign dropped — SDK not ready: $sdkState');
        return;
      case SDKState.initializing:
        if (_isNudge(payload)) {
          debugPrint(
              '[Digia] 💾 SDK still initializing — queueing nudge payload: ${payload.id}');
          _pendingPayload = payload;
        } else {
          debugPrint(
              '[Digia] ❌ inline payload dropped while sdk initializing.');
        }
        return;
      case SDKState.ready:
        _routeCampaign(payload);
        return;
    }
  }

  @override
  void onCampaignInvalidated(String campaignId) {
    if (_controller.activePayload?.id == campaignId) {
      _controller.dismiss();
    }
    _controller.removeSlotById(campaignId);
  }

  void _routeCampaign(InAppPayload payload) {
    final screenId = (payload.content['screenId'] as String?)?.trim();
    final current = _currentScreen?.trim();
    debugPrint(
      '[Digia] 📍 _routeCampaign id=${payload.id} '
      'command=${payload.content['command']} '
      'screenId="$screenId" currentScreen="$current" '
      'match=${screenId != null && current != null && screenId == current}',
    );
    // NOTE: Native Displays and global in-apps might NOT have a screenId.
    // If screenId is null, we assume it's valid for the current screen.
    if (screenId != null &&
        screenId.isNotEmpty &&
        current != null &&
        screenId != current) {
      debugPrint(
        '[Digia] ❌ campaign DROPPED — screen mismatch: '
        'expected screenId="$screenId" but current="$current" '
        '(payload id=${payload.id})',
      );
      return;
    }

    final command =
        (payload.content['command'] as String?)?.trim().toUpperCase();
    debugPrint(
        '[Digia] ✅ screen match! routing command=$command id=${payload.id}');
    switch (command) {
      case 'SHOW_DIALOG':
      case 'SHOW_BOTTOM_SHEET':
        if (!_hostMounted) {
          debugPrint(
              '[Digia] ⚠️ DigiaHost not mounted yet — nudge will queue.');
        }
        debugPrint(
          '[Digia] 🗨️ routing MODAL campaign id=${payload.id} command=$command',
        );
        _controller.show(payload);
        return;
      case 'SHOW_INLINE':
        final placementKey = payload.content['placementKey'] as String?;
        final componentId = payload.content['componentId'] as String?;
        if (placementKey == null ||
            placementKey.isEmpty ||
            componentId == null ||
            componentId.isEmpty) {
          debugPrint(
              '[Digia] ❌ inline payload DROPPED — missing placementKey or componentId. '
              'placementKey=$placementKey componentId=$componentId');
          return;
        }
        debugPrint(
          '[Digia] 🧱 routing INLINE campaign id=${payload.id} '
          'placement=$placementKey component=$componentId',
        );
        _controller.addSlot(placementKey, payload);
        return;
      default:
        debugPrint('[Digia] ❌ unsupported command DROPPED: $command');
        return;
    }
  }

  bool _isNudge(InAppPayload payload) {
    final command =
        (payload.content['command'] as String?)?.trim().toUpperCase();
    return command == 'SHOW_DIALOG' || command == 'SHOW_BOTTOM_SHEET';
  }

  void _flushPendingPayloadIfAny() {
    final payload = _pendingPayload;
    if (payload == null) return;
    _pendingPayload = null;
    _routeCampaign(payload);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _activePlugin?.healthCheck();
    } else if (state == AppLifecycleState.detached) {
      _activePlugin?.teardown();
      _activePlugin = null;
    }
  }

  void _setState(SDKState state) {
    _sdkState.value = state;
  }

  void _logIfVerbose(String message) {
    if (_config?.logLevel == DigiaLogLevel.verbose) {
      debugPrint('[Digia] $message');
    }
  }
}
