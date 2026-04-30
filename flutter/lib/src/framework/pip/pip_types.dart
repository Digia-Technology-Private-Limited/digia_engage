import 'package:flutter/material.dart';

// MARK: - Event constants

class PipEvent {
  static const String shown = 'pip_shown';
  static const String videoStarted = 'pip_video_started';
  static const String play = 'pip_play_clicked';
  static const String pause = 'pip_pause_clicked';
  static const String mute = 'pip_mute_clicked';
  static const String unmute = 'pip_unmute_clicked';
  static const String expand = 'pip_expand_clicked';
  static const String collapse = 'pip_collapse_clicked';
  static const String close = 'pip_close_clicked';
  static const String dismissed = 'pip_dismissed';
}

// MARK: - Position preset

enum PipPosition {
  topLeft,
  topRight,
  bottomLeft,
  bottomRight,
  center;

  Offset resolvedOrigin({
    required Size pipSize,
    required Size screenSize,
  }) {
    final pw = pipSize.width;
    final ph = pipSize.height;
    final sw = screenSize.width;
    final sh = screenSize.height;
    switch (this) {
      case PipPosition.topLeft:
        return Offset(sw * 0.02, sh * 0.05);
      case PipPosition.topRight:
        return Offset(sw - pw - sw * 0.02, sh * 0.05);
      case PipPosition.bottomLeft:
        return Offset(sw * 0.02, sh - ph - sh * 0.08);
      case PipPosition.bottomRight:
        return Offset(sw - pw - sw * 0.02, sh - ph - sh * 0.08);
      case PipPosition.center:
        return Offset((sw - pw) / 2, (sh - ph) / 2);
    }
  }

  static PipPosition? fromString(String? s) {
    switch (s?.trim().toLowerCase()) {
      case 'tl':
        return PipPosition.topLeft;
      case 'tr':
        return PipPosition.topRight;
      case 'bl':
        return PipPosition.bottomLeft;
      case 'br':
        return PipPosition.bottomRight;
      case 'c':
      case 'center':
        return PipPosition.center;
      default:
        return null;
    }
  }
}

// MARK: - Screen filter

class PipScreenFilter {
  final bool isWhitelist;
  final Set<String> screenNames;

  const PipScreenFilter({required this.isWhitelist, required this.screenNames});

  bool isAllowed(String screen) =>
      isWhitelist ? (screenNames.isEmpty || screenNames.contains(screen)) : !screenNames.contains(screen);
}

// MARK: - Drag bounds

class PipDragBounds {
  final double minXFraction;
  final double maxXFraction;
  final double minYFraction;
  final double maxYFraction;

  const PipDragBounds({
    this.minXFraction = 0,
    this.maxXFraction = 1,
    this.minYFraction = 0,
    this.maxYFraction = 1,
  });
}

// MARK: - PipRequest

class PipRequest {
  final String componentId;
  final Map<String, dynamic>? args;
  final String? videoUrl;

  final PipPosition? position;
  final double startX;
  final double startY;

  final double widthDp;
  final double heightDp;
  final double cornerRadius;
  final Color backgroundColor;

  final bool showClose;
  final bool expandable;
  final bool autoPlay;
  final bool looping;
  final bool muted;

  final int delayMs;
  final int autoDismissMs;

  final PipScreenFilter? screenFilter;
  final bool closeOnScreenChange;

  final PipDragBounds? dragBounds;
  final int animationDurationMs;

  final void Function(String event, Map<String, dynamic> props)? onEvent;
  final void Function(dynamic result)? onDismiss;

  const PipRequest({
    this.componentId = '',
    this.args,
    this.videoUrl,
    this.position,
    this.startX = 0.7,
    this.startY = 0.1,
    this.widthDp = 200,
    this.heightDp = 120,
    this.cornerRadius = 12,
    this.backgroundColor = Colors.black,
    this.showClose = true,
    this.expandable = true,
    this.autoPlay = true,
    this.looping = false,
    this.muted = false,
    this.delayMs = 0,
    this.autoDismissMs = 0,
    this.screenFilter,
    this.closeOnScreenChange = false,
    this.dragBounds,
    this.animationDurationMs = 300,
    this.onEvent,
    this.onDismiss,
  });
}
