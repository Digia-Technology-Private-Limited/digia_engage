import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

import '../../internal/action/engage_action.dart';
import '../../internal/action/engage_action_context.dart';
import '../../internal/action/engage_action_handler.dart';
import '../../internal/campaign/inline_story_config.dart';
import '../../internal/digia_instance.dart';
import '../../internal/event/engage_matrix.dart';
import '../../internal/variable_scope.dart';

/// Pushes the full-screen story viewer for [config], starting at [initialIndex].
///
/// Mirrors the Android `DigiaStoryOverlay` (a full-screen modal driven by the
/// same `InlineStoryConfig`). [scope] carries the trigger's runtime variables,
/// used to interpolate CTA copy — it is re-provided inside the pushed route
/// (InheritedWidgets don't cross route boundaries). [actionContext] is forwarded
/// to the engage action runner so CTA links flow through the host's `onAction`
/// override.
Future<void> openStoryOverlay({
  required BuildContext context,
  required InlineStoryConfig config,
  required int initialIndex,
  VariableScope scope = VariableScope.empty,
  EngageActionContext actionContext = EngageActionContext.unknown,
}) {
  return Navigator.of(context, rootNavigator: true).push(
    PageRouteBuilder<void>(
      opaque: false,
      barrierColor: Colors.black,
      transitionDuration: const Duration(milliseconds: 200),
      pageBuilder: (_, __, ___) => VariableScopeProvider(
        scope: scope,
        child: _DigiaStoryOverlay(
          config: config,
          initialIndex: initialIndex,
          actionContext: actionContext,
        ),
      ),
      transitionsBuilder: (_, animation, __, child) =>
          FadeTransition(opacity: animation, child: child),
    ),
  );
}

/// Full-screen story player: progress bars, tap-to-navigate, hold-to-pause,
/// auto-advance, and per-item image/video rendering with an optional CTA.
class _DigiaStoryOverlay extends StatefulWidget {
  final InlineStoryConfig config;
  final int initialIndex;
  final EngageActionContext actionContext;

  const _DigiaStoryOverlay({
    required this.config,
    required this.initialIndex,
    required this.actionContext,
  });

  @override
  State<_DigiaStoryOverlay> createState() => _DigiaStoryOverlayState();
}

