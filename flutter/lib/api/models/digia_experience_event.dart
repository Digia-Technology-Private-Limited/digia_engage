/// Lifecycle events emitted by DigiaHost when a rendered experience
/// transitions state. Digia passes these to the plugin via notifyEvent().
sealed class DigiaExperienceEvent {
  const DigiaExperienceEvent();
}

/// The experience became visible to the user.
/// Map to: CleverTap syncTemplate (viewed), MoEngage trackImpression
class ExperienceImpressed extends DigiaExperienceEvent {
  const ExperienceImpressed();
}

/// The user interacted with an actionable element.
/// Map to: CleverTap syncTemplate (clicked), MoEngage trackClick
class ExperienceClicked extends DigiaExperienceEvent {
  /// Identifier of the element clicked, if defined in the campaign artifact.
  /// Null if the entire experience surface was the tap target.
  final String? elementId;

  const ExperienceClicked({this.elementId});
}

/// The experience was dismissed — by the user or programmatically.
/// Map to: CleverTap dismissTemplate, MoEngage trackDismissed
class ExperienceDismissed extends DigiaExperienceEvent {
  const ExperienceDismissed();
}
