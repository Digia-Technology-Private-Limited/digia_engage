import 'package:flutter/foundation.dart';

import '../models/cep_trigger_payload.dart';
import '../models/digia_experience_event.dart';
import 'campaign/inline_carousel_config.dart';

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
  final Map<String, CEPTriggerPayload> _slotPayloads = <String, CEPTriggerPayload>{};

  /// Inline carousel configs keyed by their `slotKey` (the [DigiaSlot]
  /// placement key). Populated when a campaign-store-routed inline campaign is
  /// triggered.
  final Map<String, InlineCarouselConfig> _slotConfigs =
      <String, InlineCarouselConfig>{};

  /// `slotKey -> campaignId`, so invalidation can clear a slot config by id.
  final Map<String, String> _slotConfigCampaignIds = <String, String>{};

  /// The currently active campaign payload, or null when no experience is shown.
  CEPTriggerPayload? get activePayload => _activePayload;
  Map<String, CEPTriggerPayload> get slotPayloads => Map.unmodifiable(_slotPayloads);
  Map<String, InlineCarouselConfig> get slotConfigs =>
      Map.unmodifiable(_slotConfigs);

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

  void addSlot(String placementKey, CEPTriggerPayload payload) {
    _slotPayloads[placementKey] = payload;
    notifyListeners();
  }

  CEPTriggerPayload? getSlot(String placementKey) => _slotPayloads[placementKey];

  void removeSlotById(String campaignId) {
    _slotPayloads.removeWhere(
        (_, payload) => payload.cepCampaignId == campaignId);
    notifyListeners();
  }

  void clearSlots() {
    _slotPayloads.clear();
    notifyListeners();
  }

  // ─── Inline carousel slot configs (campaign-store routed) ──────────────────

  /// Stores [config] for its slot, optionally tagging it with [campaignId] so
  /// it can later be cleared via [removeSlotConfigByCampaignId].
  void addSlotConfig(InlineCarouselConfig config, {String? campaignId}) {
    _slotConfigs[config.slotKey] = config;
    if (campaignId != null) _slotConfigCampaignIds[config.slotKey] = campaignId;
    notifyListeners();
  }

  InlineCarouselConfig? getSlotConfig(String placementKey) =>
      _slotConfigs[placementKey];

  void removeSlotConfigByCampaignId(String campaignId) {
    final keys = _slotConfigCampaignIds.entries
        .where((e) => e.value == campaignId)
        .map((e) => e.key)
        .toList();
    if (keys.isEmpty) return;
    for (final key in keys) {
      _slotConfigs.remove(key);
      _slotConfigCampaignIds.remove(key);
    }
    notifyListeners();
  }

  void clearSlotConfigs() {
    _slotConfigs.clear();
    _slotConfigCampaignIds.clear();
    notifyListeners();
  }

  /// Set by [DigiaInstance] at init time.
  /// [DigiaHost] calls this when a user interaction event occurs.
  /// [DigiaInstance] handles the event and forwards it to the active plugin.
  void Function(DigiaExperienceEvent, CEPTriggerPayload)? onEvent;
}
