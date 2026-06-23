import 'dart:async';

import 'package:flutter/material.dart';

import '../../action/engage_action.dart';
import '../../action/engage_action_context.dart';
import '../../action/engage_action_handler.dart';
import '../../digia_instance.dart';
import '../survey_config.dart';
import '../survey_controller.dart';
import '../survey_logic_handler.dart';
import '../survey_orchestrator.dart';
import 'survey_question_widgets.dart';
import 'survey_tokens.dart';

/// Frame-settling buffer added before the survey is shown.
const _renderDelay = Duration(milliseconds: 150);

// ─────────────────────────────────────────────────────────────────────────────
// OLD renderer: a hand-rolled inline scrim + panel drawn above the app (no
// Navigator route). Replaced by the Flutter-modal renderer further below, which
// presents via showModalBottomSheet / showDialog. Kept here, commented out, for
// reference / rollback.
// ─────────────────────────────────────────────────────────────────────────────
/*
/// Top-level survey overlay — mounted once inside [DigiaHost]. Observes the
/// [SurveyOrchestrator] and presents the active survey as a bottom sheet or
/// dialog drawn inline above the app (its own scrim + panel), mirroring the
/// Android `SurveyRenderer`.
class SurveyRenderer extends StatelessWidget {
  const SurveyRenderer({super.key});

  @override
  Widget build(BuildContext context) {
    final orchestrator = DigiaInstance.instance.surveyOrchestrator;
    return ListenableBuilder(
      listenable: orchestrator,
      builder: (context, _) {
        final state = orchestrator.state;
        if (state == null) return const SizedBox.shrink();
        return _SurveySession(key: ValueKey(state.token), state: state);
      },
    );
  }
}

class _SurveySession extends StatefulWidget {
  final ActiveSurveyState state;
  const _SurveySession({required this.state, super.key});

  @override
  State<_SurveySession> createState() => _SurveySessionState();
}

class _SurveySessionState extends State<_SurveySession> {
  late final SurveyController _controller;
  bool _visible = false;
  String? _lastRedirect;
  bool _finishing = false;

  SurveyConfigModel get _survey => widget.state.config;

  @override
  void initState() {
    super.initState();
    _controller = SurveyController(_survey)..addListener(_onControllerChanged);
    final instance = DigiaInstance.instance;
    Future.delayed(
      Duration(milliseconds: _survey.timeDelayMs) + _renderDelay,
      () {
        if (!mounted) return;
        instance.reportSurveyStarted();
        setState(() => _visible = true);
      },
    );
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    _controller.dispose();
    super.dispose();
  }

  void _onControllerChanged() {
    final redirect = _controller.redirectUrl;
    if (redirect != null && redirect != _lastRedirect) {
      _lastRedirect = redirect;
      unawaited(_openRedirect(redirect));
    }
    if (_controller.isComplete && !_finishing) {
      _finishing = true;
      WidgetsBinding.instance.addPostFrameCallback((_) => _finish(completed: true));
    }
    if (mounted) setState(() {});
  }

  Future<void> _openRedirect(String url) async {
    final uri = Uri.tryParse(url);
    if (uri == null) return;
    try {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {/* best effort */}
  }

  void _finish({required bool completed}) {
    final instance = DigiaInstance.instance;
    if (completed) {
      instance.markSurveyCompleted(_controller.responsePayload());
    } else {
      instance.markSurveyDismissed();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_visible || _controller.isComplete) return const SizedBox.shrink();

    final accent = Color(_survey.theme.accentColor);
    final background = Color(_survey.theme.backgroundColor);
    final display = _survey.settings.display;

    final panel = _SurveyPanel(
      controller: _controller,
      survey: _survey,
      accent: accent,
      onClose: () => _finish(completed: false),
      onCompletedClose: () => DigiaInstance.instance.dismissCompletedSurvey(),
      showCloseButton: display.type == SurveyDisplayType.dialog
          ? display.dialog.showCloseButton
          : display.bottomSheet.backdropDismissible,
    );

    final overlay = switch (display.type) {
      SurveyDisplayType.bottomSheet => _BottomSheetPresentation(
          sheet: display.bottomSheet,
          background: background,
          onDismiss: () => _finish(completed: false),
          child: panel,
        ),
      SurveyDisplayType.dialog => _DialogPresentation(
          dialog: display.dialog,
          background: background,
          onDismiss: () => _finish(completed: false),
          child: panel,
        ),
    };

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        if (_controller.canGoBack) {
          _controller.back();
        } else if (display.dismissible) {
          _finish(completed: false);
        }
      },
      child: Material(type: MaterialType.transparency, child: overlay),
    );
  }
}

// ── presentations ──────────────────────────────────────────────────────────────

class _BottomSheetPresentation extends StatefulWidget {
  final BottomSheetProps sheet;
  final Color background;
  final VoidCallback onDismiss;
  final Widget child;

  const _BottomSheetPresentation({
    required this.sheet,
    required this.background,
    required this.onDismiss,
    required this.child,
  });

  @override
  State<_BottomSheetPresentation> createState() => _BottomSheetPresentationState();
}

class _BottomSheetPresentationState extends State<_BottomSheetPresentation> {
  double _dragOffset = 0;

