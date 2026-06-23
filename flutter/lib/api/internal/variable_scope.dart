import 'package:flutter/widgets.dart';

import '../models/variable_schema.dart';
import 'expr_evaluator.dart';

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
///   threading the map through every call. [VariableScope] also adds `{{ a + b }}`
///   arithmetic over number-typed variables.

// ─── Identifier grammar ───────────────────────────────────────────────────────

final _identifierPattern = RegExp(r'^[a-z][a-z0-9_]*$');
final _placeholderPattern = RegExp(r'\{\{([\s\S]*?)\}\}');
final _identifierPlaceholderPattern =
    RegExp(r'\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}');
final _wholeTokenPattern =
    RegExp(r'^\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}$');

// ─── Basic interpolation ──────────────────────────────────────────────────────

/// Replaces `{{ name }}` placeholders in [text] using [variables]. A matched
/// value is stringified via `toString()`; an unmatched placeholder collapses to
/// an empty string; a null/empty [variables] map or a string with no `{{`
/// returns [text] unchanged.
String interpolateVariables(String text, Map<String, dynamic>? variables) {
  if (variables == null || variables.isEmpty || !text.contains('{{')) {
    return text;
  }
  return text.replaceAllMapped(
    _identifierPlaceholderPattern,
    (m) => variables[m.group(1)]?.toString() ?? '',
  );
}

/// Resolves [text] to a value that **keeps its type**.
///
/// When [text] is a single whole `{{ name }}` token (ignoring surrounding
/// whitespace), the matched variable is returned as-is — an `int`, `bool`,
/// `List`, `Map`, etc. survive instead of being stringified. This is for
/// non-string fields fed by a variable (a numeric size, a boolean flag).
Object? resolveValue(String text, Map<String, dynamic>? variables) {
  final whole = _wholeTokenPattern.firstMatch(text.trim());
  if (whole != null && variables != null) {
    final key = whole.group(1)!;
    if (variables.containsKey(key)) {
      return variables[key];
    }
  }
  return interpolateVariables(text, variables);
}

// ─── VariableScope ────────────────────────────────────────────────────────────

/// An immutable set of runtime variables a surface is rendered against, with
/// convenience methods for interpolating a single string.
class VariableScope {
  final Map<String, dynamic> variables;

  /// Type map for arithmetic: `"number"` entries participate in arithmetic
  /// expressions; everything else is plain-substitution only.
  final Map<String, String> types;

  const VariableScope(this.variables, [this.types = const {}]);

  /// No variables in scope — every placeholder collapses to empty.
  static const empty = VariableScope(<String, dynamic>{});

  /// Builds a scope by resolving each declared [schemas] entry against the
  /// runtime CEP [cepVars] payload. The winning value follows the chain
  /// CEP payload → `fallbackValue` → `""`; number-typed variables are coerced
  /// numerically so they can take part in `{{ a + b }}` arithmetic.
  factory VariableScope.fromSchemas(
    List<VariableSchema> schemas,
    Map<String, dynamic>? cepVars,
  ) {
    final values = <String, dynamic>{};
    final types = <String, String>{};
    for (final schema in schemas) {
      types[schema.name] = schema.type;
      values[schema.name] = schema.type == 'number'
          ? _resolveNumber(cepVars?[schema.name], schema.fallbackValue)
          : _resolveString(cepVars?[schema.name], schema.fallbackValue);
    }
    return VariableScope(values, types);
  }

  /// Replaces every `{{ … }}` token in [text]. A plain `{{ identifier }}` is a
  /// direct lookup; anything else is evaluated as an arithmetic expression over
  /// the scope's number-typed variables (collapsing to `""` on any error).
  String resolve(String text) {
    if (!text.contains('{{')) return text;
    final values = {
      for (final e in variables.entries) e.key: e.value?.toString() ?? ''
    };
    return text.replaceAllMapped(_placeholderPattern, (m) {
      final inner = m.group(1)!.trim();
      if (_identifierPattern.hasMatch(inner)) return values[inner] ?? '';
      return evaluateArithmetic(inner, values, types) ?? '';
    });
  }

  /// Resolves [text] to a type-preserving value. See [resolveValue].
  Object? resolveTyped(String text) => resolveValue(text, variables);
}

String _resolveString(dynamic raw, String fallback) {
  final value = raw?.toString() ?? '';
  return value.isEmpty ? fallback : value;
}

String _resolveNumber(dynamic raw, String fallback) {
  if (raw is num) return _formatNumber(raw.toDouble());
  if (raw != null) {
    final parsed = double.tryParse(raw.toString());
    if (parsed != null) return _formatNumber(parsed);
  }
  final fallbackParsed = double.tryParse(fallback);
  return fallbackParsed != null ? _formatNumber(fallbackParsed) : '';
}

String _formatNumber(double value) {
  if (!value.isFinite) return '';
  final rounded = (value * 10000).roundToDouble() / 10000;
  var result = rounded.toStringAsFixed(4);
  if (result.contains('.')) {
    result = result.replaceAll(RegExp(r'0+$'), '');
    result = result.replaceAll(RegExp(r'\.$'), '');
  }
  return result;
}

// ─── VariableScopeProvider ────────────────────────────────────────────────────

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
