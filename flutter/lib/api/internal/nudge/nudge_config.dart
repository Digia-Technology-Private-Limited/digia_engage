import 'dart:ui' show Color;

import 'nudge_content.dart';

/// How a nudge surface presents over the host app.
enum NudgeDisplayType { bottomSheet, dialog }

/// The presentation chrome for a nudge — everything *around* the content tree.
///
/// A pure value object; decoding from the `container` wire object lives in
/// `nudge_parser.dart`. The content layout (spacing / alignment) lives on the
/// [NudgeColumn], so this only describes how the modal frame looks and behaves.
class NudgeSurface {
  final NudgeDisplayType displayType;

  /// Surface background; null inherits white at render time.
  final Color? backgroundColor;
  final double cornerRadius;

  /// Uniform inner padding around the content tree, in logical pixels.
  final double padding;

  /// Dismiss when the scrim/barrier outside the surface is tapped.
  final bool backdropDismissible;

  /// Render an "×" close affordance on the surface.
  final bool showCloseButton;

  /// Show the drag-handle pill at the top of the sheet (bottom sheet only).
  final bool showHandle;

  /// Allow dragging the sheet down to dismiss (bottom sheet only).
  final bool draggable;

  /// Dialog width as a fraction of the screen width, 0…1 (dialog only).
  final double widthFraction;

  const NudgeSurface({
    required this.displayType,
    required this.backgroundColor,
    required this.cornerRadius,
    required this.padding,
    required this.backdropDismissible,
    required this.showCloseButton,
    required this.showHandle,
    required this.draggable,
    required this.widthFraction,
  });

  bool get isBottomSheet => displayType == NudgeDisplayType.bottomSheet;
}

/// A fully parsed nudge: the presentation [surface] plus the typed content
/// tree ([content]) the renderer draws inside it.
class NudgeConfig {
  final NudgeSurface surface;
  final NudgeColumn content;

  const NudgeConfig({required this.surface, required this.content});
}
