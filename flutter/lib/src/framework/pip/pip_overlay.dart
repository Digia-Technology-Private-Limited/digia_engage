import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

import '../ui_factory.dart';
import 'pip_types.dart';

// MARK: - PipManager

/// Singleton that owns the active PiP route and exposes show/dismiss.
class PipManager {
  PipManager._();
  static final PipManager instance = PipManager._();

  _PipRoute? _currentRoute;

  void show(NavigatorState navigator, PipRequest request) {
    dismiss(); // dismiss any existing pip first
    final route = _PipRoute(request: request, onPipDismissed: _onRouteClosed);
    _currentRoute = route;
    navigator.push(route);
  }

  void dismiss() {
    final route = _currentRoute;
    _currentRoute = null;
    if (route != null && route.isActive) {
      route.navigator?.removeRoute(route);
    }
    route?.request.onDismiss?.call(null);
  }

  /// Called when the active screen changes (from DigiaNavigatorObserver).
  /// Dismisses PiP if `closeOnScreenChange` is set or the new screen is blocked by screenFilter.
  void onScreenChanged(String screenName) {
    final route = _currentRoute;
    if (route == null) return;
    final req = route.request;
    final shouldDismiss = req.closeOnScreenChange ||
        (req.screenFilter != null && !req.screenFilter!.isAllowed(screenName));
    if (shouldDismiss) dismiss();
  }

  void _onRouteClosed() {
    _currentRoute = null;
  }
}

// MARK: - _PipRoute

class _PipRoute extends PopupRoute<void> {
  final PipRequest request;
  final VoidCallback onPipDismissed;

  // Shared state so PopScope inside buildPage can read/mutate expand state.
  bool isExpanded = false;

  _PipRoute({required this.request, required this.onPipDismissed});

  @override
  Color? get barrierColor => null;

  @override
  bool get barrierDismissible => false;

  @override
  String? get barrierLabel => null;

  @override
  Duration get transitionDuration => Duration.zero;

  @override
  bool get opaque => false;

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (isExpanded) {
          // Back while expanded → collapse
          isExpanded = false;
          changedInternalState();
        } else {
          // Back while collapsed → dismiss pip + propagate pop
          PipManager.instance.dismiss();
        }
      },
      child: _PipOverlayWidget(
        route: this,
        request: request,
        onDismiss: () => PipManager.instance.dismiss(),
      ),
    );
  }

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    return child;
  }

  @override
  void didComplete(void result) {
    onPipDismissed();
    super.didComplete(result);
  }
}

// MARK: - _PipOverlayWidget

class _PipOverlayWidget extends StatefulWidget {
  final _PipRoute route;
  final PipRequest request;
  final VoidCallback onDismiss;

  const _PipOverlayWidget({
    required this.route,
    required this.request,
    required this.onDismiss,
  });

  @override
  State<_PipOverlayWidget> createState() => _PipOverlayWidgetState();
}

