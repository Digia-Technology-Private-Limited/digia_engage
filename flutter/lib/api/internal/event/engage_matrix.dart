import '../action/engage_action.dart';

/// Pure builders that assemble the engage-event-matrix `properties` map for each
/// rendered campaign type, using the exact matrix snake_case keys. Kept as
/// free functions (no widget/service dependencies) so the error-prone mapping
/// logic is unit-tested in isolation; renderers call these at the
/// `EngageEventEmitter.analytics(...)` site.
///
/// Optional keys are omitted entirely when their source is null — the analytics
/// service strips nulls anyway, but omitting keeps the on-wire payload tight and
/// makes the builders' intent explicit.

/// Maps an [EngageAction] to the matrix `action_type` token
/// (`url` | `deeplink` | `dismiss` | `custom`).
String actionTypeToken(EngageAction action) => switch (action) {
      OpenUrlAction() => 'url',
      OpenDeeplinkAction() => 'deeplink',
      HideAction() => 'dismiss',
      ShareAction() => 'custom',
    };

/// The matrix `action_url` for an action, or null for actions without a target
/// (dismiss/share). Only [LinkAction]s carry a navigable target.
String? actionUrlOf(EngageAction action) =>
    action is LinkAction ? action.target : null;

/// Properties for a nudge `Digia Experience Viewed`. [displayStyle] is required
/// (`bottom_sheet` | `dialog`); trigger/screen context is included only when known.
Map<String, dynamic> nudgeViewedProperties({
  required String displayStyle,
  String? screenName,
  String? triggerType,
  String? triggerEvent,
}) =>
    {
      'display_style': displayStyle,
      if (screenName != null) 'screen_name': screenName,
      if (triggerType != null) 'trigger_type': triggerType,
      if (triggerEvent != null) 'trigger_event': triggerEvent,
    };

/// Properties for a nudge `Digia Experience Clicked`. The Flutter nudge model
/// has no per-button id, so [element_id] is synthesised from the button's role
/// (`cta_primary` / `cta_secondary`) — a stable, deterministic id that mirrors
/// `cta_position`. `action_type`/`action_url` come from the button's first
/// action (the navigational intent of the tap).
Map<String, dynamic> nudgeClickedProperties({
  required String label,
  required bool isPrimary,
  required List<EngageAction> actions,
  int? timeToActionMs,
}) {
  final position = isPrimary ? 'primary' : 'secondary';
  final action = actions.isEmpty ? null : actions.first;
  final url = action == null ? null : actionUrlOf(action);
  return {
    'element_id': 'cta_$position',
    'cta_label': label,
    'cta_position': position,
    if (action != null) 'action_type': actionTypeToken(action),
    if (url != null) 'action_url': url,
    if (timeToActionMs != null) 'time_to_action_ms': timeToActionMs,
  };
}

/// Properties for an inline container `Digia Experience Viewed` (carousel rail
/// or story rail first render). [displayStyle] is `carousel` | `story`;
/// [itemTotal] is the slide/story count. Screen/trigger context is included only
/// when known.
Map<String, dynamic> inlineViewedProperties({
  required String displayStyle,
  required int itemTotal,
  String? screenName,
  String? triggerType,
  String? triggerEvent,
}) =>
    {
      'display_style': displayStyle,
      'item_total': itemTotal,
      if (screenName != null) 'screen_name': screenName,
      if (triggerType != null) 'trigger_type': triggerType,
      if (triggerEvent != null) 'trigger_event': triggerEvent,
    };

/// Properties shared by inline `Digia Step Viewed` / `Step Clicked` /
/// `Step Dismissed` (and story frames): the 0-based [itemIndex] within
/// [itemTotal], the container [displayStyle], plus the optional server [itemId]
/// and (for clicks) the resolved [actionType]/[actionUrl]. Optional fields are
/// omitted when their source is null (e.g. Flutter carousel slides have no id).
Map<String, dynamic> inlineStepProperties({
  required String displayStyle,
  required int itemIndex,
  required int itemTotal,
  String? itemId,
  String? actionType,
  String? actionUrl,
}) =>
    {
      'display_style': displayStyle,
      'item_index': itemIndex,
      'item_total': itemTotal,
      if (itemId != null) 'item_id': itemId,
      if (actionType != null) 'action_type': actionType,
      if (actionUrl != null) 'action_url': actionUrl,
    };

/// Properties for an inline `Digia Experience Completed` (a story played through
/// all frames). [timeToCompleteMs] — total view-through time — stays in the
/// properties JSON (the matrix does not hoist it) and is included only when known.
Map<String, dynamic> inlineCompletedProperties({
  required String displayStyle,
  required int itemTotal,
  int? timeToCompleteMs,
}) =>
    {
      'display_style': displayStyle,
      'item_total': itemTotal,
      if (timeToCompleteMs != null) 'time_to_complete_ms': timeToCompleteMs,
    };

/// Properties for a nudge `Digia Experience Dismissed`. Both fields are optional
/// because the dismiss source (backdrop / close_button / back_button /
/// secondary_cta / auto_timeout) and view-time are only known when the
/// presentation layer surfaces them.
Map<String, dynamic> nudgeDismissedProperties({
  String? dismissReason,
  int? timeToDismissMs,
}) =>
    {
      if (dismissReason != null) 'dismiss_reason': dismissReason,
      if (timeToDismissMs != null) 'time_to_dismiss_ms': timeToDismissMs,
    };
