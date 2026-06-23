/// How a host-app surfaced a campaign. Serialized as lower_snake_case.
///
/// Mirrors Android's `TriggerType`.
enum TriggerType {
  event('event'),
  screenView('screen_view'),
  manual('manual'),
  appOpen('app_open');

  final String wire;

  const TriggerType(this.wire);
}

/// Trigger attribution shared by every campaign's "Viewed" event.
///
/// Mirrors Android's `TriggerContext`.
class TriggerContext {
  final TriggerType type;
  final String? event;

  const TriggerContext(this.type, {this.event});

  Map<String, Object?> asProperties() =>
      _nonNull({'trigger_type': type.wire, 'trigger_event': event});
}

/// A first-party (Digia analytics) event, modelled 1:1 on the Engage event
/// matrix.
///
/// Events are grouped by campaign type — [NudgeEvent], [GuideEvent],
/// [SurveyEvent], [CarouselEvent], [StoriesEvent] — so each subtype exposes
/// *exactly* the fields its matrix row defines (ISP): a nudge event cannot
/// carry `has_welcome`, a guide step cannot omit its `item_index`. Each leaf
/// owns its wire [eventName] and how its typed fields serialize into
/// [properties]. Field→wire-key mapping (e.g. step index → `item_index`) lives
/// in the leaf, the single source of truth (SRP).
///
/// These are Digia-only. CEP forwarding uses the coarse `DigiaExperienceEvent`
/// on a separate channel — the two concerns are deliberately not unified.
sealed class EngageAnalyticsEvent {
  const EngageAnalyticsEvent();

  String get eventName;

  Map<String, Object?> get properties => const {};
}

// ── Nudge (bottom_sheet / dialog; distinguished by displayStyle) ─────────────

sealed class NudgeEvent extends EngageAnalyticsEvent {
  const NudgeEvent();
}

class NudgeViewed extends NudgeEvent {
  final String displayStyle;
  final TriggerContext? trigger;
  final String? screenName;

  const NudgeViewed({
    required this.displayStyle,
    this.trigger,
    this.screenName,
  });

  @override
  String get eventName => 'Digia Experience Viewed';

  @override
  Map<String, Object?> get properties => {
        ..._nonNull({'screen_name': screenName, 'display_style': displayStyle}),
        ...?trigger?.asProperties(),
      };
}

class NudgeClicked extends NudgeEvent {
  final String? elementId;
  final String? ctaLabel;
  final String? actionType;
  final String? actionUrl;
  final String? ctaRole;
  final int? timeToActionMs;

  const NudgeClicked({
    this.elementId,
    this.ctaLabel,
    this.actionType,
    this.actionUrl,
    this.ctaRole,
    this.timeToActionMs,
  });

  @override
  String get eventName => 'Digia Experience Clicked';

  @override
  Map<String, Object?> get properties => _nonNull({
        'cta_role': ctaRole,
        'time_to_action_ms': timeToActionMs,
        'element_id': elementId,
        'cta_label': ctaLabel,
        'action_type': actionType,
        'action_url': actionUrl,
      });
}

class NudgeDismissed extends NudgeEvent {
  final int? dwellMs;

  const NudgeDismissed({this.dwellMs});

  @override
  String get eventName => 'Digia Experience Dismissed';

  @override
  Map<String, Object?> get properties => _nonNull({'dwell_ms': dwellMs});
}

// ── Guide (tooltip / spotlight; distinguished by displayStyle) ───────────────

sealed class GuideEvent extends EngageAnalyticsEvent {
  const GuideEvent();
}

class GuideViewed extends GuideEvent {
  final String displayStyle;
  final int itemTotal;
  final TriggerContext? trigger;
  final String? screenName;

  const GuideViewed({
    required this.displayStyle,
    required this.itemTotal,
    this.trigger,
    this.screenName,
  });

  @override
  String get eventName => 'Digia Experience Viewed';

  @override
  Map<String, Object?> get properties => {
        ..._nonNull({
          'screen_name': screenName,
          'display_style': displayStyle,
          'item_total': itemTotal,
        }),
        ...?trigger?.asProperties(),
      };
}

class GuideStepViewed extends GuideEvent {
  final int itemIndex;
  final int itemTotal;
  final String? anchorKey;
  final String? displayStyle;

  const GuideStepViewed({
    required this.itemIndex,
    required this.itemTotal,
    this.anchorKey,
    this.displayStyle,
  });

  @override
  String get eventName => 'Digia Step Viewed';

