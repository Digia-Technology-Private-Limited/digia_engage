import 'package:flutter/material.dart';

import '../internal/digia_instance.dart';
import '../internal/digia_overlay_controller.dart';
import '../models/digia_experience_event.dart';
import '../models/in_app_payload.dart';

/// Wraps the application root and renders in-app message overlays
/// (dialogs, bottom sheets, PIPs, fullscreen) above all app content.
///
/// Place this widget once, at the root of your application. The recommended
/// placement is in [MaterialApp.builder]:
///
/// ```dart
/// MaterialApp(
///   navigatorObservers: [DigiaNavigatorObserver()],
///   builder: (context, child) => DigiaHost(child: child!),
/// )
/// ```
///
/// Placing [DigiaHost] multiple times or below the navigation root
/// produces undefined behavior — the SDK logs a warning.
///
/// Marketing name: "In-App Messages" → [DigiaHost]
class DigiaHost extends StatefulWidget {
  /// The application widget tree to render below the overlay layer.
  final Widget child;

  const DigiaHost({required this.child, super.key});

  @override
  State<DigiaHost> createState() => _DigiaHostState();
}

class _DigiaHostState extends State<DigiaHost> {
  /// Reference to the shared controller. [DigiaInstance] owns it;
  /// [DigiaHost] only listens.
  late final DigiaOverlayController _controller;

  @override
  void initState() {
    super.initState();
    _controller = DigiaInstance.instance.controller;
    _controller.addListener(_onControllerChanged);

    // Notify SDK that DigiaHost is now mounted.
    DigiaInstance.instance.onHostMounted();
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    DigiaInstance.instance.onHostUnmounted();
    super.dispose();
  }

  void _onControllerChanged() {
    // Rebuild whenever activePayload changes.
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // App content sits below — always present.
        widget.child,

        // Experience overlay renders above when a payload is active.
        if (_controller.activePayload != null)
          _DigiaOverlayWidget(
            payload: _controller.activePayload!,
            onEvent: (event) {
              // Route event back to DigiaInstance → plugin.
              _controller.onEvent?.call(event, _controller.activePayload!);

              // Dismiss overlay on click or explicit dismiss.
              if (event is ExperienceClicked || event is ExperienceDismissed) {
                _controller.dismiss();
              }
            },
          ),
      ],
    );
  }
}

/// Internal widget that renders the campaign artifact and emits lifecycle
/// events back to [DigiaHost] via [onEvent].
class _DigiaOverlayWidget extends StatefulWidget {
  final InAppPayload payload;
  final void Function(DigiaExperienceEvent) onEvent;

  const _DigiaOverlayWidget({
    required this.payload,
    required this.onEvent,
  });

  @override
  State<_DigiaOverlayWidget> createState() => _DigiaOverlayWidgetState();
}

class _DigiaOverlayWidgetState extends State<_DigiaOverlayWidget> {
  @override
  void initState() {
    super.initState();
    // Emit ExperienceImpressed on first frame — after layout is complete.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        widget.onEvent(const ExperienceImpressed());
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      // Tapping the scrim (outside the card) dismisses the experience.
      onTap: () => widget.onEvent(const ExperienceDismissed()),
      child: ColoredBox(
        color: Colors.black54,
        child: Center(
          child: _buildContent(),
        ),
      ),
    );
  }

  Widget _buildContent() {
    // In production, this delegates to Digia's server-driven UI rendering
    // engine, which reads payload.content and constructs the correct widget
    // tree from the campaign artifact.
    //
    // Shown here with a representative static layout for clarity.
    return GestureDetector(
      // Consume taps inside the card — do not propagate to scrim.
      onTap: () {},
      child: Card(
        margin: const EdgeInsets.all(32),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                widget.payload.content['title'] as String? ?? '',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 16),
              // Primary CTA — emits ExperienceClicked with elementId
              ElevatedButton(
                onPressed: () => widget.onEvent(
                  const ExperienceClicked(elementId: 'primary_cta'),
                ),
                child: Text(
                  widget.payload.content['cta'] as String? ?? 'OK',
                ),
              ),
              // Dismiss — emits ExperienceDismissed
              TextButton(
                onPressed: () => widget.onEvent(const ExperienceDismissed()),
                child: const Text('Dismiss'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