  double _maxHeight(double screenHeight) {
    return switch (widget.sheet.heightMode) {
      BottomSheetHeightMode.wrap => double.infinity,
      BottomSheetHeightMode.half => screenHeight * 0.5,
      BottomSheetHeightMode.full => screenHeight,
      BottomSheetHeightMode.custom =>
        screenHeight * (widget.sheet.customHeight.clamp(10, 100) / 100),
    };
  }

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final screenHeight = media.size.height;
    final dismissThreshold = 150 * (screenHeight / 800).clamp(1.0, double.infinity);
    final maxHeight = _maxHeight(screenHeight);

    final panel = Container(
      width: double.infinity,
      constraints: maxHeight.isFinite ? BoxConstraints(maxHeight: maxHeight) : const BoxConstraints(),
      decoration: BoxDecoration(
        color: widget.background,
        borderRadius: BorderRadius.vertical(top: Radius.circular(widget.sheet.cornerRadius.toDouble())),
      ),
      clipBehavior: Clip.antiAlias,
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (widget.sheet.showHandle)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: SurveyTokens.border,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
            Flexible(child: widget.child),
          ],
        ),
      ),
    );

    return Stack(
      children: [
        Positioned.fill(
          child: GestureDetector(
            onTap: widget.sheet.backdropDismissible ? widget.onDismiss : null,
            child: Container(color: Colors.black.withValues(alpha: 0.4)),
          ),
        ),
        Align(
          alignment: Alignment.bottomCenter,
          child: Transform.translate(
            offset: Offset(0, _dragOffset),
            child: GestureDetector(
              onTap: () {},
              onVerticalDragUpdate: widget.sheet.draggable
                  ? (d) => setState(() => _dragOffset = (_dragOffset + d.delta.dy).clamp(0, double.infinity))
                  : null,
              onVerticalDragEnd: widget.sheet.draggable
                  ? (_) {
                      if (_dragOffset > dismissThreshold) {
                        widget.onDismiss();
                      } else {
                        setState(() => _dragOffset = 0);
                      }
                    }
                  : null,
              child: panel,
            ),
          ),
        ),
      ],
    );
  }
}

class _DialogPresentation extends StatelessWidget {
  final DialogProps dialog;
  final Color background;
  final VoidCallback onDismiss;
  final Widget child;

  const _DialogPresentation({
    required this.dialog,
    required this.background,
    required this.onDismiss,
    required this.child,
  });