  @override
  Map<String, Object?> get properties => _nonNull({
        'item_index': itemIndex,
        'item_total': itemTotal,
        'anchor_key': anchorKey,
        'display_style': displayStyle,
      });
}

class GuideStepClicked extends GuideEvent {
  final int itemIndex;
  final String? elementId;
  final String? ctaLabel;
  final String? actionType;
  final String? actionUrl;

  const GuideStepClicked({
    required this.itemIndex,
    this.elementId,
    this.ctaLabel,
    this.actionType,
    this.actionUrl,
  });

  @override
  String get eventName => 'Digia Step Clicked';

  @override
  Map<String, Object?> get properties => _nonNull({
        'item_index': itemIndex,
        'element_id': elementId,
        'cta_label': ctaLabel,
        'action_type': actionType,
        'action_url': actionUrl,
      });
}

class GuideStepDismissed extends GuideEvent {
  final int itemIndex;

  const GuideStepDismissed({required this.itemIndex});

  @override
  String get eventName => 'Digia Step Dismissed';

  @override
  Map<String, Object?> get properties => _nonNull({'item_index': itemIndex});
}

/// Guide abandoned (rolls up step-level dismiss).
class GuideDismissed extends GuideEvent {
  final int? abandonedAtItem;
  final int? itemTotal;
  final int? dwellMs;

  const GuideDismissed({this.abandonedAtItem, this.itemTotal, this.dwellMs});

  @override
  String get eventName => 'Digia Experience Dismissed';

  @override
  Map<String, Object?> get properties => _nonNull({
        'abandoned_at_item': abandonedAtItem,
        'item_total': itemTotal,
        'dwell_ms': dwellMs,
      });
}

class GuideCompleted extends GuideEvent {
  final int? itemTotal;
  final int? timeToCompleteMs;

  const GuideCompleted({this.itemTotal, this.timeToCompleteMs});

  @override
  String get eventName => 'Digia Experience Completed';

  @override
  Map<String, Object?> get properties => _nonNull({
        'time_to_complete_ms': timeToCompleteMs,
        'item_total': itemTotal,
      });
}

// ── Survey ──────────────────────────────────────────────────────────────────

sealed class SurveyEvent extends EngageAnalyticsEvent {
  const SurveyEvent();
}

class SurveyViewed extends SurveyEvent {
  final int? itemTotal;
  final bool? hasWelcome;
  final bool? hasThanks;
  final bool? hasBranching;
  final TriggerContext? trigger;
  final String? screenName;

  const SurveyViewed({
    this.itemTotal,
    this.hasWelcome,
    this.hasThanks,
    this.hasBranching,
    this.trigger,
    this.screenName,
  });

  @override
  String get eventName => 'Digia Experience Viewed';

  @override
  Map<String, Object?> get properties => {
        ..._nonNull({
          'has_welcome': hasWelcome,
          'has_thanks': hasThanks,
          'has_branching': hasBranching,
          'screen_name': screenName,
          'item_total': itemTotal,
        }),
        ...?trigger?.asProperties(),
      };
}

/// Start tapped on the welcome screen, or first-answer engagement.
class SurveyClicked extends SurveyEvent {
  final String? elementId;

  const SurveyClicked({this.elementId});

  @override
  String get eventName => 'Digia Experience Clicked';

  @override
  Map<String, Object?> get properties => _nonNull({'element_id': elementId});
}

class SurveyQuestionViewed extends SurveyEvent {
  final String questionId;
  final String? questionType;
  final int? itemIndex;
  final int? itemTotal;
  final String? blockType;
  final String? blockId;
  final bool? isRequired;
  final String? questionTitle;

  const SurveyQuestionViewed({
    required this.questionId,
    this.questionType,
    this.itemIndex,
    this.itemTotal,
    this.blockType,
    this.blockId,
    this.isRequired,
    this.questionTitle,
  });

  @override
  String get eventName => 'Digia Question Viewed';

  @override
  Map<String, Object?> get properties => _nonNull({
        'block_type': blockType,
        'block_id': blockId,
        'is_required': isRequired,
        'question_title': questionTitle,
        'question_id': questionId,
        'question_type': questionType,
        'item_index': itemIndex,
        'item_total': itemTotal,
      });
}

class SurveyQuestionAnswered extends SurveyEvent {
  final String questionId;
  final String? questionType;
  final String? questionTitle;
  final String? answerValue;
  final String? answerText;
  final String? blockType;
  final String? blockId;
  final String? answerLabel;
  final List<String>? answerOptions;
  final int? scaleMin;
  final int? scaleMax;
  final int? timeToAnswerMs;

