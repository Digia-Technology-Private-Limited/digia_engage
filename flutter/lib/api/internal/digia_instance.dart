import 'package:flutter/widgets.dart';

import '../../digia_engage.dart';
import '../../src/analytics/analytics_service.dart';
import '../../src/engage_settings.dart';
import '../../src/preferences_store.dart';
import 'action/engage_action_handler.dart';
import 'campaign/campaign_fetcher.dart';
import 'campaign/campaign_model.dart';
import 'campaign/campaign_store.dart';
import 'digia_overlay_controller.dart';
import 'engage_fonts.dart';
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
  CEPTriggerPayload? _pendingPayload;

  /// Shared with [DigiaHost]. Created once, lives for the app lifetime.
  final DigiaOverlayController _controller = DigiaOverlayController();

  /// Controller for inline campaigns, notifies when they change.
  // final InlineCampaignController inlineController = InlineCampaignController();

  /// Exposed so [DigiaHost] can subscribe at mount time.
  DigiaOverlayController get controller => _controller;

  /// Gets the active inline campaign for a given placement key, if any.
  CEPTriggerPayload? getInlineCampaign(String placementKey) =>
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
      // Wire the event callback — when DigiaHost reports a user interaction,
      // route it to the active plugin.
      _controller.onEvent = (event, payload) async {
        final campaign = _campaignStore.find(payload.campaignKey);
        await DigiaAnalyticsService.instance.captureExperienceEvent(
          event,
          payload,
          campaignType: campaign?.campaignType,
          campaignId: campaign?.id,
        );

        if (event.flushOnCapture) {
          await DigiaAnalyticsService.instance.flush();
        }

        _activePlugin?.notifyEvent(event, payload);
      };

      // Register the host's action override (if any) on the shared runner.
      EngageActionRunner.shared.interceptor = config.onAction;

      // ── Fetch + cache engage campaigns ──────────────────────────────────
      await PreferencesStore.instance.initialize();
      await DigiaAnalyticsService.instance
          .initialize(config.analyticsConfig, config.apiKey);
      final deviceId = EngageSettings.instance.getUuid();
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

  Future<void> setUserId(String userId) async {
    if (!_initialized) {
      debugPrint('[Digia] setUserId() called before initialize().');
      return;
    }
    await DigiaAnalyticsService.instance.setUserId(userId);
  }

  Future<void> clearUserId() async {
    if (!_initialized) {
      debugPrint('[Digia] clearUserId() called before initialize().');
      return;
    }
    await DigiaAnalyticsService.instance.clearUserId();
  }

  String getAnonymousId() {
    if (!_initialized) {
      debugPrint('[Digia] getAnonymousId() called before initialize().');
      return '';
    }
    return DigiaAnalyticsService.instance.getAnonymousId();
  }

  Future<void> flushAnalytics() async {
    if (!_initialized) {
      debugPrint('[Digia] flushAnalytics() called before initialize().');
      return;
    }
    await DigiaAnalyticsService.instance.flush();
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
  void onCampaignTriggered(CEPTriggerPayload payload) {
    // State gating mirrors Android's DigiaInstance.onCampaignTriggered.
    switch (_sdkState) {
      case SDKState.notInitialized:
        debugPrint(
          '[Digia] campaign dropped — SDK not initialized: ${payload.campaignKey}',
        );
        return;
      case SDKState.initializing:
        // Buffer campaigns that arrive while the engage fetch is in flight.
        if (_pendingPayload != null) {
          debugPrint(
            '[Digia] WARNING: pending payload replaced by newer payload: ${payload.campaignKey}',
          );
        }
        _pendingPayload = payload;
        return;
      case SDKState.failed:
        debugPrint(
          '[Digia] campaign dropped — SDK initialization failed: ${payload.campaignKey}',
        );
        return;
      case SDKState.ready:
        _routeCampaign(payload);
    }
  }

  // ─── Routing ───────────────────────────────────────────────────────────────

  /// Routes a triggered campaign by looking up the campaign key in the store.
  void _routeCampaign(CEPTriggerPayload payload) {
    final campaign = _campaignStore.find(payload.campaignKey);
    if (campaign != null) {
      _routeStoredCampaign(campaign, payload);
      return;
    }
    debugPrint('[Digia] campaign not found in store: ${payload.campaignKey}');
  }

  /// Routes a campaign resolved from the store. Only inline carousels are
  /// rendered; every other (recognised) type is dropped with a log.
  void _routeStoredCampaign(CampaignModel campaign, CEPTriggerPayload payload) {
    final config = campaign.config;
    switch (config) {
      case InlineCarouselCampaignConfig(:final inlineConfig):
        _controller.addInlineSlot(inlineConfig.slotKey, config, payload);
        _controller.onEvent?.call(const ExperienceImpressed(), payload);
        _controller.onEvent?.call(const ExperienceDismissed(), payload);

        _logIfVerbose(
          "inline carousel routed to slot '${inlineConfig.slotKey}' "
          '(campaignKey=${campaign.campaignKey}).',
        );
      case InlineStoryCampaignConfig(:final storyConfig):
        _controller.addInlineSlot(storyConfig.slotKey, config, payload);
        _controller.onEvent?.call(const ExperienceImpressed(), payload);
        _controller.onEvent?.call(const ExperienceDismissed(), payload);

        _logIfVerbose(
          "inline story routed to slot '${storyConfig.slotKey}' "
          '(campaignKey=${campaign.campaignKey}).',
        );
      case NudgeCampaignConfig():
        _controller.show(payload);
        _logIfVerbose('nudge scheduled (campaignKey=${campaign.campaignKey}).');
      case UnsupportedCampaignConfig(:final reason):
        debugPrint(
          "[Digia] campaign '${campaign.campaignKey}' dropped: $reason",
        );
    }
  }

  /// Resolves the [CampaignModel] for the active payload so [DigiaHost] can
  /// switch on its config type and present the correct experience.
  /// Returns null (and auto-dismisses) when no matching campaign is found.
  CampaignModel? resolveActiveCampaign() {
    final payload = _controller.activePayload;
    if (payload == null) return null;
    final campaign = _campaignStore.find(payload.campaignKey);
    if (campaign == null) {
      _controller.dismiss();
      return null;
    }
    return campaign;
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
    if (_controller.activePayload?.cepCampaignId == campaignId) {
      _controller.dismiss();
    }
    // Inline slot (carousel or story), if this campaign populated one.
    _controller.removeInlineSlotByCampaignId(campaignId);
  }

  // ─── WidgetsBindingObserver ────────────────────────────────────────────────

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // detached = permanent process destruction. Not paused (every background).
    DigiaAnalyticsService.instance.appLifecycleChanged(state);
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