  double _width(double screenWidth) => switch (dialog.width) {
        DialogWidthPreset.small => screenWidth * 0.6,
        DialogWidthPreset.medium => screenWidth * 0.8,
        DialogWidthPreset.large => screenWidth * 0.95,
        DialogWidthPreset.custom => dialog.customWidth.clamp(200, 1 << 20).toDouble(),
      };

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    return Stack(
      children: [
        Positioned.fill(
          child: GestureDetector(
            onTap: dialog.backdropDismissible ? onDismiss : null,
            child: Container(color: Colors.black.withValues(alpha: dialog.backdropOpacity)),
          ),
        ),
        Center(
          child: Padding(
            padding: EdgeInsets.only(
              left: 16,
              right: 16,
              bottom: media.viewInsets.bottom,
            ),
            child: GestureDetector(
              onTap: () {},
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: _width(media.size.width),
                  maxHeight: media.size.height * 0.85,
                ),
                child: Material(
                  color: background,
                  borderRadius: BorderRadius.circular(dialog.cornerRadius.toDouble()),
                  clipBehavior: Clip.antiAlias,
                  child: SingleChildScrollView(child: child),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
*/

// ── renderer: presents the active survey via Flutter's native modals ──────────

/// Top-level survey host — mounted once inside [DigiaHost]. Observes the
/// [SurveyOrchestrator] and presents the active survey using Flutter's own
/// [showModalBottomSheet] / [showDialog] routes (rather than a hand-rolled
/// inline scrim + panel).
///
/// Because those are Navigator routes, presentation needs a context below the
/// app's [Navigator]; the SDK exposes one via [DigiaInstance.navigator] (the
/// same context nudges use). The widget itself renders nothing.
class SurveyRenderer extends StatefulWidget {
  const SurveyRenderer({super.key});

  @override
  State<SurveyRenderer> createState() => _SurveyRendererState();
}

class _SurveyRendererState extends State<SurveyRenderer> {
  late final SurveyOrchestrator _orchestrator;

  /// Token of the survey we've already begun handling, so an orchestrator
  /// rebuild can't present the same showing twice.
  int? _handledToken;

  /// Navigator context of the currently-open modal route, while one is open.
  BuildContext? _routeNavContext;
  bool _routeOpen = false;

  @override
  void initState() {
    super.initState();
    _orchestrator = DigiaInstance.instance.surveyOrchestrator;
    _orchestrator.addListener(_onOrchestratorChanged);
    _maybePresent();
  }

  @override
  void dispose() {
    _orchestrator.removeListener(_onOrchestratorChanged);
    super.dispose();
  }

  void _onOrchestratorChanged() {
    if (_orchestrator.state == null) {
      // The survey was cleared (completed / dismissed / invalidated). The
      // teardown call already fired analytics; we just close any open modal.
      if (_routeOpen) {
        final ctx = _routeNavContext;
        _routeOpen = false;
        _routeNavContext = null;
        if (ctx != null && ctx.mounted) Navigator.of(ctx).pop();
      }
      return;
    }
    _maybePresent();
  }

  void _maybePresent() {
    final state = _orchestrator.state;
    if (state == null || _handledToken == state.token) return;
    _handledToken = state.token;
    final survey = state.config;
    Future.delayed(
      Duration(milliseconds: survey.timeDelayMs) + _renderDelay,
      () {
        if (!mounted) return;
        // The survey may have been cleared while we waited out the delay.
        if (_orchestrator.state?.token != state.token) return;
        _present(state);
      },
    );
  }

  Future<void> _present(ActiveSurveyState state) async {
    final navContext = DigiaInstance.instance.navigator?.context;
    if (navContext == null || !navContext.mounted) {
      // No navigator route available — clear the survey so it doesn't wedge the
      // single-survey-at-a-time orchestrator.
      debugPrint(
        '[Digia] Survey not shown: no navigator context. Wire '
        'DigiaHost.navigatorKey to MaterialApp.navigatorKey.',
      );
      _orchestrator.dismiss();
      return;
    }

    final survey = state.config;
    final accent = Color(survey.theme.accentColor);
    final background = Color(survey.theme.backgroundColor);
    final display = survey.settings.display;
    final controller = SurveyController(survey);

    final showClose = display.type == SurveyDisplayType.dialog
        ? display.dialog.showCloseButton
        : display.bottomSheet.backdropDismissible;

    final content = _SurveyModalContent(
      controller: controller,
      survey: survey,
      accent: accent,
      showCloseButton: showClose,
    );

    DigiaInstance.instance.reportSurveyStarted();
    _routeOpen = true;
    _routeNavContext = navContext;

    switch (display.type) {
      case SurveyDisplayType.bottomSheet:
        await _showSheet(navContext, display.bottomSheet, background, content);
      case SurveyDisplayType.dialog:
        await _showDialog(navContext, display.dialog, background, content);
    }

    _routeOpen = false;
    _routeNavContext = null;

    // The route closed without a teardown call having cleared the survey — i.e.
    // a Flutter swipe / barrier gesture popped it — so report the dismissal.
    if (_orchestrator.state?.token == state.token) {
      DigiaInstance.instance.markSurveyDismissed();
    }
    controller.dispose();
  }

  Future<bool?> _showSheet(
    BuildContext ctx,
    BottomSheetProps sheet,
    Color background,
    Widget content,
  ) {
    final screenHeight = MediaQuery.of(ctx).size.height;
    final maxHeight = switch (sheet.heightMode) {
      BottomSheetHeightMode.wrap => null,
      BottomSheetHeightMode.half => screenHeight * 0.5,
      BottomSheetHeightMode.full => screenHeight,
      BottomSheetHeightMode.custom =>
        screenHeight * (sheet.customHeight.clamp(10, 100) / 100),
    };
    return showModalBottomSheet<bool>(
      context: ctx,
      isScrollControlled: true,
      isDismissible: sheet.backdropDismissible,
      enableDrag: sheet.draggable,
      showDragHandle: sheet.showHandle,
      useSafeArea: true,
      backgroundColor: background,
      barrierColor: Colors.black.withValues(alpha: 0.4),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(
            top: Radius.circular(sheet.cornerRadius.toDouble())),
      ),
      constraints:
          maxHeight == null ? null : BoxConstraints(maxHeight: maxHeight),
      builder: (_) => content,
    );
  }

  Future<bool?> _showDialog(
    BuildContext ctx,
    DialogProps dialog,
    Color background,
    Widget content,
  ) {
    final media = MediaQuery.of(ctx);

    return showDialog<bool>(
      context: ctx,
      barrierDismissible: dialog.backdropDismissible,
      barrierColor: Colors.black.withValues(alpha: dialog.backdropOpacity),
      builder: (_) => Dialog(
        backgroundColor: background,
        insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
        clipBehavior: Clip.antiAlias,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(dialog.cornerRadius.toDouble()),
        ),
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxWidth: double.infinity,
            maxHeight: media.size.height * 0.85,
          ),
          child: content,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}

/// The content rendered inside the modal route: the welcome gate + survey body
/// ([_SurveyPanel]) plus the cross-step concerns the old `_SurveySession`
/// owned — redirect-url launching and completion teardown.
///
/// Teardown calls here only clear the orchestrator; [_SurveyRendererState]
/// reacts to that by popping the route, so this widget never pops itself (which
/// keeps a single owner of the route and avoids double-pop races).
class _SurveyModalContent extends StatefulWidget {
  final SurveyController controller;
  final SurveyConfigModel survey;
  final Color accent;
  final bool showCloseButton;

  const _SurveyModalContent({
    required this.controller,
    required this.survey,
    required this.accent,
    required this.showCloseButton,
  });

  @override
  State<_SurveyModalContent> createState() => _SurveyModalContentState();
}

class _SurveyModalContentState extends State<_SurveyModalContent> {
  String? _lastRedirect;
  bool _finishing = false;

  SurveyController get _c => widget.controller;

  @override
  void initState() {
    super.initState();
    _c.addListener(_onControllerChanged);
  }

  @override
  void dispose() {
    _c.removeListener(_onControllerChanged);
    super.dispose();
  }

  void _onControllerChanged() {
    final redirect = _c.redirectUrl;
    if (redirect != null && redirect != _lastRedirect) {
      _lastRedirect = redirect;
      unawaited(_openRedirect(redirect));
    }
    if (_c.isComplete && !_finishing) {
      _finishing = true;
      final instance = DigiaInstance.instance;
      instance.markSurveyCompleted(_c.responsePayload(), _c.answers);
      // Completing is NOT the same as dismissing: a result page is its own node,
      // so when one is still to be shown we leave the survey up and let the user
      // close it (Done → markSurveyDismissed). Only when no node is left in the
      // path do we dismiss here, so the route isn't left open showing nothing.
      if (_c.currentBlock?.type != SurveyBlockType.resultPage) {
        instance.markSurveyDismissed();
      }
    }
    if (mounted) setState(() {});
  }

  Future<void> _openRedirect(String url) async {
    if (url.isEmpty || !mounted) return;
    // Route through the engage action runner (like inline/nudge CTAs) so the
    // host's `onAction` override is consulted before the default open. Built
    // synchronously here, before the completing survey tears down the route.
    final state = DigiaInstance.instance.surveyOrchestrator.state;
    final scope = EngageActionScope.fromContext(context);
    final actionContext = EngageActionContext(
      campaignId: state?.campaign.id ?? '',
      campaignKey: state?.campaign.campaignKey ?? '',
      surface: EngageSurface.survey,
    );
    await EngageActionRunner.shared.run(
      [OpenDeeplinkAction(url)],
      scope,
      actionContext,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_c.isComplete) return const SizedBox.shrink();
    final display = widget.survey.settings.display;
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        // System-back / barrier tap: step back through the survey first, else
        // dismiss it (when the display allows dismissal).
        if (_c.canGoBack) {
          _c.back();
        } else if (display.dismissible) {
          DigiaInstance.instance.markSurveyDismissed();
        }
      },
      child: _SurveyPanel(
        controller: _c,
        survey: widget.survey,
        accent: widget.accent,
        onClose: () => DigiaInstance.instance.markSurveyDismissed(),
        onCompletedClose: () => DigiaInstance.instance.markSurveyDismissed(),
        showCloseButton: widget.showCloseButton,
      ),
    );
  }
}

// ── panel (welcome gate → body) ────────────────────────────────────────────────

class _SurveyPanel extends StatefulWidget {
  final SurveyController controller;
  final SurveyConfigModel survey;
  final Color accent;
  final VoidCallback onClose;
  final VoidCallback onCompletedClose;
  final bool showCloseButton;

  const _SurveyPanel({
    required this.controller,
    required this.survey,
    required this.accent,
    required this.onClose,
    required this.onCompletedClose,
    required this.showCloseButton,
  });

  @override
  State<_SurveyPanel> createState() => _SurveyPanelState();
}

class _SurveyPanelState extends State<_SurveyPanel> {
  bool _welcomeDone = false;

  @override
  Widget build(BuildContext context) {
    final welcome = widget.survey.welcomeBlock();
    if (welcome != null && !_welcomeDone) {
      return _WelcomeScreen(
        block: welcome,
        cta: widget.survey.settings.cta,
        accent: widget.accent,
        showClose: widget.showCloseButton &&
            widget.survey.settings.display.dismissible,
        onStart: () {
          DigiaInstance.instance.reportWelcomeCtaClicked();
          setState(() => _welcomeDone = true);
        },
        onClose: widget.onClose,
      );
    }
    return _SurveyBody(
      controller: widget.controller,
      survey: widget.survey,
      accent: widget.accent,
      onClose: widget.onClose,
      onCompletedClose: widget.onCompletedClose,
      showCloseButton: widget.showCloseButton,
    );
  }
}

class _WelcomeScreen extends StatelessWidget {
  final SurveyBlock block;
  final CtaSettings cta;
  final Color accent;
  final bool showClose;
  final VoidCallback onStart;
  final VoidCallback onClose;

  const _WelcomeScreen({
    required this.block,
    required this.cta,
    required this.accent,
    required this.showClose,
    required this.onStart,
    required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    final blockBg = surveyColorOrNull(block.backgroundColorHex);
    final stacked = cta.layout == CtaLayout.stacked;
    final startBtn = _CtaButton(
      label: cta.startLabel,
      background: _ctaBg(cta, accent),
      foreground: _ctaText(cta),
      cornerRadius: cta.cornerRadius.toDouble(),
      onTap: onStart,
      fullWidth: stacked,
    );
    return Container(
      width: double.infinity,
      color: blockBg,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (showClose)
            Align(
              alignment: Alignment.centerRight,
              child: _CloseButton(onTap: onClose),
            ),
          if (block.showMedia && block.media.position == MediaPosition.top) ...[
            _BlockMediaImage(block.media),
            const SizedBox(height: 12),
          ],
          _BlockTitle(block: block),
          if (block.showMedia &&
              block.media.position == MediaPosition.inline) ...[
            const SizedBox(height: 12),
            _BlockMediaImage(block.media),
          ],
          const SizedBox(height: 14),
          startBtn,
        ],
      ),
    );
  }
}

// ── body ─────────────────────────────────────────────────────────────────────

class _SurveyBody extends StatefulWidget {
  final SurveyController controller;
  final SurveyConfigModel survey;
  final Color accent;
  final VoidCallback onClose;
  final VoidCallback onCompletedClose;
  final bool showCloseButton;

  const _SurveyBody({
    required this.controller,
    required this.survey,
    required this.accent,
    required this.onClose,
    required this.onCompletedClose,
    required this.showCloseButton,
  });

  @override
  State<_SurveyBody> createState() => _SurveyBodyState();
}

class _SurveyBodyState extends State<_SurveyBody> {
  int _remainingSecs = 0;
  Timer? _ticker;
  bool _completionReported = false;
  Timer? _autoAdvanceTimer;
  String? _autoAdvanceArmedFor;

  /// Last (node, answer) the clicked/answered event was emitted for. Guards
  /// against the manual Next CTA and the auto-advance timer both reporting the
  /// same step (which surfaced as the clicked event firing twice on a CTA tap).
  String? _lastAnsweredKey;

  SurveyController get _c => widget.controller;
  SurveyConfigModel get _survey => widget.survey;

  @override
  void initState() {
    super.initState();
    _remainingSecs = _survey.settings.timer.timeLimitSeconds;
    _c.addListener(_onChanged);
    _maybeStartTimer();
    _maybeScheduleAutoAdvance();
  }

  @override
  void dispose() {
    _c.removeListener(_onChanged);
    _ticker?.cancel();
    _autoAdvanceTimer?.cancel();
    super.dispose();
  }

  void _onChanged() {
    if (mounted) setState(() {});
    _maybeStartTimer();
    _maybeScheduleAutoAdvance();
  }

  void _maybeStartTimer() {
    final timer = _survey.settings.timer;
    if (!timer.enabled || timer.timeLimitSeconds <= 0) return;
    final block = _c.currentBlock;
    final paused =
        timer.pauseOnNonTimerBlock && (block?.type.isContent ?? false);
    if (paused) {
      _ticker?.cancel();
      _ticker = null;
      return;
    }
    if (_ticker != null) return;
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() => _remainingSecs = (_remainingSecs - 1).clamp(0, 1 << 30));
      if (_remainingSecs == 0) {
        _ticker?.cancel();
        widget.onClose();
      }
    });
  }

