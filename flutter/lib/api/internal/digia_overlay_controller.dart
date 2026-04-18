import 'package:flutter/foundation.dart';

import '../models/digia_experience_event.dart';
import '../models/in_app_payload.dart';

/// Internal controller that coordinates between [DigiaInstance] (imperative)
/// and [DigiaHost] (declarative widget tree).
///
/// Mirrors Flutter's own controller pattern (ScrollController,
/// AnimationController, TextEditingController) — a plain Dart object that
/// bridges the SDK world and the widget tree.
///
/// Never exposed to app developers.
class DigiaOverlayController extends ChangeNotifier {
  InAppPayload? _activePayload;
  final Map<String, InAppPayload> _slotPayloads = <String, InAppPayload>{};

  /// The currently active campaign payload, or null when no experience is shown.
  InAppPayload? get activePayload => _activePayload;
  Map<String, InAppPayload> get slotPayloads => Map.unmodifiable(_slotPayloads);

  /// Called by [DigiaInstance] when a new experience is ready to render.
  /// Notifies [DigiaHost] to rebuild and display the overlay.
  void show(InAppPayload payload) {
    _activePayload = payload;
    notifyListeners();
  }

  /// Called by [DigiaInstance] on invalidation, or by [DigiaHost] when
  /// the user dismisses the experience.
  /// Notifies [DigiaHost] to rebuild and remove the overlay.
  void dismiss() {
    _activePayload = null;
    notifyListeners();
  }

  void addSlot(String placementKey, InAppPayload payload) {
    _slotPayloads[placementKey] = payload;
    notifyListeners();
  }

  InAppPayload? getSlot(String placementKey) => _slotPayloads[placementKey];

  void removeSlotById(String campaignId) {
    _slotPayloads.removeWhere((_, payload) => payload.id == campaignId);
    notifyListeners();
  }

  void clearSlots() {
    _slotPayloads.clear();
    notifyListeners();
  }

  /// Set by [DigiaInstance] at init time.
  /// [DigiaHost] calls this when a user interaction event occurs.
  /// [DigiaInstance] handles the event and forwards it to the active plugin.
  void Function(DigiaExperienceEvent, InAppPayload)? onEvent;
}
