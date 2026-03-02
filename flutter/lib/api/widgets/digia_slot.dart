import 'package:flutter/widgets.dart';

import '../../src/framework/ui_factory.dart';
import '../internal/digia_instance.dart';
import '../models/digia_experience_event.dart';
import '../models/in_app_payload.dart';

/// Renders inline campaign content (banners, cards, widgets) at a specific
/// placement position in scrolling content or screen layouts.
///
/// The [placementKey] is the link between developer code and the Digia
/// dashboard — the marketer selects the same key when creating inline content.
///
/// **Lifecycle:**
/// - Shows content as soon as a matching campaign is stored for [placementKey].
/// - Fires an impression event the first time each unique payload renders.
/// - Collapses to [SizedBox.shrink] when no campaign is active.
/// - The campaign **persists** in the controller when the page is disposed so
///   it reappears on the next visit. Only the server (via
///   [onExperienceInvalidated]) or an explicit user dismiss clears it.
///
/// ```dart
/// // Self-sizing (recommended)
/// DigiaSlot('hero_banner')
///
/// // With explicit height
/// SizedBox(
///   height: 200,
///   child: DigiaSlot('hero_banner'),
/// )
/// ```
///
/// Marketing name: "Inline Content" → [DigiaSlot]
class DigiaSlot extends StatefulWidget {
  /// The placement key that identifies this slot in the Digia dashboard.
  ///
  /// Must match the key the marketer selects when creating inline content.
  /// Convention: snake_case — e.g. "home_hero_banner", "pdp_mid_banner".
  final String placementKey;

  /// Optional widget to display when no campaign content is active for this
  /// slot. Defaults to [SizedBox.shrink] when null.
  final Widget? placeholder;

  const DigiaSlot(this.placementKey, {super.key, this.placeholder});

  @override
  State<DigiaSlot> createState() => _DigiaSlotState();
}

class _DigiaSlotState extends State<DigiaSlot> {
  /// ID of the last payload we already fired an impression for.
  /// Prevents re-firing on every setState rebuild.
  String? _impressedPayloadId;

  /// ID of the payload currently being displayed.
  /// Used to skip setState when the controller fires for a different slot.
  String? _currentPayloadId;

  @override
  void initState() {
    super.initState();
    DigiaInstance.instance.inlineController.addListener(_onInlineChanged);

    // A campaign might already be in the controller when this slot mounts
    // (e.g., the campaign arrived before the page was opened).
    _scheduleImpressionIfNeeded();
  }

  @override
  void dispose() {
    DigiaInstance.instance.inlineController.removeListener(_onInlineChanged);
    // Do NOT remove the campaign on dispose. Inline campaigns are "sticky" —
    // they persist for when the user returns to this page. Only server
    // invalidation or an explicit user dismiss should clear them.
    super.dispose();
  }

  // ─── Controller listener ──────────────────────────────────────────────────

  void _onInlineChanged() {
    final scheduled = _scheduleImpressionIfNeeded();
    if (scheduled) setState(() {});
  }

  // ─── Impression tracking ──────────────────────────────────────────────────

  /// Fires [ExperienceImpressed] the first time a unique payload renders in
  /// this slot. Safe to call multiple times — no-ops if already impressed.
  ///
  /// Returns `true` if the payload changed (triggering a rebuild is needed).
  bool _scheduleImpressionIfNeeded() {
    final payload = DigiaInstance.instance.inlineController
        .getCampaign(widget.placementKey);

    final newId = payload?.id;
    if (newId == _currentPayloadId) return false;

    _currentPayloadId = newId;

    if (payload == null || payload.id == _impressedPayloadId) return true;

    _impressedPayloadId = payload.id;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      DigiaInstance.instance.inlineController.onEvent
          ?.call(const ExperienceImpressed(), payload);
    });
    return true;
  }

  // ─── Dismiss ──────────────────────────────────────────────────────────────

  /// Called when the user explicitly dismisses the inline content
  /// (e.g., a close button inside the campaign component).
  ///
  /// Fires [ExperienceDismissed] and removes the campaign from the controller,
  /// collapsing this slot to nothing until a new campaign arrives.
  void _dismiss(InAppPayload payload) {
    DigiaInstance.instance.inlineController.onEvent
        ?.call(const ExperienceDismissed(), payload);
    DigiaInstance.instance.inlineController
        .dismissCampaign(widget.placementKey);
  }

  @override
  Widget build(BuildContext context) {
    final payload = DigiaInstance.instance.inlineController
        .getCampaign(widget.placementKey);
    return _DigiaSlotContent(
      payload: payload,
      onDismiss: _dismiss,
      placeholder: widget.placeholder,
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────

/// Internal widget that renders the active inline campaign content for a
/// given [payload], or collapses to nothing when no content is active.
class _DigiaSlotContent extends StatelessWidget {
  /// The campaign payload resolved by the parent [_DigiaSlotState].
  final InAppPayload? payload;

  /// Called when the campaign component requests a dismiss
  /// (e.g., user taps a close CTA inside the rendered component).
  final ValueChanged<InAppPayload> onDismiss;

  /// Widget to show when no campaign is active. Defaults to [SizedBox.shrink].
  final Widget? placeholder;

  const _DigiaSlotContent({
    required this.payload,
    required this.onDismiss,
    this.placeholder,
  });

  @override
  Widget build(BuildContext context) {
    if (payload == null) return placeholder ?? const SizedBox.shrink();

    final content = payload!.content;
    final viewId = content['viewId'] as String? ??
        content['componentId'] as String? ??
        content['pageId'] as String?;

    if (viewId == null || viewId.isEmpty) {
      return placeholder ?? const SizedBox.shrink();
    }

    return DUIFactory().createComponent(viewId, content);
  }
}