  /// Raw answer envelope (`values`, `comment`) — preserved as-is.
  final Map<String, Object?> answer;

  const SurveyQuestionAnswered({
    required this.questionId,
    this.questionType,
    this.questionTitle,
    this.answerValue,
    this.answerText,
    this.blockType,
    this.blockId,
    this.answerLabel,
    this.answerOptions,
    this.scaleMin,
    this.scaleMax,
    this.timeToAnswerMs,
    this.answer = const {},
  });

  @override
  String get eventName => 'Digia Question Answered';

  @override
  Map<String, Object?> get properties => _nonNull({
        'question_id': questionId,
        'question_type': questionType,
        'question_title': questionTitle,
        'answer_value': answerValue,
        'answer_text': answerText,
        'block_type': blockType,
        'block_id': blockId,
        'answer_label': answerLabel,
        'answer_options': answerOptions,
        'scale_min': scaleMin,
        'scale_max': scaleMax,
        'time_to_answer_ms': timeToAnswerMs,
        'answer': answer.isNotEmpty ? answer : null,
      });
}

class SurveyQuestionSkipped extends SurveyEvent {
  final String questionId;
  final int? itemIndex;
  final String? blockType;
  final String? blockId;
  final String? questionTitle;

  const SurveyQuestionSkipped({
    required this.questionId,
    this.itemIndex,
    this.blockType,
    this.blockId,
    this.questionTitle,
  });

  @override
  String get eventName => 'Digia Question Skipped';

  @override
  Map<String, Object?> get properties => _nonNull({
        'block_type': blockType,
        'block_id': blockId,
        'question_id': questionId,
        'item_index': itemIndex,
        'question_title': questionTitle,
      });
}

class SurveyDismissed extends SurveyEvent {
  final int? abandonedAtItem;
  final int? itemTotal;
  final int? answeredCount;
  final int? dwellMs;

  const SurveyDismissed({
    this.abandonedAtItem,
    this.itemTotal,
    this.answeredCount,
    this.dwellMs,
  });

  @override
  String get eventName => 'Digia Experience Dismissed';

  @override
  Map<String, Object?> get properties => _nonNull({
        'answered_count': answeredCount,
        'dwell_ms': dwellMs,
        'abandoned_at_item': abandonedAtItem,
        'item_total': itemTotal,
      });
}

class SurveyCompleted extends SurveyEvent {
  final int? itemTotal;
  final int? answeredCount;
  final String? submissionId;
  final int? timeToCompleteMs;

  /// Submitted answers, keyed by node id (each value is the answer envelope).
  final Map<String, Object?> response;

  const SurveyCompleted({
    this.itemTotal,
    this.answeredCount,
    this.submissionId,
    this.timeToCompleteMs,
    this.response = const {},
  });

  @override
  String get eventName => 'Digia Experience Completed';

  @override
  Map<String, Object?> get properties => _nonNull({
        'item_total': itemTotal,
        'answered_count': answeredCount,
        'submission_id': submissionId,
        'time_to_complete_ms': timeToCompleteMs,
        'response': response.isNotEmpty ? response : null,
      });
}

// ── Inline: carousel ────────────────────────────────────────────────────────

sealed class CarouselEvent extends EngageAnalyticsEvent {
  const CarouselEvent();
}

class CarouselViewed extends CarouselEvent {
  final int? itemTotal;
  final String? slotKey;
  final String? screenName;

  const CarouselViewed({this.itemTotal, this.slotKey, this.screenName});

  @override
  String get eventName => 'Digia Experience Viewed';

  @override
  Map<String, Object?> get properties => _nonNull({
        'screen_name': screenName,
        'display_style': 'carousel',
        'item_total': itemTotal,
        'slot_key': slotKey,
      });
}

class CarouselStepViewed extends CarouselEvent {
  final int itemIndex;
  final int? itemTotal;
  final String? itemId;

  /// True when the carousel auto-advanced to this item; false on a manual swipe.
  final bool? auto;

  const CarouselStepViewed({
    required this.itemIndex,
    this.itemTotal,
    this.itemId,
    this.auto,
  });

  @override
  String get eventName => 'Digia Step Viewed';

  @override
  Map<String, Object?> get properties => _nonNull({
        'item_id': itemId,
        'auto': auto,
        'item_index': itemIndex,
        'item_total': itemTotal,
      });
}

