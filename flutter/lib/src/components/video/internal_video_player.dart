import 'package:chewie/chewie.dart';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

class InternalVideoPlayer extends StatefulWidget {
  final String videoUrl;
  final bool? showControls;
  final double? aspectRatio;
  final bool? autoPlay;
  final bool? looping;

  const InternalVideoPlayer({
    super.key,
    required this.videoUrl,
    this.showControls,
    this.aspectRatio,
    this.autoPlay,
    this.looping,
  });

  @override
  State<InternalVideoPlayer> createState() => _InternalVideoPlayerState();
}

class _InternalVideoPlayerState extends State<InternalVideoPlayer> {
  late VideoPlayerController _videoPlayerController;
  ChewieController? _chewieController;
  bool _isInitialized = false;
  bool _initializationError = false;
  String? _initializationErrorMessage;
  double _aspectRatio = 16 / 9;

  @override
  void initState() {
    super.initState();
    _initializeControllers();
  }

  Future<void> _initializeControllers() async {
    final controller =
        VideoPlayerController.networkUrl(Uri.parse(widget.videoUrl));
    _videoPlayerController = controller;

    try {
      await controller.initialize();

      if (controller != _videoPlayerController) {
        controller.dispose();
        return;
      }

      _aspectRatio = widget.aspectRatio ?? controller.value.aspectRatio;
      _chewieController = _createChewieController(autoPlay: widget.autoPlay ?? true);
      _initializationError = false;
      _initializationErrorMessage = null;

      if (mounted) setState(() => _isInitialized = true);
    } catch (e) {
      if (controller != _videoPlayerController) {
        controller.dispose();
        return;
      }
      _aspectRatio = widget.aspectRatio ?? 16 / 9;
      if (mounted) {
        setState(() {
          _initializationError = true;
          _initializationErrorMessage = e.toString();
        });
      }
    }
  }

  @override
  void didUpdateWidget(covariant InternalVideoPlayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.videoUrl != widget.videoUrl) {
      _disposeControllers();
      _chewieController = null;
      _isInitialized = false;
      _initializationError = false;
      _initializationErrorMessage = null;
      _initializeControllers();
    } else if (oldWidget.aspectRatio != widget.aspectRatio) {
      _aspectRatio = widget.aspectRatio ?? _videoPlayerController.value.aspectRatio;
      final wasPlaying = _chewieController?.isPlaying ?? false;
      _chewieController?.dispose();
      _chewieController = _createChewieController(autoPlay: widget.autoPlay ?? wasPlaying);
      setState(() {});
    }
  }

  ChewieController _createChewieController({required bool autoPlay}) {
    return ChewieController(
      videoPlayerController: _videoPlayerController,
      allowMuting: true,
      errorBuilder: _buildErrorWidget,
      showControls: widget.showControls ?? true,
      aspectRatio: _aspectRatio,
      allowPlaybackSpeedChanging: true,
      playbackSpeeds: [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2],
      autoPlay: autoPlay,
      looping: widget.looping ?? false,
      hideControlsTimer: const Duration(seconds: 1),
      autoInitialize: false,
    );
  }

  Widget _buildErrorWidget(BuildContext context, String error) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.info, color: Colors.red,
              size: MediaQuery.of(context).size.height * 0.1),
          Text(error, textAlign: TextAlign.center),
        ],
      ),
    );
  }

  void _disposeControllers() {
    _chewieController?.dispose();
    _videoPlayerController.dispose();
  }

  @override
  void dispose() {
    _disposeControllers();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_initializationError) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, color: Colors.red, size: 48),
            const SizedBox(height: 8),
            Text('Video initialization failed',
                style: Theme.of(context).textTheme.titleMedium),
            if (_initializationErrorMessage != null)
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: Text(_initializationErrorMessage!,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall),
              ),
          ],
        ),
      );
    }

    if (!_isInitialized || _chewieController == null) {
      return const Center(child: CircularProgressIndicator());
    }

    if (!(ModalRoute.of(context)?.isCurrent ?? true)) {
      if (_chewieController?.isPlaying ?? false) _chewieController?.pause();
    } else {
      if ((widget.autoPlay ?? true) && !(_chewieController?.isPlaying ?? true)) {
        _chewieController?.play();
      }
    }

    return AspectRatio(
      aspectRatio: _aspectRatio,
      child: Chewie(controller: _chewieController!),
    );
  }
}
