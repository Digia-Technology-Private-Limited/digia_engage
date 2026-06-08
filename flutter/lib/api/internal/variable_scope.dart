import 'package:flutter/widgets.dart';

/// Runtime variable substitution shared by every Digia surface that renders
/// trigger copy — nudges, inline stories, inline carousels, and anything added
/// later. This is the single source of truth for the dashboard's variable
/// syntax (matching Android's `interpolate`).
///
/// Use it two ways:
/// - [interpolateVariables] — a plain function, when you already hold the
///   variables map (e.g. a widget that receives them as a prop).
/// - [VariableScope] + [VariableScopeProvider] — when the variables must reach
///   a deep, type-erased render tree (e.g. the nudge renderers) without
///   threading the map through every call.

/// Replaces `{{ name }}` placeholders in [text] using [variables]. An unmatched
/// placeholder collapses to an empty string; a null/empty [variables] map or a
/// string with no `{{` returns [text] unchanged.
String interpolateVariables(String text, Map<String, String>? variables) {
  if (variables == null || variables.isEmpty || !text.contains('{{')) {
    return text;
  }
  return text.replaceAllMapped(
    _placeholderPattern,
    (m) => variables[m.group(1)] ?? '',
  );
}

final _placeholderPattern = RegExp(r'\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}');

/// An immutable set of runtime variables a surface is rendered against, with a
/// [resolve] convenience for interpolating a single string.
class VariableScope {
  final Map<String, String> variables;

  const VariableScope(this.variables);

  /// No variables in scope — every placeholder collapses to empty.
  static const empty = VariableScope(<String, String>{});

  /// Replaces `{{ name }}` placeholders in [text]. See [interpolateVariables].
  String resolve(String text) => interpolateVariables(text, variables);
}

/// Carries the active [VariableScope] down the widget tree so descendants can
/// interpolate copy at draw time without receiving the variables as a prop.
/// Mirrors `EngageActionContextScope`.
class VariableScopeProvider extends InheritedWidget {
  final VariableScope scope;

  const VariableScopeProvider({
    required this.scope,
    required super.child,
    super.key,
  });

  /// Read (without subscribing) the scope in effect, or [VariableScope.empty]
  /// when no provider is above [context].
  static VariableScope of(BuildContext context) =>
      context.getInheritedWidgetOfExactType<VariableScopeProvider>()?.scope ??
      VariableScope.empty;

  @override
  bool updateShouldNotify(VariableScopeProvider oldWidget) =>
      !identical(scope, oldWidget.scope);
}