  void _reportCompletionIfResultIsNext() {
    if (!_completionReported && _c.nextBlockIsResultPage()) {
      DigiaInstance.instance
          .reportSurveyCompleted(_c.responsePayload(), _c.answers);
      _completionReported = true;
    }
  }

  void _maybeScheduleAutoAdvance() {
    final node = _c.currentNode;
    final block = _c.currentBlock;
    if (node == null || block == null) return;
    final answer = _c.answers[node.id];
    final candidate = _survey.settings.autoAdvance &&
        block.type.isAutoAdvanceCandidate &&
        answer?.isAnswered == true;
    if (!candidate) return;
    // Arm once per (node, answer) so re-emits of the same answer don't stack.
    final armKey =
        '${node.id}:${answer!.values.join(',')}:${answer.comment ?? ''}';
    if (_autoAdvanceArmedFor == armKey) return;
    _autoAdvanceArmedFor = armKey;
    _autoAdvanceTimer?.cancel();
    _autoAdvanceTimer = Timer(const Duration(milliseconds: 250), () {
      if (!mounted) return;
      if (_c.currentNode?.id != node.id) return;
      _reportAnswered(node, answer);
      _reportCompletionIfResultIsNext();
      _c.advance();
    });
  }

  /// Emits the per-step answered/clicked event, de-duplicated. Both the manual
  /// Next CTA and the auto-advance timer can report the same step; this ensures
  /// the clicked event fires at most once per (node, answer).
  void _reportAnswered(SurveyNode node, SurveyAnswer answer) {
    final key = '${node.id}:${answer.values.join(',')}:${answer.comment ?? ''}';
    if (_lastAnsweredKey == key) return;
    _lastAnsweredKey = key;
    // No welcome screen ⇒ no welcome CTA, so the survey's single `Clicked`
    // engagement signal is emitted on the first question's CTA instead.
    // Guarded to once per showing by DigiaInstance.
    if (_survey.welcomeBlock() == null) {
      DigiaInstance.instance.reportWelcomeCtaClicked();
    }
    DigiaInstance.instance.reportSurveyAnswered(node.id, answer.toMap());
  }