class _PipOverlayWidgetState extends State<_PipOverlayWidget>
    with SingleTickerProviderStateMixin {
  late AnimationController _animCtrl;
  late Animation<double> _scaleAnim;
  late Animation<double> _opacityAnim;

  double _left = 0;
  double _top = 0;
  bool _isVisible = false;
  double _lastCollapsedLeft = 0;
  double _lastCollapsedTop = 0;

  bool get _isExpanded => widget.route.isExpanded;

  Size get _screen => MediaQuery.sizeOf(context);
  EdgeInsets get _safePad => MediaQuery.paddingOf(context);
  Size get _pipSize =>
      Size(widget.request.widthDp, widget.request.heightDp);

  double get _minX {
    final frac = (widget.request.dragBounds?.minXFraction ?? 0) * _screen.width;
    return frac;
  }

  double get _maxX {
    final frac =
        (widget.request.dragBounds?.maxXFraction ?? 1) * _screen.width;
    return frac;
  }

  double get _minY {
    final frac =
        (widget.request.dragBounds?.minYFraction ?? 0) * _screen.height;
    return frac < _safePad.top ? _safePad.top : frac;
  }

  double get _maxY {
    final frac =
        (widget.request.dragBounds?.maxYFraction ?? 1) * _screen.height;
    final safeBottom = _screen.height - _safePad.bottom;
    return frac > safeBottom ? safeBottom : frac;
  }

  @override
  void initState() {
    super.initState();
    final ms = widget.request.animationDurationMs;
    _animCtrl = AnimationController(
      vsync: this,
      duration: Duration(milliseconds: ms),
    );
    _scaleAnim = Tween<double>(begin: 0.85, end: 1.0).animate(
      CurvedAnimation(parent: _animCtrl, curve: Curves.easeOut),
    );
    _opacityAnim = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _animCtrl, curve: Curves.easeOut),
    );
    WidgetsBinding.instance.addPostFrameCallback((_) => _setup());
  }

  void _setup() async {
    _initPosition();
    if (widget.request.delayMs > 0) {
      await Future.delayed(Duration(milliseconds: widget.request.delayMs));
    }
    if (!mounted) return;
    setState(() => _isVisible = true);
    _animCtrl.forward();
    widget.request.onEvent?.call(PipEvent.shown, {
      'pip_type': widget.request.videoUrl != null ? 'video' : 'component',
      'componentId': widget.request.componentId,
    });
    if (widget.request.autoDismissMs > 0) {
      await Future.delayed(
          Duration(milliseconds: widget.request.autoDismissMs));
      if (!mounted) return;
      widget.request.onEvent
          ?.call(PipEvent.dismissed, {'dismiss_type': 'auto_dismiss'});
      widget.onDismiss();
    }
  }

  void _initPosition() {
    late double x, y;
    if (widget.request.position != null) {
      final origin = widget.request.position!
          .resolvedOrigin(pipSize: _pipSize, screenSize: _screen);
      x = origin.dx;
      y = origin.dy;
    } else {
      x = _screen.width * widget.request.startX;
      y = _screen.height * widget.request.startY;
    }
    final snapped = _snapCorner(x, y);
    setState(() {
      _left = snapped.dx;
      _top = snapped.dy;
      _lastCollapsedLeft = snapped.dx;
      _lastCollapsedTop = snapped.dy;
    });
  }

  Offset _snapCorner(double x, double y) {
    final effectiveMaxX = (_maxX - _pipSize.width).clamp(_minX, double.infinity);
    final effectiveMaxY = (_maxY - _pipSize.height).clamp(_minY, double.infinity);
    final midX = (_minX + effectiveMaxX) / 2;
    final midY = (_minY + effectiveMaxY) / 2;
    final snapX = (x + _pipSize.width / 2) < midX ? _minX : effectiveMaxX;
    final snapY = (y + _pipSize.height / 2) < midY ? _minY : effectiveMaxY;
    return Offset(snapX, snapY);
  }

  void _expand() {
    _lastCollapsedLeft = _left;
    _lastCollapsedTop = _top;
    setState(() => widget.route.isExpanded = true);
    widget.route.changedInternalState();
    widget.request.onEvent?.call(PipEvent.expand, {'source': 'button'});
  }

  void _collapse() {
    setState(() => widget.route.isExpanded = false);
    widget.route.changedInternalState();
    setState(() {
      _left = _lastCollapsedLeft;
      _top = _lastCollapsedTop;
    });
    widget.request.onEvent?.call(PipEvent.collapse, {'source': 'button'});
  }

  void _handleDismiss() {
    widget.request.onEvent
        ?.call(PipEvent.close, {'dismiss_type': 'close_button'});
    widget.request.onEvent
        ?.call(PipEvent.dismissed, {'dismiss_type': 'close_button'});
    widget.onDismiss();
  }

  @override
  void dispose() {
    _animCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_isVisible) return const SizedBox.expand();

    final dur = Duration(milliseconds: widget.request.animationDurationMs);
    final w = _isExpanded ? _screen.width : _pipSize.width;
    final h = _isExpanded ? _screen.height : _pipSize.height;
    final effectiveLeft = _isExpanded ? 0.0 : _left;
    final effectiveTop = _isExpanded ? 0.0 : _top;

    return AnimatedBuilder(
      animation: _animCtrl,
      builder: (context, child) => Opacity(
        opacity: _opacityAnim.value,
        child: Transform.scale(scale: _scaleAnim.value, child: child),
      ),
      child: Stack(
        children: [
          AnimatedPositioned(
            duration: dur,
            curve: Curves.easeOut,
            left: effectiveLeft,
            top: effectiveTop,
            width: w,
            height: h,
            child: GestureDetector(
              onPanUpdate: _isExpanded
                  ? null
                  : (d) {
                      final effectiveMaxX =
                          (_maxX - _pipSize.width).clamp(_minX, double.infinity);
                      final effectiveMaxY =
                          (_maxY - _pipSize.height).clamp(_minY, double.infinity);
                      setState(() {
                        _left = (_left + d.delta.dx).clamp(_minX, effectiveMaxX);
                        _top = (_top + d.delta.dy).clamp(_minY, effectiveMaxY);
                      });
                    },
              onPanEnd: _isExpanded
                  ? null
                  : (_) {
                      final snapped = _snapCorner(_left, _top);
                      setState(() {
                        _left = snapped.dx;
                        _top = snapped.dy;
                        _lastCollapsedLeft = snapped.dx;
                        _lastCollapsedTop = snapped.dy;
                      });
                    },
              child: AnimatedContainer(
                duration: dur,
                curve: Curves.easeOut,
                decoration: BoxDecoration(
                  color: widget.request.backgroundColor,
                  borderRadius: BorderRadius.circular(
                    _isExpanded ? 0 : widget.request.cornerRadius,
                  ),
                ),
                clipBehavior: Clip.antiAlias,
                child: widget.request.videoUrl != null
                    ? _PipVideoContent(
                        request: widget.request,
                        isExpanded: _isExpanded,
                        onToggleExpand:
                            _isExpanded ? _collapse : _expand,
                        onDismiss: _handleDismiss,
                      )
                    : _PipComponentContent(
                        request: widget.request,
                        onDismiss: _handleDismiss,
                      ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// MARK: - Video content

class _PipVideoContent extends StatefulWidget {
  final PipRequest request;
  final bool isExpanded;
  final VoidCallback onToggleExpand;
  final VoidCallback onDismiss;

  const _PipVideoContent({
    required this.request,
    required this.isExpanded,
    required this.onToggleExpand,
    required this.onDismiss,
  });

  @override
  State<_PipVideoContent> createState() => _PipVideoContentState();
}

class _PipVideoContentState extends State<_PipVideoContent> {
  VideoPlayerController? _controller;
  bool _isPlaying = false;
  bool _isMuted = false;
  bool _isBuffering = true;

  @override
  void initState() {
    super.initState();
    _setupPlayer();
  }

  Future<void> _setupPlayer() async {
    final url = widget.request.videoUrl;
    if (url == null) return;
    final ctrl = VideoPlayerController.networkUrl(Uri.parse(url));
    await ctrl.initialize();
    if (!mounted) {
      ctrl.dispose();
      return;
    }
    ctrl.addListener(_onPlayerStateChanged);
    ctrl.setVolume(widget.request.muted ? 0 : 1);
    ctrl.setLooping(widget.request.looping);
    if (widget.request.autoPlay) ctrl.play();
    setState(() {
      _controller = ctrl;
      _isPlaying = widget.request.autoPlay;
      _isMuted = widget.request.muted;
      _isBuffering = false;
    });
  }

  void _onPlayerStateChanged() {
    if (!mounted) return;
    final ctrl = _controller;
    if (ctrl == null) return;
    final buffering = ctrl.value.isBuffering;
    if (buffering != _isBuffering) setState(() => _isBuffering = buffering);
    if (ctrl.value.isPlaying && !_isPlaying) {
      setState(() => _isPlaying = true);
      widget.request.onEvent?.call(
          PipEvent.videoStarted, {'videoUrl': widget.request.videoUrl ?? ''});
    }
  }

  @override
  void dispose() {
    _controller?.removeListener(_onPlayerStateChanged);
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = _controller;
    return Stack(
      children: [
        if (ctrl != null)
          Positioned.fill(
            child: FittedBox(
              fit: BoxFit.cover,
              child: SizedBox(
                width: ctrl.value.size.width,
                height: ctrl.value.size.height,
                child: VideoPlayer(ctrl),
              ),
            ),
          ),
        if (_isBuffering)
          const Center(
            child: SizedBox(
              width: 36,
              height: 36,
              child: CircularProgressIndicator(
                  strokeWidth: 2.5, color: Colors.white),
            ),
          ),
        Positioned(
          top: 0,
          right: 0,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              _ControlBtn(
                icon: _isMuted ? Icons.volume_off : Icons.volume_up,
                onTap: () {
                  setState(() => _isMuted = !_isMuted);
                  _controller?.setVolume(_isMuted ? 0 : 1);
                  widget.request.onEvent?.call(
                    _isMuted ? PipEvent.mute : PipEvent.unmute,
                    {},
                  );
                },
              ),
              _ControlBtn(
                icon: _isPlaying ? Icons.pause : Icons.play_arrow,
                onTap: () {
                  setState(() => _isPlaying = !_isPlaying);
                  _isPlaying ? _controller?.play() : _controller?.pause();
                  widget.request.onEvent?.call(
                    _isPlaying ? PipEvent.play : PipEvent.pause,
                    {},
                  );
                },
              ),
              if (widget.request.expandable)
                _ControlBtn(
                  icon: widget.isExpanded
                      ? Icons.fullscreen_exit
                      : Icons.fullscreen,
                  onTap: widget.onToggleExpand,
                ),
              if (widget.request.showClose)
                _ControlBtn(icon: Icons.close, onTap: widget.onDismiss),
            ],
          ),
        ),
      ],
    );
  }
}

// MARK: - Component content

class _PipComponentContent extends StatelessWidget {
  final PipRequest request;
  final VoidCallback onDismiss;

  const _PipComponentContent(
      {required this.request, required this.onDismiss});

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Positioned.fill(
          child: DUIFactory().createComponent(
            request.componentId,
            request.args ?? {},
          ),
        ),
        if (request.showClose)
          Positioned(
            top: 6,
            right: 6,
            child: GestureDetector(
              onTap: onDismiss,
              child: Container(
                width: 24,
                height: 24,
                decoration: const BoxDecoration(
                  color: Color(0x80000000),
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.close, color: Colors.white, size: 14),
              ),
            ),
          ),
      ],
    );
  }
}

// MARK: - Control button

class _ControlBtn extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;

  const _ControlBtn({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 28,
        height: 28,
        alignment: Alignment.center,
        child: Icon(icon, color: Colors.white, size: 16),
      ),
    );
  }
}
