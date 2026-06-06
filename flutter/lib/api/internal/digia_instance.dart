import 'package:flutter/widgets.dart';

import '../../digia_engage.dart';
import '../../digia_ui.dart';
import '../../src/preferences_store.dart';
import 'action/engage_action_handler.dart';
import 'campaign/campaign_fetcher.dart';
import 'campaign/campaign_model.dart';
import 'campaign/campaign_store.dart';
import 'digia_overlay_controller.dart';
import 'engage_fonts.dart';
import 'nudge/nudge_config.dart';
import 'nudge/nudge_presenter.dart';
import 'sdk_state.dart';

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

  /// Tracks engage campaign-fetch readiness. Used to buffer a campaign that
  /// arrives while the SDK is still fetching (see [_pendingPayload]).
  SDKState _sdkState = SDKState.notInitialized;
  SDKState get sdkState => _sdkState;

  /// In-memory cache of campaigns fetched from the Digia backend, keyed by
  /// `campaignKey`. Consulted on every CEP-triggered campaign.
  final CampaignStore _campaignStore = CampaignStore();

  /// A campaign that arrived before the fetch completed; routed once ready.
  InAppPayload? _pendingPayload;

  /// Shared with [DigiaHost]. Created once, lives for the app lifetime.
  final DigiaOverlayController _controller = DigiaOverlayController();

  /// Controller for inline campaigns, notifies when they change.
  // final InlineCampaignController inlineController = InlineCampaignController();

  /// Exposed so [DigiaHost] can subscribe at mount time.
  DigiaOverlayController get controller => _controller;

  /// Gets the active inline campaign for a given placement key, if any.
  InAppPayload? getInlineCampaign(String placementKey) =>
      _controller.getSlot(placementKey);

  // ─── Lifecycle ─────────────────────────────────────────────────────────────

  Future<void> initialize(DigiaConfig config) async {
    if (_initialized) {
      debugPrint('[Digia] initialize() called more than once — ignored.');
      return;
    }
    _config = config;
    _initialized = true;
    _sdkState = SDKState.initializing;

    // Apply the global font family to all Digia-rendered text (mirrors
    // Android setting DigiaFontConfig.fontFamily at the top of init).
    EngageFonts.fontFamily = config.fontFamily;

    WidgetsBinding.instance.addObserver(this);

    // The whole init runs under one guard (mirrors Android's DigiaInstance):
    // any failure leaves the SDK in [SDKState.failed] and resets the
    // started flag so a later call can retry.
    try {
      // ── Wire the real src services ──────────────────────────────────────
      // DigiaUI.initialize() sets up PreferencesStore, NetworkClient, and
      // loads the DSL config (app_config / functions) from the server or
      // local assets depending on the chosen Flavor.
      // final digiaUI = await DigiaUI.initialize(config.toOptions());
      // Initialize the Digia UI manager with the provided configuration
      // DigiaUIManager().initialize(digiaUI);

      // Initialize global app state with configuration from DSL
      // DUIAppState().init(digiaUI.dslConfig.appState ?? []);

      // Set up the UI factory with custom resources and providers
      // DUIFactory().initialize(
      //     // pageConfigProvider: pageConfigProvider,
      //     // icons: icons,
      //     // images: {
      //     //   ...(DigiaUIManager().assetImages.asMap().map((k, v) => MapEntry(
      //     //       v.assetData.localPath,
      //     //       NetworkImage(
      //     //           '${v.assetData.image?.baseUrl}${v.assetData.image?.path}')))),
      //     //   ...?images
      //     // },
      //     // fontFactory: fontFactory,
      //     );

      // Wire the event callback — when DigiaHost reports a user interaction,
      // route it to the active plugin.
      _controller.onEvent = (event, payload) {
        _activePlugin?.notifyEvent(event, payload);
      };

      // Register the host's action override (if any) on the shared runner.
      EngageActionRunner.shared.interceptor = config.onAction;

      // ── Fetch + cache engage campaigns ──────────────────────────────────
      await PreferencesStore.instance.initialize();
      final deviceId = DUISettings.instance.getUuid();
      final campaigns = await CampaignFetcher(config, deviceId).fetch();
      _campaignStore.populate(campaigns);

      // ── Ready ───────────────────────────────────────────────────────────
      _sdkState = SDKState.ready;
      if (_campaignStore.isEmpty) {
        debugPrint('[Digia] No campaigns fetched — CampaignStore is empty.');
      } else {
        _logIfVerbose('Fetched ${campaigns.length} campaign(s).');
      }
      _flushPendingPayloadIfAny();

      _logIfVerbose('Digia initialized with apiKey=${config.apiKey}, '
          'environment=${config.environment.name}.');
    } catch (e) {
      _initialized = false;
      _sdkState = SDKState.failed;
      debugPrint('[Digia] initialize failed: $e');
    }
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
  void onCampaignTriggered(InAppPayload payload) {
    // State gating mirrors Android's DigiaInstance.onCampaignTriggered.
    switch (_sdkState) {
      case SDKState.notInitialized:
        debugPrint(
          '[Digia] campaign dropped — SDK not initialized: ${payload.id}',
        );
        return;
      case SDKState.initializing:
        // Buffer campaigns that arrive while the engage fetch is in flight.
        if (_pendingPayload != null) {
          debugPrint(
            '[Digia] WARNING: pending payload replaced by newer payload: ${payload.id}',
          );
        }
        _pendingPayload = payload;
        return;
      case SDKState.failed:
        debugPrint(
          '[Digia] campaign dropped — SDK initialization failed: ${payload.id}',
        );
        return;
      case SDKState.ready:
        _routeCampaign(payload);
    }
  }

  // ─── Routing ───────────────────────────────────────────────────────────────

  /// Routes a triggered campaign. First consults the campaign store (the
  /// backend-fetched, key-based path); falls back to the legacy CEP-driven
  /// `type`/`command` routing when no stored campaign matches.
  void _routeCampaign(InAppPayload payload) {
    final campaignKey = _resolveCampaignKey(payload);
    if (campaignKey != null) {
      final campaign = _campaignStore.find(campaignKey);
      if (campaign != null) {
        _routeStoredCampaign(campaign, payload);
        return;
      }
    }
    _routeLegacy(payload);
  }

  /// Extracts the campaign key from the payload, mirroring the Android lookup
  /// order: CEP-set keys first, then the React Native bridge key, then the id.
  String? _resolveCampaignKey(InAppPayload payload) {
    final content = payload.content;
    final candidates = <Object?>[
      content['digia_campaign_key'],
      content['campaign_key'],
      content['digiaKey'],
      payload.id,
    ];
    for (final candidate in candidates) {
      if (candidate is String && candidate.trim().isNotEmpty) {
        return candidate.trim();
      }
    }
    return null;
  }

  /// Routes a campaign resolved from the store. Only inline carousels are
  /// rendered; every other (recognised) type is dropped with a log.
  void _routeStoredCampaign(CampaignModel campaign, InAppPayload payload) {
    final config = campaign.config;
    switch (config) {
      case InlineCarouselCampaignConfig(:final inlineConfig):
        _controller.addSlotConfig(inlineConfig, campaignId: campaign.id);
        _controller.onEvent?.call(const ExperienceImpressed(), payload);
        _logIfVerbose(
          "inline carousel routed to slot '${inlineConfig.slotKey}' "
          '(campaignKey=${campaign.campaignKey}).',
        );
      case NudgeCampaignConfig(:final nudgeConfig):
        _presentNudge(nudgeConfig, payload, campaign.id, campaign.campaignKey);
      case UnsupportedCampaignConfig(:final reason):
        debugPrint(
          "[Digia] campaign '${campaign.campaignKey}' dropped: $reason",
        );
    }
  }

  /// Presents a nudge (bottom sheet / dialog) over the app via the root
  /// navigator. Fires an impression on present and a dismissal when it closes.
  void _presentNudge(
    NudgeConfig nudgeConfig,
    InAppPayload payload,
    String campaignId,
    String campaignKey,
  ) {
    final context = _navigator?.context;
    if (context == null) {
      debugPrint(
        "[Digia] nudge '$campaignKey' dropped: no navigator. Add "
        'DigiaNavigatorObserver to MaterialApp.navigatorObservers.',
      );
      return;
    }

    _controller.onEvent?.call(const ExperienceImpressed(), payload);
    _logIfVerbose('nudge presented (campaignKey=$campaignKey).');

    presentNudge(
      context: context,
      config: nudgeConfig,
      actionContext: EngageActionContext(
        campaignId: campaignId,
        campaignKey: campaignKey,
        surface: EngageSurface.nudge,
      ),
    ).whenComplete(() {
      _controller.onEvent?.call(const ExperienceDismissed(), payload);
    });
  }

  /// Legacy CEP-driven routing, used when the campaign is not in the store.
  /// Renders inline content via [DUIFactory] (viewId) and modal campaigns via
  /// [DigiaHost].
  void _routeLegacy(InAppPayload payload) {
    final type = (payload.content['type'] as String?)?.trim().toLowerCase();
    final command =
        (payload.content['command'] as String?)?.trim().toUpperCase();

    if ((type == null || type.isEmpty) &&
        (command == null || command.isEmpty)) {
      debugPrint(
        '[Digia] WARNING: campaign dropped: neither type nor command is set: ${payload.id}',
      );
      return;
    }

    if (type == 'inline') {
      final placementKey = payload.content['placementKey'] as String?;
      if (placementKey == null || placementKey.isEmpty) {
        debugPrint(
          '[Digia] WARNING: inline payload dropped: placementKey is required when type is set: ${payload.id}',
        );
        return;
      }
      _controller.addSlot(placementKey, payload);
      _controller.onEvent?.call(
          const ExperienceImpressed(), payload); // Fire impression immediately
    } else {
      if (!_hostMounted) {
        debugPrint(
          '[Digia] WARNING: A campaign payload arrived but DigiaHost is not '
          'mounted. Wrap your app root with DigiaHost to display experiences.',
        );
      }
      // Modal campaigns (SHOW_BOTTOM_SHEET / SHOW_DIALOG): route to DigiaHost.
      _controller.show(payload);
    }
  }

  /// Routes a campaign that arrived during initialization, once ready.
  void _flushPendingPayloadIfAny() {
    final payload = _pendingPayload;
    if (payload == null) return;
    _pendingPayload = null;
    _routeCampaign(payload);
  }

  @override
  void onCampaignInvalidated(String campaignId) {
    // Active modal overlay.
    if (_controller.activePayload?.id == campaignId) {
      _controller.dismiss();
    }
    // Inline carousel slot config, if this campaign populated one.
    _controller.removeSlotConfigByCampaignId(campaignId);
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