  void _onNext(SurveyNode node, SurveyBlock block) {
    // A manual Next supersedes any armed auto-advance for this node — cancel it
    // so the answered/clicked event can't be emitted twice for the same step.
    _autoAdvanceTimer?.cancel();
    if (!block.type.isContent) {
      final ans = _c.answers[node.id];
      if (ans != null && ans.isAnswered) {
        _reportAnswered(node, ans);
      }
    }
    _reportCompletionIfResultIsNext();
    _c.advance();
  }

  @override
  Widget build(BuildContext context) {
    final node = _c.currentNode;
    if (node == null) return const SizedBox.shrink();
    final block = _survey.blockFor(node);
    if (block == null) return const SizedBox.shrink();

    final pagination = _survey.settings.pagination;
    final timerCfg = _survey.settings.timer;
    final cta = _survey.settings.cta;

    // A branching survey stores one node per branch target, so the same screen
    // (block) can appear under several nodes — counting raw nodes overcounts the
    // steps. Count distinct blocks instead, in node order, so the bar shows one
    // segment per real screen regardless of how many branches reuse it.
    final orderedBlockIds = <String>[];
    for (final n in _survey.nodes) {
      if (!orderedBlockIds.contains(n.blockId)) orderedBlockIds.add(n.blockId);
    }
    final total = orderedBlockIds.isEmpty ? 1 : orderedBlockIds.length;
    final blockIndex = orderedBlockIds.indexOf(node.blockId);
    final position = blockIndex >= 0 ? blockIndex + 1 : 1;

    final hasInlineCta = block.type == SurveyBlockType.welcome ||
        block.type == SurveyBlockType.resultPage;
    final canAutoAdvanceThisBlock =
        _survey.settings.autoAdvance && block.type.isAutoAdvanceCandidate;
    final showNext = !hasInlineCta &&
        (_survey.settings.chooseButton || !canAutoAdvanceThisBlock);

    final blockBg = surveyColorOrNull(block.backgroundColorHex);
    final showBarHere = pagination.progressbar &&
        !(pagination.onlyShowOnQuestionBlock && block.type.isContent);

    return Container(
      width: double.infinity,
      color: blockBg,
      padding: EdgeInsets.only(
        left: 14,
        right: 14,
        top: 14,
        bottom: 14 + MediaQuery.of(context).viewInsets.bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Top row: progress + counter + timer + close
          Row(
            children: [
              if (showBarHere)
                Expanded(
                  child: _ProgressBar(
                    progress: position / total,
                    style: pagination.paginationStyle,
                    segments: total,
                    currentSegment: position,
                    accent: widget.accent,
                    indicator: pagination.progressIndicatorStyle,
                  ),
                )
              else
                const Spacer(),
              if (pagination.numberOfPages && !block.type.isContent) ...[
                const SizedBox(width: 10),
                Text('$position/$total',
                    style: const TextStyle(
                        color: SurveyTokens.textTertiary,
                        fontSize: 11,
                        fontWeight: FontWeight.w600)),
              ],
              if (timerCfg.enabled && timerCfg.timeLimitSeconds > 0) ...[
                const SizedBox(width: 10),
                _TimerChip(
                  remainingSecs: _remainingSecs,
                  warningAtSecs: timerCfg.warningAtSeconds,
                  accent: widget.accent,
                ),
              ],
              if (widget.showCloseButton &&
                  _survey.settings.display.dismissible) ...[
                const SizedBox(width: 10),
                _CloseButton(onTap: widget.onClose),
              ],
            ],
          ),
          const SizedBox(height: 14),
          Flexible(
            child: SingleChildScrollView(
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  maxHeight: block.flexibleHeight ? double.infinity : 480,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (block.showMedia &&
                        block.media.position == MediaPosition.top) ...[
                      _BlockMediaImage(block.media),
                      const SizedBox(height: 12),
                    ],
                    _CategoryPill(block: block, accent: widget.accent),
                    _BlockTitle(block: block),
                    if (block.showMedia &&
                        block.media.position == MediaPosition.inline) ...[
                      const SizedBox(height: 12),
                      _BlockMediaImage(block.media),
                    ],
                    const SizedBox(height: 12),
                    _blockContent(node, block),
                  ],
                ),
              ),
            ),
          ),
          if (showNext) ...[
            const SizedBox(height: 18),
            _FooterRow(
              cta: cta,
              accent: widget.accent,
              canGoBack: _c.canGoBack,
              onBack: _c.back,
              nextEnabled: _c.canAdvance(),
              nextLabel: _footerNextLabel(_survey, node, block, cta),
              onNext: () => _onNext(node, block),
            ),
          ],
        ],
      ),
    );
  }

  Widget _blockContent(SurveyNode node, SurveyBlock block) {
    switch (block.type) {
      case SurveyBlockType.welcome:
        return _CtaButton(
          label: 'Start →',
          background: widget.accent,
          foreground: Colors.white,
          cornerRadius: 8,
          onTap: () {
            DigiaInstance.instance.reportWelcomeCtaClicked();
            _c.advance();
          },
          fullWidth: false,
        );
      case SurveyBlockType.resultPage:
        return _ResultPagePanel(
          cta: _survey.settings.cta,
          accent: widget.accent,
          onDone: widget.onCompletedClose,
        );
      case SurveyBlockType.textMedia:
        return block.media.hasUrl
            ? const SizedBox.shrink()
            : const _MediaPlaceholder();
      default:
        return SurveyQuestionContent(
          block: block,
          nodeId: node.id,
          answer: _c.answers[node.id],
          accent: widget.accent,
          onAnswer: (a) => _c.setAnswer(node.id, a),
        );
    }
  }
}

