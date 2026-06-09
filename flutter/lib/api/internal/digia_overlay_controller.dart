import 'dart:async';

import 'package:flutter/foundation.dart';

import '../models/cep_trigger_payload.dart';
import '../models/digia_experience_event.dart';
import 'campaign/campaign_model.dart';

/// Internal controller that coordinates between [DigiaInstance] (imperative)
/// and [DigiaHost] (declarative widget tree).
///
/// Mirrors Flutter's own controller pattern (ScrollController,
/// AnimationController, TextEditingController) — a plain Dart object that
/// bridges the SDK world and the widget tree.
///
/// Never exposed to app developers.
class DigiaOverlayController extends ChangeNotifier {
  CEPTriggerPayload? _activePayload;

  /// Per-slot trigger context, keyed by `slotKey`. Holds the [CEPTriggerPayload]
  /// — its `cepCampaignId` (used to invalidate the slot) and `variables` (used
  /// for story CTA copy). It does **not** hold what to render.
  final Map<String, CEPTriggerPayload> _slotPayloads =
      <String, CEPTriggerPayload>{};

  /// Per-slot resolved render config, keyed by `slotKey`. The carousel/story
  /// config is looked up from the campaign store at trigger time — it is **not**
  /// part of the payload, so it has to be kept here. One map covers both inline
  /// kinds (a slot key only ever holds one); [DigiaSlot] switches on the type.
  final Map<String, CampaignConfigModel> _slotConfigs =
      <String, CampaignConfigModel>{};

  /// The currently active campaign payload, or null when no experience is shown.
  CEPTriggerPayload? get activePayload => _activePayload;

  /// Called by [DigiaInstance] when a new modal experience is ready to render.
  /// Notifies [DigiaHost] to present the overlay.
  void show(CEPTriggerPayload payload) {
    _activePayload = payload;
    notifyListeners();
  }

  /// Called by [DigiaInstance] on invalidation, or by [DigiaHost] when
  /// the user dismisses the experience.
  void dismiss() {
    _activePayload = null;
    notifyListeners();
  }

  // ─── Inline slots (carousel / story — campaign-store routed) ───────────────

  /// Stores the resolved [config] and its trigger [payload] for [slotKey]. The
  /// payload's `cepCampaignId` is what [removeInlineSlotByCampaignId] matches on,
  /// so no separate id map is needed.
  void addInlineSlot(
    String slotKey,
    CampaignConfigModel config,
    CEPTriggerPayload payload,
  ) {
    _slotConfigs[slotKey] = config;
    _slotPayloads[slotKey] = payload;
    notifyListeners();
  }

  /// The render config for [slotKey], or null when no inline campaign is active.
  CampaignConfigModel? getInlineConfig(String slotKey) => _slotConfigs[slotKey];

  /// The trigger payload for [slotKey] (story CTA variables read this).
  CEPTriggerPayload? getSlot(String slotKey) => _slotPayloads[slotKey];

  /// Clears every inline slot whose payload was triggered by [cepCampaignId].
  void removeInlineSlotByCampaignId(String cepCampaignId) {
    final keys = _slotPayloads.entries
        .where((e) => e.value.cepCampaignId == cepCampaignId)
        .map((e) => e.key)
        .toList();
    if (keys.isEmpty) return;
    for (final key in keys) {
      _slotConfigs.remove(key);
      _slotPayloads.remove(key);
    }
    notifyListeners();
  }

  void clearInlineSlots() {
    _slotConfigs.clear();
    _slotPayloads.clear();
    notifyListeners();
  }

  /// Set by [DigiaInstance] at init time.
  /// [DigiaHost] calls this when a user interaction event occurs.
  /// [DigiaInstance] handles the event and forwards it to the active plugin.
  void Function(DigiaExperienceEvent, CEPTriggerPayload)? onEvent;
}