class CarouselStepClicked extends CarouselEvent {
  final int itemIndex;
  final String? elementId;
  final String? ctaLabel;
  final String? actionType;
  final String? actionUrl;
  final String? itemId;

  const CarouselStepClicked({
    required this.itemIndex,
    this.elementId,
    this.ctaLabel,
    this.actionType,
    this.actionUrl,
    this.itemId,
  });

  @override
  String get eventName => 'Digia Step Clicked';

  @override
  Map<String, Object?> get properties => _nonNull({
        'item_id': itemId,
        'item_index': itemIndex,
        'element_id': elementId,
        'cta_label': ctaLabel,
        'action_type': actionType,
        'action_url': actionUrl,
      });
}

/// Carousel container tapped (non-item region).
class CarouselClicked extends CarouselEvent {
  final String? elementId;
  final String? ctaLabel;
  final String? actionType;
  final String? actionUrl;

  const CarouselClicked({
    this.elementId,
    this.ctaLabel,
    this.actionType,
    this.actionUrl,
  });

  @override
  String get eventName => 'Digia Experience Clicked';

  @override
  Map<String, Object?> get properties => _nonNull({
        'element_id': elementId,
        'cta_label': ctaLabel,
        'action_type': actionType,
        'action_url': actionUrl,
      });
}

// ── Inline: stories ─────────────────────────────────────────────────────────

sealed class StoriesEvent extends EngageAnalyticsEvent {
  const StoriesEvent();
}

class StoriesViewed extends StoriesEvent {
  final int? itemTotal;
  final String? slotKey;
  final String? screenName;

  const StoriesViewed({this.itemTotal, this.slotKey, this.screenName});

  @override
  String get eventName => 'Digia Experience Viewed';

  @override
  Map<String, Object?> get properties => _nonNull({
        'screen_name': screenName,
        'display_style': 'stories',
        'item_total': itemTotal,
        'slot_key': slotKey,
      });
}

/// A story is opened (ring/thumbnail tapped).
class StoriesOpened extends StoriesEvent {
  final String? storyId;

  const StoriesOpened({this.storyId});

  @override
  String get eventName => 'Digia Experience Clicked';

  @override
  Map<String, Object?> get properties =>
      _nonNull({'story_id': storyId, 'element_id': 'story_open'});
}

class StoriesStepViewed extends StoriesEvent {
  final int itemIndex;
  final int? itemTotal;
  final String? storyId;
  final String? frameId;

  const StoriesStepViewed({
    required this.itemIndex,
    this.itemTotal,
    this.storyId,
    this.frameId,
  });

  @override
  String get eventName => 'Digia Step Viewed';

  @override
  Map<String, Object?> get properties => _nonNull({
        'story_id': storyId,
        'frame_id': frameId,
        'item_index': itemIndex,
        'item_total': itemTotal,
      });
}

class StoriesStepClicked extends StoriesEvent {
  final int itemIndex;
  final String? ctaLabel;
  final String? actionType;
  final String? actionUrl;
  final String? frameId;

  const StoriesStepClicked({
    required this.itemIndex,
    this.ctaLabel,
    this.actionType,
    this.actionUrl,
    this.frameId,
  });

  @override
  String get eventName => 'Digia Step Clicked';

  @override
  Map<String, Object?> get properties => _nonNull({
        'frame_id': frameId,
        'item_index': itemIndex,
        'cta_label': ctaLabel,
        'action_type': actionType,
        'action_url': actionUrl,
      });
}

class StoriesStepDismissed extends StoriesEvent {
  final int itemIndex;

  const StoriesStepDismissed({required this.itemIndex});

  @override
  String get eventName => 'Digia Step Dismissed';

  @override
  Map<String, Object?> get properties => _nonNull({'item_index': itemIndex});
}

class StoriesCompleted extends StoriesEvent {
  final int? itemTotal;
  final int? timeToCompleteMs;

  const StoriesCompleted({this.itemTotal, this.timeToCompleteMs});

  @override
  String get eventName => 'Digia Experience Completed';

  @override
  Map<String, Object?> get properties => _nonNull({
        'time_to_complete_ms': timeToCompleteMs,
        'item_total': itemTotal,
      });
}

/// Builds a wire map from named fields, dropping any whose value is null.
///
/// Mirrors Android's `nonNull(...)` helper.
Map<String, Object?> _nonNull(Map<String, Object?> pairs) {
  final result = <String, Object?>{};
  pairs.forEach((key, value) {
    if (value != null) result[key] = value;
  });
  return result;
}