// ── chrome pieces ──────────────────────────────────────────────────────────────

class _ProgressBar extends StatelessWidget {
  final double progress;
  final PaginationStyle style;
  final int segments;
  final int currentSegment;
  final Color accent;
  final ProgressIndicatorStyle indicator;

  const _ProgressBar({
    required this.progress,
    required this.style,
    required this.segments,
    required this.currentSegment,
    required this.accent,
    required this.indicator,
  });

  @override
  Widget build(BuildContext context) {
    final activeColor = surveyColorOrNull(indicator.activeColorHex) ?? accent;
    final trackColor = surveyColorOrNull(indicator.trackColorHex) ??
        SurveyTokens.surfaceSunken;
    final barHeight = indicator.height;
    final radius = BorderRadius.circular(indicator.cornerRadius);

    if (style == PaginationStyle.segmented && segments > 1) {
      return SizedBox(
        height: barHeight,
        child: Row(
          children: [
            for (var i = 1; i <= segments; i++) ...[
              if (i > 1) const SizedBox(width: 3),
              Expanded(
                child: Container(
                  decoration: BoxDecoration(
                    color: i <= currentSegment ? activeColor : trackColor,
                    borderRadius: radius,
                  ),
                ),
              ),
            ],
          ],
        ),
      );
    }
    return ClipRRect(
      borderRadius: radius,
      child: Container(
        height: barHeight,
        color: trackColor,
        child: FractionallySizedBox(
          alignment: Alignment.centerLeft,
          widthFactor: progress.clamp(0.0, 1.0),
          child: Container(color: activeColor),
        ),
      ),
    );
  }
}

