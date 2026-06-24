import 'package:digia_engage/api/internal/variable_scope.dart';
import 'package:digia_engage/api/models/variable_schema.dart';
import 'package:flutter_test/flutter_test.dart';

// Helpers to reduce boilerplate in arithmetic tests.
VariableScope _numScope(Map<String, dynamic> cepVars,
    {Map<String, String>? fallbacks}) {
  final schemas = cepVars.keys
      .map((k) => VariableSchema(
          name: k, type: 'number', fallbackValue: fallbacks?[k] ?? ''))
      .toList();
  return VariableScope.fromSchemas(schemas, cepVars);
}

String _eval(String expr, VariableScope scope) => scope.resolve('{{ $expr }}');

void main() {
  group('VariableScope.fromSchemas — resolution chain', () {
    test('CEP string value wins over fallback', () {
      final schema = normalizeVariable(
          {'name': 'city', 'type': 'string', 'fallbackValue': 'DefaultCity'})!;
      final scope = VariableScope.fromSchemas([schema], {'city': 'Mumbai'});
      expect(scope.variables['city'], 'Mumbai');
    });

    test('empty CEP string falls through to fallback', () {
      final schema = normalizeVariable(
          {'name': 'city', 'type': 'string', 'fallbackValue': 'DefaultCity'})!;
      final scope = VariableScope.fromSchemas([schema], {'city': ''});
      expect(scope.variables['city'], 'DefaultCity');
    });

    test('absent CEP entry falls through to fallback', () {
      final schema = normalizeVariable(
          {'name': 'city', 'type': 'string', 'fallbackValue': 'DefaultCity'})!;
      final scope = VariableScope.fromSchemas([schema], {});
      expect(scope.variables['city'], 'DefaultCity');
    });

    test('CEP JSON number used directly for number-type', () {
      final schema = normalizeVariable(
          {'name': 'price', 'type': 'number', 'fallbackValue': '0'})!;
      final scope = VariableScope.fromSchemas([schema], {'price': 42});
      expect(scope.variables['price'], '42');
    });

    test('CEP numeric string parsed for number-type', () {
      final schema = normalizeVariable(
          {'name': 'price', 'type': 'number', 'fallbackValue': '0'})!;
      final scope = VariableScope.fromSchemas([schema], {'price': '99'});
      expect(scope.variables['price'], '99');
    });

    test('CEP non-numeric string for number-type falls to fallback', () {
      final schema = normalizeVariable(
          {'name': 'score', 'type': 'number', 'fallbackValue': '10'})!;
      final scope = VariableScope.fromSchemas([schema], {'score': 'bad'});
      expect(scope.variables['score'], '10');
    });

    test('non-numeric fallback for number-type yields empty string', () {
      final schema = normalizeVariable(
          {'name': 'score', 'type': 'number', 'fallbackValue': 'n/a'})!;
      final scope = VariableScope.fromSchemas([schema], {'score': 'bad'});
      expect(scope.variables['score'], '');
    });

    test('absent fallbackValue uses sampleValue (backward compat)', () {
      final schema = normalizeVariable({'name': 'score', 'sampleValue': '42'})!;
      expect(schema.fallbackValue, '42');
    });

    test('absent type defaults to string', () {
      final schema = normalizeVariable({'name': 'label'})!;
      expect(schema.type, 'string');
    });
  });

  group('VariableScope.resolve — plain substitution', () {
    test('no {{ }} → returned unchanged', () {
      expect(VariableScope.empty.resolve('hello world'), 'hello world');
    });

    test('plain identifier lookup', () {
      const scope = VariableScope({'name': 'Alex'}, {'name': 'string'});
      expect(scope.resolve('Hi {{ name }}!'), 'Hi Alex!');
    });

    test('missing identifier → empty string', () {
      expect(VariableScope.empty.resolve('{{ missing }}'), '');
    });

    test('plain string var from CEP', () {
      final scope = VariableScope.fromSchemas(
        [VariableSchema(name: 'name', type: 'string', fallbackValue: '')],
        {'name': 'Alex'},
      );
      expect(scope.resolve('{{ name }}'), 'Alex');
    });

    test('empty CEP value falls through to fallback', () {
      final scope = VariableScope.fromSchemas(
        [VariableSchema(name: 'name', type: 'string', fallbackValue: 'Anon')],
        {'name': ''},
      );
      expect(scope.resolve('{{ name }}'), 'Anon');
    });

    test('no CEP value and no fallback → empty string', () {
      final scope = VariableScope.fromSchemas(
        [VariableSchema(name: 'name', type: 'string', fallbackValue: '')],
        {},
      );
      expect(scope.resolve('{{ name }}'), '');
    });
  });

  group('arithmetic expressions', () {
    test('BODMAS: a + b*c = a + (b*c)', () {
      final scope = _numScope({'a': '2', 'b': '3', 'c': '4'});
      expect(_eval('a + b*c', scope), '14');
    });

    test('BODMAS: a*b + c = (a*b) + c', () {
      final scope = _numScope({'a': '2', 'b': '3', 'c': '4'});
      expect(_eval('a*b + c', scope), '10');
    });

    test('decimal mul rounds to 4 dp, strips trailing zeros', () {
      final scope = _numScope({'price': '99'});
      expect(_eval('price * 1.18', scope), '116.82');
    });

    test('10/3 rounds to 4 dp', () {
      expect(_eval('10 / 3', _numScope({})), '3.3333');
    });

    test('exact division strips trailing zeros', () {
      expect(_eval('4 / 2', _numScope({})), '2');
    });

    test('0.1 + 0.2 rounds to 0.3', () {
      expect(_eval('0.1 + 0.2', _numScope({})), '0.3');
    });

    test('unary minus on variable', () {
      final scope = _numScope({'a': '5', 'b': '8'});
      expect(_eval('-a + b', scope), '3');
    });

    test('unary minus on literal', () {
      final scope = _numScope({'a': '5'});
      expect(_eval('a * -2', scope), '-10');
    });

    test('division by zero → empty string', () {
      final scope = _numScope({'a': '5', 'b': '0'});
      expect(_eval('a / b', scope), '');
    });

    test('non-numeric CEP value in arithmetic → empty string', () {
      // 'a' resolves to '' (non-numeric CEP, no fallback) → arithmetic fails.
      final scope = _numScope({'a': 'x', 'b': '2'});
      expect(_eval('a + b', scope), '');
    });

    test('variable missing from schema → empty string', () {
      final scope = _numScope({'a': '2'}); // 'b' not in schema
      expect(_eval('a + b', scope), '');
    });
  });

  group('VariableScope arithmetic via resolve', () {
    test('resolves plain identifier', () {
      const scope = VariableScope({'price': '100'}, {'price': 'number'});
      expect(scope.resolve('Price: {{ price }}'), 'Price: 100');
    });

    test('resolves arithmetic expression', () {
      const scope = VariableScope({'price': '99'}, {'price': 'number'});
      expect(scope.resolve('{{ price * 1.18 }}'), '116.82');
    });
  });
}
