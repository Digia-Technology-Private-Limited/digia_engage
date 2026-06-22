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
import 'event/cep_plugin_sink.dart';
import 'event/digia_analytics_sink.dart';
import 'event/dwell_tracker.dart';
import 'event/engage_analytics_event.dart';
import 'event/engage_event_emitter.dart';
import 'nudge/nudge_config.dart';
import 'sdk_state.dart';
import 'survey/submission_reporter.dart';
import 'survey/survey_config.dart';
import 'survey/survey_logic_handler.dart';
import 'survey/survey_orchestrator.dart';

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

  /// Holds the single active survey. Mirrors Android's `SurveyOrchestrator`;
  /// [DigiaHost] mounts the survey renderer against it.
  final SurveyOrchestrator _surveyOrchestrator = SurveyOrchestrator();
  SurveyOrchestrator get surveyOrchestrator => _surveyOrchestrator;

  /// Posts completed-survey answers to the backend. Created during [initialize]
  /// once the device id is known. Mirrors Android's `submissionReporter`.
  SubmissionReporter? _submissionReporter;

  /// Guards double-firing the survey `Completed` event for one showing.
  int? _completedSurveyToken;

  /// Guards double-firing the survey `Clicked` engagement event for one
  /// showing. Fired on the welcome CTA, or on the first question's CTA when
  /// the welcome screen is hidden.
  int? _clickedSurveyToken;

  /// `"<surveyToken>:<nodeId>"` → wall-clock ms when that question was viewed,
  /// used to compute `time_to_answer_ms`. Mirrors Android's `questionViewedAt`.
  final Map<String, int> _questionViewedAt = {};

  /// Controller for inline campaigns, notifies when they change.
  // final InlineCampaignController inlineController = InlineCampaignController();

  /// Exposed so [DigiaHost] can subscribe at mount time.
  DigiaOverlayController get controller => _controller;

  /// The SDK's experience-event emitter. Composition root: assembles the two
  /// concrete delivery sinks — the CEP sink (coarse [DigiaExperienceEvent]s)
  /// and the Digia analytics sink (rich [EngageAnalyticsEvent]s). Built lazily
  /// on first use — it only reads [_campaignStore] (ready) and [_activePlugin]
  /// (resolved at delivery time), so no init ordering is required.
  late final EngageEventEmitter _events = EngageEventEmitter(
    CepPluginSink(() => _activePlugin),
    DigiaAnalyticsSink(DigiaAnalyticsService.instance, _campaignStore),
  );

  /// Experience-event emitter used by render surfaces ([DigiaSlot],
  /// [DigiaHost], nudge widgets) to route lifecycle events to CEP and/or Digia.
  EngageEventEmitter get events => _events;

  /// Measures how long each campaign instance was on screen (`dwell_ms`).
  /// Mirrors Android's `DigiaInstance.dwellTracker`.
  final DwellTracker _dwellTracker = DwellTracker();

  /// The most recent screen name reported via [setCurrentScreen], attached to
  /// every "Viewed" analytics event. Mirrors Android's `screenTracker`.
  String? _currentScreen;

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
      // Register the host's action override (if any) on the shared runner.
      EngageActionRunner.shared.interceptor = config.onAction;

      // ── Fetch + cache engage campaigns ──────────────────────────────────
      await PreferencesStore.instance.initialize();
      await DigiaAnalyticsService.instance
          .initialize(config.analyticsConfig, config.apiKey);
      final deviceId = EngageSettings.instance.getUuid();
      _submissionReporter = SubmissionReporter(config, deviceId);
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
      // A template presented during init left a CEP slot held (we returned
      // `true` from onCampaignTriggered). Init failed so it will never route —
      // release the slot, else later in-apps queue forever.
      final pending = _pendingPayload;
      _pendingPayload = null;
      if (pending != null) {
        _events.toCep(const ExperienceDismissed(), pending);
      }
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
    _currentScreen = name;
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
  bool onCampaignTriggered(CEPTriggerPayload payload) {
    // State gating mirrors Android's DigiaInstance.onCampaignTriggered.
    switch (_sdkState) {
      case SDKState.notInitialized:
        debugPrint(
          '[Digia] campaign dropped — SDK not initialized: ${payload.campaignKey}',
        );
        return false;
      case SDKState.initializing:
        // Buffer campaigns that arrive while the engage fetch is in flight.
        if (_pendingPayload != null) {
          debugPrint(
            '[Digia] WARNING: pending payload replaced by newer payload: ${payload.campaignKey}',
          );
        }
        _pendingPayload = payload;
        // Accepted: it will route once init resolves. The CEP keeps its in-app
        // slot held until then; [_flushPendingPayloadIfAny] (or the init-failure
        // path) releases it if routing ultimately drops the campaign.
        return true;
      case SDKState.failed:
        debugPrint(
          '[Digia] campaign dropped — SDK initialization failed: ${payload.campaignKey}',
        );
        return false;
      case SDKState.ready:
        return _routeCampaign(payload);
    }
  }

  // ─── Routing ───────────────────────────────────────────────────────────────

  /// Routes a triggered campaign by looking up the campaign key in the store.
  ///
  /// Returns whether the campaign was accepted for rendering (see
  /// [onCampaignTriggered]).
  bool _routeCampaign(CEPTriggerPayload payload) {
    final campaign = _campaignStore.find(payload.campaignKey);
    if (campaign != null) {
      return _routeStoredCampaign(campaign, payload);
    }
    debugPrint('[Digia] campaign not found in store: ${payload.campaignKey}');
    return false;
  }

  /// Routes a campaign resolved from the store. Returns whether the campaign was
  /// accepted for rendering — `false` for dropped types (unsupported, or an
  /// orchestrator that refused because another experience is on screen).
  bool _routeStoredCampaign(CampaignModel campaign, CEPTriggerPayload payload) {
    final config = campaign.config;
    switch (config) {
      case InlineCarouselCampaignConfig(:final inlineConfig):
        _controller.addInlineSlot(inlineConfig.slotKey, config, payload);
        // CEP is notified instantly at route time (syncTemplate semantics).
        // Digia's impression fires only when the slot first renders — see
        // DigiaSlot._scheduleDigiaImpressionIfNeeded.
        _events.toCep(const ExperienceImpressed(), payload);
        _events.toCep(const ExperienceDismissed(), payload);

        _logIfVerbose(
          "inline carousel routed to slot '${inlineConfig.slotKey}' "
          '(campaignKey=${campaign.campaignKey}).',
        );
        return true;
      case InlineStoryCampaignConfig(:final storyConfig):
        _controller.addInlineSlot(storyConfig.slotKey, config, payload);
        // CEP instantly; Digia impression on first render (see DigiaSlot).
        _events.toCep(const ExperienceImpressed(), payload);
        _events.toCep(const ExperienceDismissed(), payload);

        _logIfVerbose(
          "inline story routed to slot '${storyConfig.slotKey}' "
          '(campaignKey=${campaign.campaignKey}).',
        );
        return true;
      case NudgeCampaignConfig():
        _controller.show(payload);
        _logIfVerbose('nudge scheduled (campaignKey=${campaign.campaignKey}).');
        return true;
      case SurveyCampaignConfig():
        final started = _surveyOrchestrator.start(
          campaign,
          payload,
          nowMs: DateTime.now().millisecondsSinceEpoch,
        );
        if (!started) {
          debugPrint(
            "[Digia] survey campaign '${campaign.campaignKey}' dropped: "
            'another survey is on screen.',
          );
          return false;
        }
        _logIfVerbose(
            'survey scheduled (campaignKey=${campaign.campaignKey}).');
        return true;
      case UnsupportedCampaignConfig(:final reason):
        debugPrint(
          "[Digia] campaign '${campaign.campaignKey}' dropped: $reason",
        );
        return false;
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

  /// Looks up a cached campaign by its [campaignKey]. Used by inline surfaces to
  /// attach campaign context to their actions (the same id analytics reports).
  CampaignModel? campaignForKey(String campaignKey) =>
      _campaignStore.find(campaignKey);

  /// Routes a campaign that arrived during initialization, once ready.
  void _flushPendingPayloadIfAny() {
    final payload = _pendingPayload;
    if (payload == null) return;
    _pendingPayload = null;
    if (!_routeCampaign(payload)) {
      // We returned `true` from onCampaignTriggered while initializing, so a CEP
      // plugin may be holding its in-app slot for this campaign. Routing dropped
      // it and no terminal event will fire — release the slot now, else later
      // in-apps queue forever.
      _events.toCep(const ExperienceDismissed(), payload);
    }
  }

  @override
  void onCampaignInvalidated(String campaignId) {
    // Active modal overlay.
    if (_controller.activePayload?.cepCampaignId == campaignId) {
      _controller.dismiss();
    }
    // Active survey, if this campaign triggered it.
    if (_surveyOrchestrator.state?.payload.cepCampaignId == campaignId) {
      _surveyOrchestrator.dismiss();
    }
    // Inline slot (carousel or story), if this campaign populated one.
    _controller.removeInlineSlotByCampaignId(campaignId);
    // Forget the impression mark so a re-trigger impresses to Digia afresh.
    _events.resetImpression(campaignId);
  }

  // ─── Survey lifecycle ──────────────────────────────────────────────────────
  // Called by `SurveyRenderer` as the showing progresses. Each fires the
  // matching experience event to the active CEP plugin + analytics, via the
  // same `_events` emitter nudges use.

  /// Fired once when the survey first becomes visible (its impression).
  void reportSurveyStarted() {
    final state = _surveyOrchestrator.state;
    if (state == null) return;
    _dwellTracker.markViewed(state.payload.cepCampaignId);
    final config = state.config;
    _events.toBoth(
      const ExperienceImpressed(),
      SurveyViewed(
        itemTotal: _surveyItemTotal(config),
        hasWelcome: _surveyHasWelcome(config),
        hasThanks: _surveyHasThanks(config),
        hasBranching: _surveyHasBranching(config),
        screenName: _currentScreen,
      ),
      state.payload,
    );
  }

  /// Fired when a (non-content) question becomes visible. [itemIndex] is the
  /// 1-based respondent traversal depth. Stamps the view time for
  /// `time_to_answer_ms`. First-party Digia analytics only.
  void reportSurveyQuestionViewed(String nodeId, int itemIndex) {
    final state = _surveyOrchestrator.state;
    if (state == null) return;
    final block = _blockForNode(state.config, nodeId);
    if (block == null || block.type.isContent) return;
    _questionViewedAt[_questionKey(state.token, nodeId)] =
        DateTime.now().millisecondsSinceEpoch;
    final typeWire = _blockTypeWire(block.type);
    _events.toDigia(
      SurveyQuestionViewed(
        questionId: nodeId,
        questionType: typeWire,
        itemIndex: itemIndex,
        itemTotal: _surveyItemTotal(state.config),
        blockType: typeWire,
        blockId: block.id,
        isRequired: block.required,
        questionTitle: _questionTitle(block),
      ),
      state.payload,
    );
  }

  /// Fired when the user advances past an unanswered, optional question.
  /// First-party Digia analytics only.
  void reportSurveyQuestionSkipped(String nodeId, int itemIndex) {
    final state = _surveyOrchestrator.state;
    if (state == null) return;
    final block = _blockForNode(state.config, nodeId);
    if (block == null) return;
    _questionViewedAt.remove(_questionKey(state.token, nodeId));
    _events.toDigia(
      SurveyQuestionSkipped(
        questionId: nodeId,
        itemIndex: itemIndex,
        blockType: _blockTypeWire(block.type),
        blockId: block.id,
        questionTitle: _questionTitle(block),
      ),
      state.payload,
    );
  }

  /// Fired after a question (any block other than welcome) is answered.
  /// [stepId] identifies the node. First-party Digia analytics only.
  void reportSurveyAnswered(String stepId, Map<String, dynamic> answer) {
    final state = _surveyOrchestrator.state;
    if (state == null) return;
    final block = _blockForNode(state.config, stepId);
    final values = (answer['values'] as List?)
        ?.map((value) => value.toString())
        .toList(growable: false);
    final hasValues = values != null && values.isNotEmpty;
    final comment = answer['comment'] as String?;
    final viewedKey = _questionKey(state.token, stepId);
    final viewedAt = _questionViewedAt.remove(viewedKey);
    final scale = block == null ? null : _scaleBounds(block);
    final typeWire = block == null ? null : _blockTypeWire(block.type);
    _events.toDigia(
      SurveyQuestionAnswered(
        questionId: stepId,
        questionType: typeWire,
        questionTitle: block == null ? null : _questionTitle(block),
        answerValue: hasValues ? values.first : null,
        answerText: comment ?? (hasValues ? values.join(', ') : null),
        blockType: typeWire,
        blockId: block?.id,
        answerLabel:
            block == null ? null : _answerLabel(block, values ?? const []),
        answerOptions: (values != null && values.length > 1) ? values : null,
        scaleMin: scale?.min,
        scaleMax: scale?.max,
        timeToAnswerMs: viewedAt == null
            ? null
            : DateTime.now().millisecondsSinceEpoch - viewedAt,
        answer: answer,
      ),
      state.payload,
    );
  }

  /// Fired for the survey's single `Clicked` engagement signal — on the welcome
  /// screen's start CTA, or on the first question's CTA when the welcome screen
  /// is hidden. Idempotent per showing. First-party Digia analytics only.
  void reportWelcomeCtaClicked() {
    final state = _surveyOrchestrator.state;
    if (state == null || _clickedSurveyToken == state.token) return;
    _clickedSurveyToken = state.token;
    _events.toDigia(
      const SurveyClicked(elementId: 'welcome_start'),
      state.payload,
    );
  }

  /// Fired once when the survey finishes (idempotent per showing). Beyond the
  /// `Completed` analytics event, [answers] (when supplied) are POSTed to the
  /// backend's `recordSubmission` endpoint via [SubmissionReporter].
  void reportSurveyCompleted(
    Map<String, dynamic> response, [
    Map<String, SurveyAnswer> answers = const {},
  ]) {
    final state = _surveyOrchestrator.state;
    if (state == null || _completedSurveyToken == state.token) return;
    _completedSurveyToken = state.token;
    _events.toDigia(
      SurveyCompleted(
        itemTotal: _surveyItemTotal(state.config),
        answeredCount: answers.isNotEmpty ? answers.length : response.length,
        timeToCompleteMs:
            DateTime.now().millisecondsSinceEpoch - state.startedAtMs,
        response: response,
      ),
      state.payload,
    );

    if (answers.isNotEmpty) {
      _logIfVerbose(
        'survey submission started: campaignKey=${state.campaign.campaignKey}, '
        'campaignId=${state.campaign.id}, answers=${answers.length}',
      );
      _submissionReporter?.reportSurveyCompleted(
        state.campaign,
        answers,
        state.startedAtMs,
      );
    }
  }

  /// Reports completion and clears the active survey.
  void markSurveyCompleted(
    Map<String, dynamic> response, [
    Map<String, SurveyAnswer> answers = const {},
  ]) {
    reportSurveyCompleted(response, answers);
  }

  /// Clears the active survey after the user closes the result page.

  /// Fired when the user closes the survey without completing it.
  /// [abandonedAtItem] is the 1-based question they were on; [answeredCount]
  /// is how many they had answered.
  void markSurveyDismissed({int? abandonedAtItem, int? answeredCount}) {
    final state = _surveyOrchestrator.state;
    if (state == null) return;
    _events.toBoth(
      const ExperienceDismissed(),
      SurveyDismissed(
        abandonedAtItem: abandonedAtItem,
        itemTotal: _surveyItemTotal(state.config),
        answeredCount: answeredCount,
        dwellMs: _dwellTracker.consumeDwellMs(state.payload.cepCampaignId),
      ),
      state.payload,
    );
    _clearQuestionViewedAt(state.token);
    _surveyOrchestrator.dismiss();
  }

  // ── Survey config derivations (mirror Android's SurveyConfig helpers) ──
  int _surveyItemTotal(SurveyConfigModel config) => config.nodes.length;

  String _questionKey(int token, String nodeId) => '$token:$nodeId';

  void _clearQuestionViewedAt(int token) =>
      _questionViewedAt.removeWhere((key, _) => key.startsWith('$token:'));

  SurveyBlock? _blockForNode(SurveyConfigModel config, String nodeId) {
    final node = config.nodeById(nodeId);
    return node == null ? null : config.blockFor(node);
  }

  /// The analytics wire value for a block type, e.g. `single_select`, `nps_emoji`.
  String _blockTypeWire(SurveyBlockType type) => switch (type) {
        SurveyBlockType.singleSelect => 'single_select',
        SurveyBlockType.multiSelect => 'multi_select',
        SurveyBlockType.rating => 'rating',
        SurveyBlockType.nps => 'nps',
        SurveyBlockType.npsEmoji => 'nps_emoji',
        SurveyBlockType.npsSmiley => 'nps_smiley',
        SurveyBlockType.reaction => 'reaction',
        SurveyBlockType.thisOrThat => 'this_or_that',
        SurveyBlockType.tierList => 'tier_list',
        SurveyBlockType.upvote => 'upvote',
        SurveyBlockType.shortText => 'short_text',
        SurveyBlockType.longText => 'long_text',
        SurveyBlockType.number => 'number',
        SurveyBlockType.email => 'email',
        SurveyBlockType.date => 'date',
        SurveyBlockType.welcome => 'welcome',
        SurveyBlockType.textMedia => 'text_media',
        SurveyBlockType.resultPage => 'result_page',
      };

  String? _questionTitle(SurveyBlock block) {
    final text = block.title.text.trim();
    return text.isEmpty ? null : text;
  }

  /// Maps answer value ids to their option labels (comma-joined), or null.
  String? _answerLabel(SurveyBlock block, List<String> values) {
    if (values.isEmpty || block.options.isEmpty) return null;
    final labels = <String>[];
    for (final id in values) {
      for (final option in block.options) {
        if (option.id == id) {
          labels.add(option.label);
          break;
        }
      }
    }
    return labels.isEmpty ? null : labels.join(', ');
  }

  /// Numeric scale bounds for rating (1–5) and NPS variants (0–10), else null.
  ({int min, int max})? _scaleBounds(SurveyBlock block) => switch (block.type) {
        SurveyBlockType.rating => (min: 1, max: 5),
        SurveyBlockType.nps ||
        SurveyBlockType.npsEmoji ||
        SurveyBlockType.npsSmiley =>
          (min: 0, max: 10),
        _ => null,
      };

  bool _surveyHasWelcome(SurveyConfigModel config) =>
      config.welcomeBlock() != null;

  bool _surveyHasThanks(SurveyConfigModel config) =>
      config.blocks.any((block) => block.type == SurveyBlockType.resultPage);

  bool _surveyHasBranching(SurveyConfigModel config) => config.nodes.any(
        (node) =>
            node.branching.type != BranchingType.linear ||
            node.branching.rules.isNotEmpty,
      );

  // ─── Nudge lifecycle ───────────────────────────────────────────────────────
  // Called by `DigiaHost`/nudge widgets. Mirrors Android's reportNudgeImpression
  // / emitNudgeClick / markNudgeDismissed.

  /// Fired once when the nudge first becomes visible (its impression).
  void reportNudgeImpression(CEPTriggerPayload payload, NudgeConfig config) {
    _dwellTracker.markViewed(payload.cepCampaignId);
    _events.toBoth(
      const ExperienceImpressed(),
      NudgeViewed(
        displayStyle: _nudgeDisplayStyle(config),
        screenName: _currentScreen,
      ),
      payload,
    );
  }

  /// Fired when the user taps the nudge's primary CTA. First-party Digia
  /// analytics only; `time_to_action_ms` is the dwell so far (nudge still open).
  void reportNudgeClicked(
    CEPTriggerPayload payload, {
    String? elementId,
    String? ctaLabel,
    String? actionType,
    String? actionUrl,
    String? ctaRole,
  }) {
    _events.toDigia(
      NudgeClicked(
        elementId: elementId,
        ctaLabel: ctaLabel,
        actionType: actionType,
        actionUrl: actionUrl,
        ctaRole: ctaRole,
        timeToActionMs: _dwellTracker.elapsedMs(payload.cepCampaignId),
      ),
      payload,
    );
  }

  /// Fired when the nudge is dismissed — by the user or programmatically.
  void markNudgeDismissed(CEPTriggerPayload payload) {
    _events.toBoth(
      const ExperienceDismissed(),
      NudgeDismissed(
          dwellMs: _dwellTracker.consumeDwellMs(payload.cepCampaignId)),
      payload,
    );
  }

  String _nudgeDisplayStyle(NudgeConfig config) =>
      config.surface.displayType == NudgeDisplayType.bottomSheet
          ? 'bottom_sheet'
          : 'dialog';

  // ─── Inline lifecycle ──────────────────────────────────────────────────────

  /// Fired when an inline slot first paints. Records the deduped Digia
  /// impression as a [CarouselViewed] or [StoriesViewed] depending on the
  /// campaign type. Mirrors Android's reportSlotFirstRender.
  void reportSlotFirstRender(CEPTriggerPayload payload) {
    final campaign = _campaignStore.find(payload.campaignKey);
    if (campaign == null) return;
    final config = campaign.config;
    switch (config) {
      case InlineCarouselCampaignConfig(:final inlineConfig):
        _events.digiaImpressionOnce(
          payload,
          CarouselViewed(
            itemTotal: inlineConfig.items.length,
            slotKey: inlineConfig.slotKey,
            screenName: _currentScreen,
          ),
        );
      case InlineStoryCampaignConfig(:final storyConfig):
        _events.digiaImpressionOnce(
          payload,
          StoriesViewed(
            itemTotal: storyConfig.items.length,
            slotKey: storyConfig.slotKey,
            screenName: _currentScreen,
          ),
        );
      case _:
        break;
    }
  }

  /// A carousel item scrolled into view. [auto] = autoplay advance vs manual
  /// swipe. [itemIndex] is 1-based.
  void reportCarouselStepViewed(
    CEPTriggerPayload payload, {
    required int itemIndex,
    required int itemTotal,
    required bool auto,
  }) {
    _events.toDigia(
      CarouselStepViewed(
          itemIndex: itemIndex, itemTotal: itemTotal, auto: auto),
      payload,
    );
  }

  /// A carousel item (or its CTA) was tapped. The first item tap also counts as
  /// an experience-level engagement click (once, deduped).
  void reportCarouselStepClicked(
    CEPTriggerPayload payload, {
    required int itemIndex,
    String? actionUrl,
  }) {
    final actionType = actionUrl != null ? 'deeplink' : null;
    _events.digiaExperienceClickedOnce(
      payload,
      CarouselClicked(actionType: actionType, actionUrl: actionUrl),
    );
    _events.toDigia(
      CarouselStepClicked(
        itemIndex: itemIndex,
        actionType: actionType,
        actionUrl: actionUrl,
      ),
      payload,
    );
  }

  /// A story was opened (ring/thumbnail tapped) — drives open rate.
  void reportStoryOpened(CEPTriggerPayload payload) {
    _events.toDigia(const StoriesOpened(), payload);
  }

  /// A story frame became visible. [itemIndex] is 1-based; [itemTotal] = frames
  /// in this story.
  void reportStoryStepViewed(
    CEPTriggerPayload payload, {
    required int itemIndex,
    required int itemTotal,
  }) {
    _events.toDigia(
      StoriesStepViewed(itemIndex: itemIndex, itemTotal: itemTotal),
      payload,
    );
  }

  /// A CTA inside a story frame was tapped.
  void reportStoryStepClicked(
    CEPTriggerPayload payload, {
    required int itemIndex,
    String? ctaLabel,
    String? actionType,
    String? actionUrl,
  }) {
    _events.toDigia(
      StoriesStepClicked(
        itemIndex: itemIndex,
        ctaLabel: ctaLabel,
        actionType: actionType,
        actionUrl: actionUrl,
      ),
      payload,
    );
  }

  /// Story closed before the last frame. [itemIndex] is the 1-based frame on
  /// close.
  void reportStoryStepDismissed(
    CEPTriggerPayload payload, {
    required int itemIndex,
  }) {
    _events.toDigia(StoriesStepDismissed(itemIndex: itemIndex), payload);
  }

  /// Last story frame viewed. [itemTotal] = frames viewed; [timeToCompleteMs]
  /// from open.
  void reportStoryCompleted(
    CEPTriggerPayload payload, {
    required int itemTotal,
    int? timeToCompleteMs,
  }) {
    _events.toDigia(
      StoriesCompleted(
          itemTotal: itemTotal, timeToCompleteMs: timeToCompleteMs),
      payload,
    );
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