class _TimerChip extends StatelessWidget {
  final int remainingSecs;
  final int warningAtSecs;
  final Color accent;

  const _TimerChip({
    required this.remainingSecs,
    required this.warningAtSecs,
    required this.accent,
  });

  @override
  Widget build(BuildContext context) {
    final warn = warningAtSecs > 0 && remainingSecs <= warningAtSecs;
    final tint = warn ? SurveyTokens.danger : accent;
    final minutes = remainingSecs ~/ 60;
    final seconds = remainingSecs % 60;
    return Container(
      decoration: BoxDecoration(
        color: tint.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      child: Text(
        '$minutes:${seconds.toString().padLeft(2, '0')}',
        style:
            TextStyle(color: tint, fontSize: 11, fontWeight: FontWeight.w600),
      ),
    );
  }
}

class _CategoryPill extends StatelessWidget {
  final SurveyBlock block;
  final Color accent;

  const _CategoryPill({required this.block, required this.accent});

  @override
  Widget build(BuildContext context) {
    if (block.type.isContent || !block.showTag) return const SizedBox.shrink();
    final label = _categoryLabel(block.type);
    if (label == null) return const SizedBox.shrink();
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 12),
        decoration: BoxDecoration(
          color: accent.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(999),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        child: Text(
          label.toUpperCase(),
          style: TextStyle(
              color: accent, fontSize: 10.5, fontWeight: FontWeight.bold),
        ),
      ),
    );
  }
}

class _BlockTitle extends StatelessWidget {
  final SurveyBlock block;
  const _BlockTitle({required this.block});

  @override
  Widget build(BuildContext context) {
    final body = block.body;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (block.title.text.isNotEmpty)
          Text(
            block.title.text,
            style: block.title.style.toTextStyle(titleDefaults),
            textAlign: block.title.style.flutterAlign,
          ),
        if (body != null && body.text.isNotEmpty) ...[
          const SizedBox(height: 6),
          Text(
            body.text,
            style: body.style.toTextStyle(bodyDefaults),
            textAlign: body.style.flutterAlign,
          ),
        ],
      ],
    );
  }
}

class _BlockMediaImage extends StatelessWidget {
  final BlockMedia media;
  const _BlockMediaImage(this.media);

  @override
  Widget build(BuildContext context) {
    if (!media.hasUrl) return const SizedBox.shrink();
    final fit = switch (media.boxFit) {
      'contain' => BoxFit.contain,
      'fill' => BoxFit.fill,
      _ => BoxFit.cover,
    };
    return Container(
      width: double.infinity,
      height: 176,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: SurveyTokens.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: Image.network(media.url,
          fit: fit, errorBuilder: (_, __, ___) => const SizedBox.shrink()),
    );
  }
}

class _MediaPlaceholder extends StatelessWidget {
  const _MediaPlaceholder();

  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        height: 96,
        decoration: BoxDecoration(
          color: SurveyTokens.surfaceSunken,
          borderRadius: BorderRadius.circular(10),
        ),
        alignment: Alignment.center,
        child: const Text('— image / video —',
            style: TextStyle(color: SurveyTokens.textTertiary, fontSize: 12)),
      );
}

class _ResultPagePanel extends StatelessWidget {
  final CtaSettings cta;
  final Color accent;
  final VoidCallback onDone;

  const _ResultPagePanel(
      {required this.cta, required this.accent, required this.onDone});

  @override
  Widget build(BuildContext context) => _CtaButton(
        label: cta.doneLabel,
        background: _ctaBg(cta, accent),
        foreground: _ctaText(cta),
        cornerRadius: cta.cornerRadius.toDouble(),
        onTap: onDone,
        fullWidth: cta.layout == CtaLayout.stacked,
      );
}

class _FooterRow extends StatelessWidget {
  final CtaSettings cta;
  final Color accent;
  final bool canGoBack;
  final VoidCallback onBack;
  final bool nextEnabled;
  final String nextLabel;
  final VoidCallback onNext;

