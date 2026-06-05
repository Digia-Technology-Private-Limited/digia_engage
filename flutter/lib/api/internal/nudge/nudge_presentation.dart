import 'package:flutter/material.dart';

import 'nudge_config.dart';

/// Strategy (GoF): knows how to put a nudge on screen for one display type.
///
/// Bottom sheet and dialog are not just different frame widgets — they are
/// different Flutter entry points (`showModalBottomSheet` vs `showDialog`) with
/// different dismissal semantics. Modelling each as a strategy keeps that
/// branching out of the caller and makes a new display type (pip, fullscreen…)
/// an open/closed addition: a new strategy registered in the selector.
abstract interface class NudgePresentation {
  Future<void> present({
    required BuildContext context,
    required NudgeSurface surface,
    required Widget content,
  });
}

/// Bottom-sheet strategy: top-rounded surface, optional drag handle, safe-area aware.
class BottomSheetPresentation implements NudgePresentation {
  const BottomSheetPresentation();

  @override
  Future<void> present({
    required BuildContext context,
    required NudgeSurface surface,
    required Widget content,
  }) {
    return showModalBottomSheet<void>(
      context: context,
      useRootNavigator: true,
      isScrollControlled: true,
      isDismissible: surface.backdropDismissible,
      enableDrag: surface.draggable,
      backgroundColor: Colors.transparent,
      builder: (_) => _SheetFrame(surface: surface, child: content),
    );
  }
}

/// Dialog strategy: centred, width-constrained, fully rounded surface.
class DialogPresentation implements NudgePresentation {
  const DialogPresentation();

  @override
  Future<void> present({
    required BuildContext context,
    required NudgeSurface surface,
    required Widget content,
  }) {
    return showDialog<void>(
      context: context,
      useRootNavigator: true,
      barrierDismissible: surface.backdropDismissible,
      builder: (_) => _DialogFrame(surface: surface, child: content),
    );
  }
}

// ─── frames (composition) ──────────────────────────────────────────────────────

class _SheetFrame extends StatelessWidget {
  final NudgeSurface surface;
  final Widget child;

  const _SheetFrame({required this.surface, required this.child});

  @override
  Widget build(BuildContext context) {
    final radius = Radius.circular(surface.cornerRadius);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: Container(
        decoration: BoxDecoration(
          color: surface.backgroundColor ?? Colors.white,
          borderRadius: BorderRadius.vertical(top: radius),
        ),
        clipBehavior: Clip.antiAlias,
        child: SafeArea(
          top: false,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (surface.showHandle) const _DragHandle(),
              Flexible(child: _NudgeBody(surface: surface, child: child)),
            ],
          ),
        ),
      ),
    );
  }
}

class _DialogFrame extends StatelessWidget {
  final NudgeSurface surface;
  final Widget child;

  const _DialogFrame({required this.surface, required this.child});

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width * surface.widthFraction;
    return Dialog(
      backgroundColor: surface.backgroundColor ?? Colors.white,
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(surface.cornerRadius),
      ),
      clipBehavior: Clip.antiAlias,
      child: SizedBox(
        width: width,
        child: _NudgeBody(surface: surface, child: child),
      ),
    );
  }
}

/// Padded, scrollable content area shared by both frames, with the optional
/// close affordance overlaid top-right.
class _NudgeBody extends StatelessWidget {
  final NudgeSurface surface;
  final Widget child;

  const _NudgeBody({required this.surface, required this.child});

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        SingleChildScrollView(
          child:
              Padding(padding: EdgeInsets.all(surface.padding), child: child),
        ),
        if (surface.showCloseButton)
          Positioned(
            top: 12,
            right: 12,
            child: _CloseButton(onTap: () => Navigator.of(context).maybePop()),
          ),
      ],
    );
  }
}

class _DragHandle extends StatelessWidget {
  const _DragHandle();

  @override
  Widget build(BuildContext context) => Container(
        width: 40,
        height: 4,
        margin: const EdgeInsets.only(top: 10, bottom: 2),
        decoration: BoxDecoration(
          color: const Color(0xFFD0D0D8),
          borderRadius: BorderRadius.circular(2),
        ),
      );
}

class _CloseButton extends StatelessWidget {
  final VoidCallback onTap;

  const _CloseButton({required this.onTap});

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          height: 26,
          width: 26,
          decoration: const BoxDecoration(
            color: Color(0x14000000),
            shape: BoxShape.circle,
          ),
          child: const Icon(Icons.close, size: 16, color: Color(0xFF66667A)),
        ),
      );
}