class _DigiaStoryOverlayState extends State<_DigiaStoryOverlay>
    with SingleTickerProviderStateMixin {
  /// Drives the progress bar for image items (videos are driven by playback).
  late final AnimationController _imageController;

  /// Active video controller for the current item, or `null` for images.
  VideoPlayerController? _videoController;

  late int _currentIndex;
  bool _paused = false;

  /// When the viewer opened — drives `time_to_complete_ms` on completion.
  final DateTime _openedAt = DateTime.now();

  /// Guards against double-emitting a terminal (Completed/Dismissed) event when
  /// both a navigation path and route teardown could fire.
  bool _terminated = false;

  /// Progress of the current item, 0..1. Fed by either the image animation or
  /// the video's playback position.
  double _progress = 0;

  List<StoryItemConfig> get _items => widget.config.items;

  StoryItemConfig get _current => _items[_currentIndex];

  /// Emits a story matrix event for this slot's active campaign, if any.
  /// Story widgets resolve their payload from the slot key (the overlay is a
  /// pushed route, decoupled from [DigiaSlot]), mirroring how the nudge renderer
  /// reaches analytics through [DigiaInstance].
  void _emit(
    String eventName,
    Map<String, dynamic> properties, {
    bool flush = false,
  }) {
    final payload =
        DigiaInstance.instance.controller.getSlot(widget.config.slotKey);
    if (payload == null) return;
    DigiaInstance.instance.events
        .analytics(eventName, payload, properties: properties, flush: flush);
  }

  /// Maps a story CTA action type (`openUrl` / `deepLink`) to the matrix token.
  String? _ctaActionType(StoryCtaAction? action) => switch (action?.type) {
        'openUrl' => 'url',
        'deepLink' => 'deeplink',
        _ => null,
      };

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex.clamp(0, _items.length - 1);
    _imageController = AnimationController(vsync: this)
      ..addListener(_onImageTick)
      ..addStatusListener(_onImageStatus);
    _startItem(_currentIndex);
  }

  @override
  void dispose() {
    _imageController.dispose();
    _disposeVideo();
    super.dispose();
  }

  // ─── Item lifecycle ────────────────────────────────────────────────────────

  void _startItem(int index) {
    _disposeVideo();
    _imageController.stop();
    setState(() {
      _currentIndex = index;
      _progress = 0;
    });

    // Each frame becoming current is a `Digia Step Viewed`.
    _emit(
      'Digia Step Viewed',
      inlineStepProperties(
        displayStyle: 'story',
        itemIndex: index,
        itemTotal: _items.length,
      ),
    );

    final item = _items[index];
    if (item.isVideo) {
      _startVideo(item);
    } else {
      _startImage(item);
    }
  }

  void _startImage(StoryItemConfig item) {
    final ms = item.duration ?? widget.config.defaultDuration;
    _imageController
      ..duration = Duration(milliseconds: ms)
      ..forward(from: 0);
  }

  void _startVideo(StoryItemConfig item) {
    final controller = VideoPlayerController.networkUrl(Uri.parse(item.url));
    _videoController = controller;
    controller.initialize().then((_) {
      if (!mounted || _videoController != controller) return;
      controller
        ..addListener(_onVideoTick)
        ..setLooping(false);
      if (!_paused) controller.play();
      setState(() {});
    }).catchError((_) {
      // Unplayable video — fall back to the default image-style timer so the
      // story still advances instead of stalling.
      if (!mounted || _videoController != controller) return;
      _disposeVideo();
      _startImage(item);
    });
  }

  void _disposeVideo() {
    final controller = _videoController;
    if (controller == null) return;
    controller.removeListener(_onVideoTick);
    controller.dispose();
    _videoController = null;
  }

  // ─── Progress ticks ────────────────────────────────────────────────────────

  void _onImageTick() {
    if (_videoController != null) return;
    setState(() => _progress = _imageController.value);
  }

  void _onImageStatus(AnimationStatus status) {
    if (status == AnimationStatus.completed) _next();
  }

  void _onVideoTick() {
    final controller = _videoController;
    if (controller == null || !controller.value.isInitialized) return;
    final total = controller.value.duration.inMilliseconds;
    if (total <= 0) return;
    final position = controller.value.position.inMilliseconds;
    setState(() => _progress = (position / total).clamp(0.0, 1.0));
    if (position >= total) _next();
  }

  // ─── Navigation ──────────────────────────────────────────────────────────────

  void _next() {
    if (_currentIndex < _items.length - 1) {
      _startItem(_currentIndex + 1);
    } else if (widget.config.restartOnCompleted) {
      _startItem(0);
    } else {
      _complete();
    }
  }

  /// Playing through the final frame is the matrix `Digia Experience Completed`.
  void _complete() {
    if (!_terminated) {
      _terminated = true;
      _emit(
        'Digia Experience Completed',
        inlineCompletedProperties(
          displayStyle: 'story',
          itemTotal: _items.length,
          timeToCompleteMs: DateTime.now().difference(_openedAt).inMilliseconds,
        ),
        flush: true,
      );
    }
    if (mounted) Navigator.of(context).maybePop();
  }

  void _previous() {
    if (_currentIndex > 0) {
      _startItem(_currentIndex - 1);
    } else {
      _startItem(0);
    }
  }

  void _pause() {
    if (_paused) return;
    _paused = true;
    _imageController.stop();
    _videoController?.pause();
  }

  void _resume() {
    if (!_paused) return;
    _paused = false;
    if (_videoController != null) {
      _videoController?.play();
    } else if (_imageController.duration != null) {
      _imageController.forward(from: _imageController.value);
    }
  }

  /// Closing the viewer before the last frame ends (swipe-down/back) is a
  /// `Digia Step Dismissed` for the frame the user was on.
  void _dismiss() {
    if (!_terminated) {
      _terminated = true;
      _emit(
        'Digia Step Dismissed',
        inlineStepProperties(
          displayStyle: 'story',
          itemIndex: _currentIndex,
          itemTotal: _items.length,
        ),
        flush: true,
      );
    }
    if (!mounted) return;
    Navigator.of(context).maybePop();
  }

  void _onTapUp(TapUpDetails details) {
    final width = MediaQuery.of(context).size.width;
    if (details.globalPosition.dx < width / 2) {
      _previous();
    } else {
      _next();
    }
  }

  // ─── CTA ─────────────────────────────────────────────────────────────────────

  void _onCtaTap() {
    final item = _current;
    final actions = <EngageAction>[];
    final action = item.ctaAction;
    final url = action?.url;
    if (url != null && url.isNotEmpty) {
      switch (action!.type) {
        case 'openUrl':
          actions.add(OpenUrlAction(url));
        case 'deepLink':
          actions.add(OpenDeeplinkAction(url));
      }
    }

    // The CTA tap is a `Digia Step Clicked` for the current frame.
    final hasLink = url != null && url.isNotEmpty;
    _emit(
      'Digia Step Clicked',
      inlineStepProperties(
        displayStyle: 'story',
        itemIndex: _currentIndex,
        itemTotal: _items.length,
        actionType: hasLink ? _ctaActionType(action) : null,
        actionUrl: hasLink ? url : null,
      ),
    );

    // Always dismiss after the (optional) link, matching Android's CTA handler.
    actions.add(const HideAction());
    EngageActionRunner.shared.run(
      actions,
      EngageActionScope.fromContext(context),
      widget.actionContext,
    );
  }

  // ─── Build ─────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final item = _current;
    final indicator = widget.config.indicator;

    return Scaffold(
      backgroundColor: Colors.black,
      body: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapUp: _onTapUp,
        onLongPressStart: (_) => _pause(),
        onLongPressEnd: (_) => _resume(),
        onVerticalDragEnd: (details) {
          if ((details.primaryVelocity ?? 0) > 0) _dismiss();
        },
        child: Stack(
          fit: StackFit.expand,
          children: [
            _StoryItemView(
              key: ValueKey(_currentIndex),
              item: item,
              videoController: _videoController,
            ),
            // Progress bars pinned to the very top — `top/left/right` (no
            // `bottom`) lets the row keep its intrinsic height instead of being
            // stretched by the expanded Stack (which would centre it).
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: SafeArea(
                bottom: false,
                child: _StoryProgressBar(
                  itemCount: _items.length,
                  currentIndex: _currentIndex,
                  progress: _progress,
                  config: indicator,
                ),
              ),
            ),
            if (item.ctaEnabled && (item.ctaText?.isNotEmpty ?? false))
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: SafeArea(
                  top: false,
                  child: Padding(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
                    child: _StoryCtaButton(
                      text: VariableScopeProvider.of(context).resolve(item.ctaText!),
                      textColor: _parseHexColor(item.ctaTextColor) ??
                          Colors.white,
                      backgroundColor: _parseHexColor(item.ctaBackgroundColor) ??
                          const Color(0xFF4945FF),
                      cornerRadius: item.ctaCornerRadius.toDouble(),
                      onTap: _onCtaTap,
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// Renders the current story media — a cached network image or a playing video.
class _StoryItemView extends StatelessWidget {
  final StoryItemConfig item;
  final VideoPlayerController? videoController;

  const _StoryItemView({
    super.key,
    required this.item,
    required this.videoController,
  });

  @override
  Widget build(BuildContext context) {
    if (item.isVideo) {
      final controller = videoController;
      if (controller != null && controller.value.isInitialized) {
        return FittedBox(
          fit: BoxFit.cover,
          clipBehavior: Clip.hardEdge,
          child: SizedBox(
            width: controller.value.size.width,
            height: controller.value.size.height,
            child: VideoPlayer(controller),
          ),
        );
      }
      return const ColoredBox(color: Color(0xFF1A1A1A));
    }

    return CachedNetworkImage(
      imageUrl: item.url,
      fit: BoxFit.cover,
      placeholder: (_, __) => const ColoredBox(color: Color(0xFF1A1A1A)),
      errorWidget: (_, __, ___) => const ColoredBox(color: Color(0xFF2A2A2A)),
    );
  }
}

/// The row of segmented progress bars at the top of the viewer.
///
/// Dart port of the Android `StoryIndicator`: completed segments are filled,
/// the current one fills by [progress], upcoming ones stay disabled.
class _StoryProgressBar extends StatelessWidget {
  final int itemCount;
  final int currentIndex;
  final double progress;
  final StoryIndicatorDisplayConfig config;

  const _StoryProgressBar({
    required this.itemCount,
    required this.currentIndex,
    required this.progress,
    required this.config,
  });

  @override
  Widget build(BuildContext context) {
    final active = _parseHexColor(config.activeColor) ?? Colors.white;
    final completed =
        _parseHexColor(config.completedColor) ?? const Color(0xFFAAAAAA);
    final disabled =
        _parseHexColor(config.disabledColor) ?? const Color(0xFF555555);

    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: config.horizontalPadding,
        vertical: config.topPadding,
      ),
      child: Row(
        children: [
          for (var i = 0; i < itemCount; i++) ...[
            if (i > 0) SizedBox(width: config.horizontalGap),
            Expanded(
              child: _ProgressSegment(
                fill: i < currentIndex
                    ? 1.0
                    : i == currentIndex
                        ? progress.clamp(0.0, 1.0)
                        : 0.0,
                activeColor: active,
                backgroundColor: i < currentIndex ? completed : disabled,
                height: config.height,
                borderRadius: config.borderRadius,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _ProgressSegment extends StatelessWidget {
  final double fill;
  final Color activeColor;
  final Color backgroundColor;
  final double height;
  final double borderRadius;

  const _ProgressSegment({
    required this.fill,
    required this.activeColor,
    required this.backgroundColor,
    required this.height,
    required this.borderRadius,
  });

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(borderRadius);
    return ClipRRect(
      borderRadius: radius,
      child: Container(
        height: height,
        color: backgroundColor,
        child: FractionallySizedBox(
          alignment: Alignment.centerLeft,
          widthFactor: fill,
          child: Container(color: activeColor),
        ),
      ),
    );
  }
}

class _StoryCtaButton extends StatelessWidget {
  final String text;
  final Color textColor;
  final Color backgroundColor;
  final double cornerRadius;
  final VoidCallback onTap;

  const _StoryCtaButton({
    required this.text,
    required this.textColor,
    required this.backgroundColor,
    required this.cornerRadius,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton(
        onPressed: onTap,
        style: ElevatedButton.styleFrom(
          backgroundColor: backgroundColor,
          foregroundColor: textColor,
          elevation: 0,
          padding: const EdgeInsets.symmetric(vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(cornerRadius),
          ),
        ),
        child: Text(
          text,
          style: TextStyle(
            color: textColor,
            fontSize: 16,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}

/// Parses `#RRGGBB` or `#AARRGGBB` hex strings. Returns `null` for unparseable
/// input so the caller can fall back to a default.
Color? _parseHexColor(String hex) {
  var value = hex.trim();
  if (value.startsWith('#')) value = value.substring(1);
  if (value.length == 6) value = 'FF$value';
  if (value.length != 8) return null;
  final intValue = int.tryParse(value, radix: 16);
  if (intValue == null) return null;
  return Color(intValue);
}