  const _FooterRow({
    required this.cta,
    required this.accent,
    required this.canGoBack,
    required this.onBack,
    required this.nextEnabled,
    required this.nextLabel,
    required this.onNext,
  });

  @override
  Widget build(BuildContext context) {
    final bg = _ctaBg(cta, accent);
    final textColor = _ctaText(cta);
    final radius = cta.cornerRadius.toDouble();

    Widget nextButton({required bool fullWidth}) => _CtaButton(
          label: nextLabel,
          background: nextEnabled ? bg : bg.withValues(alpha: 0.35),
          foreground: textColor,
          cornerRadius: radius,
          onTap: nextEnabled ? onNext : null,
          fullWidth: fullWidth,
        );

    if (cta.layout == CtaLayout.stacked) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          nextButton(fullWidth: true),
          if (canGoBack) ...[
            const SizedBox(height: 10),
            OutlinedButton(
              onPressed: onBack,
              style: OutlinedButton.styleFrom(
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(radius)),
                padding:
                    const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
              ),
              child: Text(cta.backLabel,
                  style: const TextStyle(color: SurveyTokens.textPrimary)),
            ),
          ],
        ],
      );
    }

    return Row(
      mainAxisAlignment: _rowArrangement(cta.arrangement),
      children: [
        if (canGoBack) ...[
          TextButton(
            onPressed: onBack,
            child: Text(cta.backLabel,
                style: const TextStyle(color: SurveyTokens.textSecondary)),
          ),
          const SizedBox(width: 12),
        ],
        nextButton(fullWidth: false),
      ],
    );
  }
}

class _CtaButton extends StatelessWidget {
  final String label;
  final Color background;
  final Color foreground;
  final double cornerRadius;
  final VoidCallback? onTap;
  final bool fullWidth;

  const _CtaButton({
    required this.label,
    required this.background,
    required this.foreground,
    required this.cornerRadius,
    required this.onTap,
    required this.fullWidth,
  });

  @override
  Widget build(BuildContext context) {
    final button = Material(
      color: background,
      borderRadius: BorderRadius.circular(cornerRadius),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
          child: Center(
            widthFactor: fullWidth ? null : 1,
            child: Text(label,
                style:
                    TextStyle(color: foreground, fontWeight: FontWeight.w600)),
          ),
        ),
      ),
    );
    return fullWidth ? SizedBox(width: double.infinity, child: button) : button;
  }
}

class _CloseButton extends StatelessWidget {
  final VoidCallback onTap;
  const _CloseButton({required this.onTap});

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(13),
        child: const SizedBox(
          width: 26,
          height: 26,
          child: Icon(Icons.close, size: 18, color: SurveyTokens.textTertiary),
        ),
      );
}

// ── helpers ──────────────────────────────────────────────────────────────────

Color _ctaBg(CtaSettings cta, Color accent) =>
    surveyColorOrNull(cta.bgColorHex) ?? accent;
Color _ctaText(CtaSettings cta) =>
    surveyColorOrNull(cta.textColorHex) ?? Colors.white;

MainAxisAlignment _rowArrangement(CtaArrangement arrangement) =>
    switch (arrangement) {
      CtaArrangement.spaceBetween => MainAxisAlignment.spaceBetween,
      CtaArrangement.spaceEvenly => MainAxisAlignment.spaceEvenly,
      CtaArrangement.center => MainAxisAlignment.center,
      CtaArrangement.start => MainAxisAlignment.start,
      CtaArrangement.end => MainAxisAlignment.end,
    };

/// Footer Next-button label. "Done" on the terminal node; "Next" otherwise.
String _footerNextLabel(
  SurveyConfigModel survey,
  SurveyNode node,
  SurveyBlock block,
  CtaSettings cta,
) {
  if (block.type == SurveyBlockType.textMedia) return cta.nextLabel;
  final target = node.branching.defaultTarget;
  final noRules = node.branching.rules.isEmpty;
  final terminates = noRules &&
      switch (target.kind) {
        BranchTargetKind.end => true,
        BranchTargetKind.next =>
          survey.nodes.indexWhere((n) => n.id == node.id) ==
              survey.nodes.length - 1,
        _ => false,
      };
  return terminates ? cta.doneLabel : cta.nextLabel;
}

String? _categoryLabel(SurveyBlockType type) => switch (type) {
      SurveyBlockType.singleSelect => 'Select one answer',
      SurveyBlockType.multiSelect => 'Select all that apply',
      SurveyBlockType.rating => 'Rate it',
      SurveyBlockType.nps ||
      SurveyBlockType.npsEmoji ||
      SurveyBlockType.npsSmiley =>
        'Promoter score',
      SurveyBlockType.reaction => 'Reaction poll',
      SurveyBlockType.thisOrThat => 'This or that',
      SurveyBlockType.tierList => 'Tier list',
      SurveyBlockType.upvote => 'Upvote',
      SurveyBlockType.shortText => 'Short text',
      SurveyBlockType.longText => 'Long text',
      SurveyBlockType.number => 'Number',
      SurveyBlockType.email => 'Email',
      SurveyBlockType.date => 'Date picker',
      SurveyBlockType.welcome ||
      SurveyBlockType.textMedia ||
      SurveyBlockType.resultPage =>
        null,
    };
