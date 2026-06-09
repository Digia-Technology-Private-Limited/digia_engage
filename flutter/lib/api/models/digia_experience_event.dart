/// Lifecycle events emitted by DigiaHost when a rendered experience
/// transitions state. Digia passes these to the plugin via notifyEvent().
sealed class DigiaExperienceEvent {
  const DigiaExperienceEvent();

  /// The canonical analytics event name for this lifecycle event.
  ///
  /// This mapping is intentionally outside the analytics core so that the
  /// transport layer remains agnostic to campaign semantics.
  String get analyticsEventName;

  /// Whether the SDK should flush analytics immediately after this event.
  bool get flushOnCapture => false;
}

/// The experience became visible to the user.
/// Map to: CleverTap syncTemplate (viewed), MoEngage trackImpression
class ExperienceImpressed extends DigiaExperienceEvent {
  const ExperienceImpressed();

  @override
  String get analyticsEventName => 'Digia Experience Viewed';
}

/// The user interacted with an actionable element.
/// Map to: CleverTap syncTemplate (clicked), MoEngage trackClick
class ExperienceClicked extends DigiaExperienceEvent {
  /// Identifier of the element clicked, if defined in the campaign artifact.
  /// Null if the entire experience surface was the tap target.
  final String? elementId;

  const ExperienceClicked({this.elementId});

  @override
  String get analyticsEventName => 'Digia Experience Clicked';
}

/// The experience was dismissed — by the user or programmatically.
/// Map to: CleverTap dismissTemplate, MoEngage trackDismissed
class ExperienceDismissed extends DigiaExperienceEvent {
  const ExperienceDismissed();

  @override
  String get analyticsEventName => 'Digia Experience Dismissed';

  @override
  bool get flushOnCapture => true;
}

/// The user completed the experience.
/// Map to: completion analytics semantics.
class ExperienceCompleted extends DigiaExperienceEvent {
  const ExperienceCompleted();

  @override
  String get analyticsEventName => 'Digia Experience Completed';

  @override
  bool get flushOnCapture => true;
}
