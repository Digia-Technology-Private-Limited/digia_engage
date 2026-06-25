/// Evaluates a BODMAS arithmetic expression with variable substitution.
///
/// Identifiers in [expr] resolve from [values]; only variables whose [types]
/// entry equals `"number"` may be used as operands. The grammar is `+ - * /`
/// with chained unary minus and no parentheses (matching the dashboard). Returns
/// `null` on any error — unknown or non-numeric variable, division by zero, or
/// malformed syntax.
String? evaluateArithmetic(
  String expr,
  Map<String, String> values,
  Map<String, String> types,
) {
  // Resolve eagerly: every operand becomes a `double`, every operator a
  // single-char `String`. `null` means a bad character or an unusable variable.
  final tokens = _tokenize(expr, values, types);
  if (tokens == null) return null;

  try {
    final parser = _Parser(tokens);
    final result = parser.expression();
    if (!parser.atEnd) return null; // leftover tokens, e.g. "3 4"
    return _formatResult(result);
  } on _EvalError {
    return null;
  }
}

/// Recursive-descent parser over a flat list of `double` operands and operator
/// `String`s. Two precedence levels (`+ -` below `* /`), left-associative, with
/// unary minus folded into the operand.
class _Parser {
  _Parser(this._tokens);

  final List<Object> _tokens;
  int _pos = 0;

  bool get atEnd => _pos >= _tokens.length;
  Object? get _peek => atEnd ? null : _tokens[_pos];

  double expression() {
    var value = _term();
    while (_peek == '+' || _peek == '-') {
      final op = _tokens[_pos++];
      final rhs = _term();
      value = op == '+' ? value + rhs : value - rhs;
    }
    return value;
  }

  double _term() {
    var value = _factor();
    while (_peek == '*' || _peek == '/') {
      final op = _tokens[_pos++];
      final rhs = _factor();
      if (op == '/' && rhs == 0) throw const _EvalError();
      value = op == '*' ? value * rhs : value / rhs;
    }
    return value;
  }

  double _factor() {
    var negate = false;
    while (_peek == '-') {
      negate = !negate;
      _pos++;
    }
    // A unary '+', '*', '/' or a missing operand is not a `double` → error.
    final operand = _peek;
    if (operand is! double) throw const _EvalError();
    _pos++;
    return negate ? -operand : operand;
  }
}

class _EvalError implements Exception {
  const _EvalError();
}

/// Splits [expr] into `double` operands and operator strings, or returns `null`
/// on an unexpected character or an operand that can't resolve to a number.
List<Object>? _tokenize(
  String expr,
  Map<String, String> values,
  Map<String, String> types,
) {
  final tokens = <Object>[];
  var i = 0;

  while (i < expr.length) {
    final char = expr[i];

    if (char == ' ' || char == '\t') {
      i++;
      continue;
    }

    if (char == '+' || char == '-' || char == '*' || char == '/') {
      tokens.add(char);
      i++;
      continue;
    }

    // Number literal (incl. leading-dot, e.g. `.5`).
    if (_isDigit(char) || char == '.') {
      final start = i;
      while (i < expr.length && (_isDigit(expr[i]) || expr[i] == '.')) {
        i++;
      }
      final number = double.tryParse(expr.substring(start, i));
      if (number == null) return null; // e.g. a bare "."
      tokens.add(number);
      continue;
    }

    // Identifier → resolve against a number-typed variable.
    if (_isLowercase(char)) {
      final start = i;
      while (i < expr.length && _isIdentChar(expr[i])) {
        i++;
      }
      final name = expr.substring(start, i);
      if (types[name] != 'number') return null;
      final value = double.tryParse(values[name] ?? '');
      if (value == null) return null;
      tokens.add(value);
      continue;
    }

    return null; // unexpected character
  }

  return tokens;
}

bool _isDigit(String char) {
  final code = char.codeUnitAt(0);
  return code >= 48 && code <= 57;
}

bool _isLowercase(String char) {
  final code = char.codeUnitAt(0);
  return code >= 97 && code <= 122;
}

bool _isIdentChar(String char) =>
    _isLowercase(char) || _isDigit(char) || char == '_';

String? _formatResult(double value) {
  if (!value.isFinite) return null;
  final rounded = (value * 10000).roundToDouble() / 10000;
  var result = rounded.toStringAsFixed(4);
  if (result.contains('.')) {
    result = result.replaceAll(RegExp(r'0+$'), '');
    result = result.replaceAll(RegExp(r'\.$'), '');
  }
  return result;
}
