/// Authoring metadata for one `{{ name }}` placeholder declared in the
/// Digia dashboard's `templateConfig.variables` list.
///
/// [type] determines how the value is handled at render time:
///   - `"string"` — substituted as-is.
///   - `"number"` — numeric coercion is attempted; participates in
///     arithmetic expressions inside `{{ }}`.
///
/// [fallbackValue] is the dashboard-declared default. Absent
/// `fallbackValue` falls back to `sampleValue` for backward-compatibility.
class VariableSchema {
  final String name;
  final String type; // 'string' | 'number'
  final String fallbackValue;

  const VariableSchema({
    required this.name,
    required this.type,
    required this.fallbackValue,
  });
}

/// Parses one raw entry from `templateConfig.variables`.
/// Returns `null` when the entry lacks a valid name.
VariableSchema? normalizeVariable(Map<String, dynamic> entry) {
  final name = entry['name'] as String?;
  if (name == null || name.isEmpty) return null;
  final type = entry['type'] == 'number' ? 'number' : 'string';
  final fallback =
      (entry['fallbackValue'] as String?) ?? (entry['sampleValue'] as String?) ?? '';
  return VariableSchema(name: name, type: type, fallbackValue: fallback);
}
