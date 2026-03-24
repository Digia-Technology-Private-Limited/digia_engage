import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:digia_engage/src/framework/base/virtual_stateless_widget.dart';
import 'package:digia_engage/src/framework/base/virtual_widget.dart';
import 'package:digia_engage/src/framework/expr/default_scope_context.dart';
import 'package:digia_engage/src/framework/expr/expression_util.dart';
import 'package:digia_engage/src/framework/expr/scope_context.dart';
import 'package:digia_engage/src/framework/models/vw_data.dart';
import 'package:digia_engage/src/framework/virtual_widget_registry.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final repoRoot = Directory.current.parent.path;
  final catalogPath = '$repoRoot/parity/widget_parity_catalog.json';
  final casesPath = '$repoRoot/parity/widget_parity_cases.json';
  final flutterRegistryPath =
      '$repoRoot/flutter/lib/src/framework/virtual_widget_registry.dart';
  final iosWidgetDecodePath =
      '$repoRoot/ios/Sources/DigiaEngage/api/internal/framework/models/vw_data.swift';

  test('widget parity catalog matches source registries', () {
    final catalog = ParityCatalog.load(catalogPath);
    final flutterTypes = deriveFlutterRegistryTypes(flutterRegistryPath);
    final iosSupportedTypes = deriveIosSupportedTypes(iosWidgetDecodePath);

    expect(catalog.iosSupportedWidgetTypes, orderedEquals(iosSupportedTypes));
    expect(
      catalog.flutterOnlyWidgetTypes,
      orderedEquals(
          flutterTypes.difference(iosSupportedTypes).toList()..sort()),
    );
  });

  test('widget parity cases cover every iOS-supported widget type', () {
    final catalog = ParityCatalog.load(catalogPath);
    final cases = ParityCase.loadAll(casesPath);
    final coveredTypes = cases.map((testCase) => testCase.type).toSet();

    expect(
      coveredTypes.toList()..sort(),
      orderedEquals(catalog.iosSupportedWidgetTypes),
    );
  });

  test('widget parity cases match expected semantics', () async {
    final cases = ParityCase.loadAll(casesPath);
    final registry = VirtualWidgetRegistry(
      componentBuilder: (_, __, ___) => const SizedBox.shrink(),
    );

    for (final testCase in cases) {
      final resources = ScopeResources.fromJson(testCase.scope);
      addTearDown(resources.dispose);

      final data = VWData.fromJson(testCase.node);
      expect(data, isA<VWNodeData>(), reason: testCase.id);

      final node = data as VWNodeData;
      final widget = registry.createWidget(node, null);
      final snapshot = extractSnapshot(
        id: testCase.id,
        node: node,
        widget: widget,
        scope: resources.scopeContext,
      );

      expect(
        containsSubset(snapshot, testCase.expectation),
        isTrue,
        reason:
            'case=${testCase.id}\nexpected=${jsonEncode(testCase.expectation)}\nactual=${jsonEncode(snapshot)}',
      );
    }
  });
}

class ParityCatalog {
  final List<String> iosSupportedWidgetTypes;
  final List<String> flutterOnlyWidgetTypes;

  const ParityCatalog({
    required this.iosSupportedWidgetTypes,
    required this.flutterOnlyWidgetTypes,
  });

  factory ParityCatalog.load(String path) {
    final json =
        jsonDecode(File(path).readAsStringSync()) as Map<String, dynamic>;
    return ParityCatalog(
      iosSupportedWidgetTypes:
          (json['iosSupportedWidgetTypes'] as List).cast<String>()..sort(),
      flutterOnlyWidgetTypes:
          (json['flutterOnlyWidgetTypes'] as List).cast<String>()..sort(),
    );
  }
}

class ParityCase {
  final String id;
  final String type;
  final String prop;
  final Map<String, dynamic> node;
  final Map<String, dynamic> expectation;
  final Map<String, dynamic>? scope;

  const ParityCase({
    required this.id,
    required this.type,
    required this.prop,
    required this.node,
    required this.expectation,
    required this.scope,
  });

  static List<ParityCase> loadAll(String path) {
    final json =
        jsonDecode(File(path).readAsStringSync()) as Map<String, dynamic>;
    return (json['cases'] as List)
        .cast<Map<String, dynamic>>()
        .map(
          (item) => ParityCase(
            id: item['id'] as String,
            type: item['type'] as String,
            prop: item['prop'] as String,
            node: (item['node'] as Map).cast<String, dynamic>(),
            expectation: (item['expect'] as Map).cast<String, dynamic>(),
            scope: (item['scope'] as Map?)?.cast<String, dynamic>(),
          ),
        )
        .toList();
  }
}

class ScopeResources {
  final DefaultScopeContext scopeContext;
  final List<StreamController<Object?>> _controllers;

  ScopeResources._(this.scopeContext, this._controllers);

  factory ScopeResources.fromJson(Map<String, dynamic>? json) {
    final variables = <String, Object?>{};
    final controllers = <StreamController<Object?>>[];

    final variableMap = (json?['variables'] as Map?)?.cast<String, Object?>();
    if (variableMap != null) {
      variables.addAll(variableMap);
    }

    final streamMap = (json?['streams'] as Map?)?.cast<String, Object?>();
    streamMap?.forEach((key, value) {
      final controller = StreamController<Object?>.broadcast();
      final currentValue =
          value is Map<String, Object?> ? value['currentValue'] : null;
      if (currentValue != null) {
        controller.add(currentValue);
      }
      variables[key] = controller;
      controllers.add(controller);
    });

    return ScopeResources._(
      DefaultScopeContext(variables: variables),
      controllers,
    );
  }

  Future<void> dispose() async {
    for (final controller in _controllers) {
      await controller.close();
    }
  }
}

Set<String> deriveFlutterRegistryTypes(String path) {
  final source = File(path).readAsStringSync();
  return RegExp(r"'([^']+)':\s*\w+")
      .allMatches(source)
      .map((match) => match.group(1)!)
      .toSet();
}

Set<String> deriveIosSupportedTypes(String path) {
  final values = <String>{};
  for (final line in File(path).readAsLinesSync()) {
    if (!line.contains('case ')) continue;
    for (final match in RegExp(r'"([^"]+)"').allMatches(line)) {
      final value = match.group(1)!;
      if (value != 'state' && value != 'component') {
        values.add(value);
      }
    }
  }
  return values;
}

Map<String, Object?> extractSnapshot({
  required String id,
  required VWNodeData node,
  required VirtualWidget widget,
  required ScopeContext scope,
}) {
  final snapshot = <String, Object?>{
    'caseId': id,
    'widgetType': node.type,
    'widgetClass': widget.runtimeType.toString(),
    'refName': node.refName,
  };

  switch (node.type) {
    case 'digia/text':
      snapshot.addAll(
        {
          'textValue': resolveString(node.props.get('text'), scope),
          'alignment': resolveString(node.props.get('alignment'), scope),
          'maxLines': resolveInt(node.props.get('maxLines'), scope),
          'overflow': resolveString(node.props.get('overflow'), scope),
        },
      );
      break;
    case 'fw/scaffold':
    case 'digia/scaffold':
      snapshot.addAll(
        {
          'enableSafeArea':
              resolveBool(node.props.get('enableSafeArea'), scope) ?? true,
          'backgroundColor':
              resolveString(node.props.get('scaffoldBackgroundColor'), scope),
          'bodyChildType': firstChildType(node, 'body'),
        },
      );
      break;
    case 'digia/container':
      snapshot.addAll(
        {
          'resolvedColor': resolveString(node.props.get('color'), scope),
          'width': resolveDouble(node.props.get('width'), scope),
          'height': resolveDouble(node.props.get('height'), scope),
          'childAlignment': node.props.getString('childAlignment'),
          'padding': normalizeSpacing(node.props.get('padding')),
          'margin': normalizeSpacing(node.props.get('margin')),
          'borderRadius': resolveDouble(node.props.get('borderRadius'), scope),
          'childTypes': childTypes(node, 'child'),
        },
      );
      break;
    case 'digia/column':
      snapshot.addAll(flexSnapshot(node, scope, 'vertical'));
      break;
    case 'digia/row':
      snapshot.addAll(flexSnapshot(node, scope, 'horizontal'));
      break;
    case 'digia/stack':
      snapshot.addAll(
        {
          'alignment': node.props.getString('childAlignment'),
          'fit': node.props.getString('fit'),
          'childCount': childCount(node, 'children'),
          'positionedChildCount': positionedChildCount(node),
        },
      );
      break;
    case 'digia/richText':
      final spans =
          (node.props.get('textSpans') as List?)?.cast<Object?>() ?? const [];
      snapshot.addAll(
        {
          'spanTexts': spans
              .map((span) => span is Map<String, Object?>
                  ? resolveString(span['text'], scope)
                  : resolveString(span, scope))
              .toList(),
          'spanCount': spans.length,
          'tappableSpanCount': spans
              .whereType<Map<String, Object?>>()
              .where((span) => span['onClick'] != null)
              .length,
          'alignment': resolveString(node.props.get('alignment'), scope),
          'maxLines': resolveInt(node.props.get('maxLines'), scope),
          'overflow': resolveString(node.props.get('overflow'), scope),
        },
      );
      break;
    case 'digia/button':
      snapshot.addAll(
        {
          'textValue': resolveString(node.props.get('text.text'), scope),
          'isDisabled': resolveBool(node.props.get('isDisabled'), scope) ??
              (node.props.get('onClick') == null),
          'hasOnClick': node.props.get('onClick') != null,
          'leadingIconKey': node.props.getString('leadingIcon.iconData.key'),
          'trailingIconKey': node.props.getString('trailingIcon.iconData.key'),
          'shape': node.props.getString('shape.value'),
          'width': resolveDouble(node.props.get('defaultStyle.width'), scope),
          'height': resolveDouble(node.props.get('defaultStyle.height'), scope),
        },
      );
      break;
    case 'digia/gridView':
      snapshot.addAll(
        {
          'allowScroll':
              resolveBool(node.props.get('allowScroll'), scope) ?? true,
          'mainAxisSpacing':
              resolveDouble(node.props.get('mainAxisSpacing'), scope),
          'crossAxisSpacing':
              resolveDouble(node.props.get('crossAxisSpacing'), scope),
          'scrollDirection':
              resolveString(node.props.get('scrollDirection'), scope) ??
                  'vertical',
          'crossAxisCount': resolveInt(node.props.get('crossAxisCount'), scope),
          'dataSourceCount':
              resolvedListCount(node.props.get('dataSource'), scope),
          'childTypes': childTypes(node, 'child'),
        },
      );
      break;
    case 'digia/streamBuilder':
      snapshot.addAll(
        {
          'hasController': evaluate<Object>(
                node.props.get('controller'),
                scopeContext: scope,
              ) !=
              null,
          'initialData': resolveValue(node.props.get('initialData'), scope),
          'hasOnSuccess': node.props.get('onSuccess') != null,
          'hasOnError': node.props.get('onError') != null,
          'childTypes': childTypes(node, 'child'),
        },
      );
      break;
    case 'digia/image':
      snapshot.addAll(
        {
          'source': resolveString(
            node.props.get('src.imageSrc') ?? node.props.get('imageSrc'),
            scope,
          ),
          'fit': node.props.getString('fit'),
          'alignment': node.props.getString('alignment'),
          'aspectRatio': resolveDouble(node.props.get('aspectRatio'), scope),
          'placeholder': node.props.getString('placeholder'),
          'placeholderSrc': node.props.getString('placeholderSrc'),
          'errorImageSrc': normalizeErrorImage(node.props.get('errorImage')),
        },
      );
      break;
    case 'digia/lottie':
      snapshot.addAll(
        {
          'lottiePath': resolveString(
            node.props.get('src.lottiePath') ?? node.props.get('lottiePath'),
            scope,
          ),
          'width': resolveDouble(node.props.get('width'), scope),
          'height': resolveDouble(node.props.get('height'), scope),
          'alignment': resolveString(node.props.get('alignment'), scope),
          'fit': resolveString(node.props.get('fit'), scope),
          'animate': resolveBool(node.props.get('animate'), scope) ?? true,
          'animationType': node.props.getString('animationType') ?? 'loop',
          'frameRate': resolveDouble(node.props.get('frameRate'), scope),
        },
      );
      break;
    case 'fw/sized_box':
      snapshot.addAll(
        {
          'width': resolveDouble(node.props.get('width'), scope),
          'height': resolveDouble(node.props.get('height'), scope),
        },
      );
      break;
    case 'digia/conditionalBuilder':
      snapshot.addAll(
        {
          'childCount': childCount(node, 'children'),
          'selectedChildRefName': resolveConditionalBuilderChild(node, scope),
        },
      );
      break;
    case 'digia/conditionalItem':
      snapshot.addAll(
        {
          'conditionResult':
              resolveBool(node.props.get('condition'), scope) ?? false,
          'childTypes': childTypes(node, 'child'),
        },
      );
      break;
    case 'digia/linearProgressBar':
      snapshot.addAll(progressBarSnapshot(node, scope, sizeKey: 'width'));
      break;
    case 'digia/circularProgressBar':
      snapshot.addAll(progressBarSnapshot(node, scope, sizeKey: 'size'));
      break;
    case 'digia/carousel':
      snapshot.addAll(
        {
          'width': resolveDouble(node.props.get('width'), scope),
          'height': resolveDouble(node.props.get('height'), scope),
          'initialPage': resolveInt(node.props.get('initialPage'), scope),
          'autoPlay': node.props.getBool('autoPlay') ?? false,
          'infiniteScroll': node.props.getBool('infiniteScroll') ?? false,
          'viewportFraction':
              resolveDouble(node.props.get('viewportFraction'), scope),
          'showIndicator': node.props
                  .getBool('indicator.indicatorAvailable.showIndicator') ??
              false,
          'childCount': childCount(node, 'children'),
        },
      );
      break;
    default:
      fail('Unhandled widget type ${node.type}');
  }

  snapshot.removeWhere((_, value) => value == null);
  return snapshot;
}

Map<String, Object?> flexSnapshot(
  VWNodeData node,
  ScopeContext scope,
  String direction,
) {
  return {
    'direction': direction,
    'spacing': resolveDouble(node.props.get('spacing'), scope),
    'startSpacing': resolveDouble(node.props.get('startSpacing'), scope),
    'endSpacing': resolveDouble(node.props.get('endSpacing'), scope),
    'mainAxisAlignment': node.props.getString('mainAxisAlignment'),
    'crossAxisAlignment': node.props.getString('crossAxisAlignment'),
    'mainAxisSize': node.props.getString('mainAxisSize') ?? 'max',
    'isScrollable': node.props.getBool('isScrollable') ?? false,
    'dataSourceCount': resolvedListCount(node.props.get('dataSource'), scope),
    'childCount': childCount(node, 'children'),
  }..removeWhere((_, value) => value == null);
}

Map<String, Object?> progressBarSnapshot(
  VWNodeData node,
  ScopeContext scope, {
  required String sizeKey,
}) {
  return {
    'progressValue': resolveDouble(node.props.get('progressValue'), scope),
    sizeKey: resolveDouble(node.props.get(sizeKey), scope),
    'thickness': resolveDouble(node.props.get('thickness'), scope),
    'typeValue': node.props.getString('type') ?? 'indeterminate',
    'indicatorColor': resolveString(node.props.get('indicatorColor'), scope),
    'bgColor': resolveString(node.props.get('bgColor'), scope),
    'borderRadius': resolveDouble(node.props.get('borderRadius'), scope),
    'isReverse': resolveBool(node.props.get('isReverse'), scope),
  }..removeWhere((_, value) => value == null);
}

String? firstChildType(VWNodeData node, String group) {
  final children = node.childGroups?[group];
  if (children == null || children.isEmpty) return null;
  final first = children.first;
  return first is VWNodeData ? first.type : null;
}

List<String> childTypes(VWNodeData node, String group) {
  final children = node.childGroups?[group];
  if (children == null) return const [];
  return children.whereType<VWNodeData>().map((child) => child.type).toList();
}

int childCount(VWNodeData node, String group) =>
    node.childGroups?[group]?.length ?? 0;

int positionedChildCount(VWNodeData node) {
  final children = node.childGroups?['children'] ?? const [];
  return children
      .whereType<VWNodeData>()
      .where((child) => child.parentProps?.get('position') != null)
      .length;
}

String? resolveConditionalBuilderChild(VWNodeData node, ScopeContext scope) {
  final children = node.childGroups?['children'] ?? const [];
  for (final child in children.whereType<VWNodeData>()) {
    final result = resolveBool(child.props.get('condition'), scope) ?? false;
    if (result) return child.refName;
  }
  return null;
}

int? resolvedListCount(Object? raw, ScopeContext scope) {
  final resolved = evaluate<Object>(raw, scopeContext: scope);
  if (resolved is List) return resolved.length;
  return null;
}

Object? resolveValue(Object? raw, ScopeContext scope) {
  if (raw == null) return null;
  if (raw is Map<String, Object?> && raw.containsKey('expr')) {
    return evaluate<Object>(raw['expr'], scopeContext: scope) ?? raw['expr'];
  }
  return evaluate<Object>(raw, scopeContext: scope) ?? raw;
}

String? resolveString(Object? raw, ScopeContext scope) {
  final resolved = resolveValue(raw, scope);
  return resolved?.toString();
}

bool? resolveBool(Object? raw, ScopeContext scope) {
  final resolved = resolveValue(raw, scope);
  if (resolved is bool) return resolved;
  if (resolved is String) return resolved.toLowerCase() == 'true';
  return null;
}

int? resolveInt(Object? raw, ScopeContext scope) {
  final resolved = resolveValue(raw, scope);
  if (resolved is int) return resolved;
  if (resolved is double) return resolved.toInt();
  if (resolved is String) return int.tryParse(resolved);
  return null;
}

double? resolveDouble(Object? raw, ScopeContext scope) {
  final resolved = resolveValue(raw, scope);
  if (resolved is int) return resolved.toDouble();
  if (resolved is double) return resolved;
  if (resolved is String) return double.tryParse(resolved);
  return null;
}

List<double>? normalizeSpacing(Object? raw) {
  if (raw == null) return null;
  if (raw is num) {
    final value = raw.toDouble();
    return [value, value, value, value];
  }
  if (raw is String) {
    final values = raw
        .split(',')
        .map((part) => double.tryParse(part.trim()))
        .whereType<double>()
        .toList();
    if (values.length == 1) {
      return [values.first, values.first, values.first, values.first];
    }
    if (values.length == 2) {
      final horizontal = values.first;
      final vertical = values.last;
      return [vertical, horizontal, vertical, horizontal];
    }
    if (values.length == 4) {
      return [values[1], values[0], values[3], values[2]];
    }
  }
  if (raw is Map<String, Object?>) {
    if (raw['all'] is num) {
      final value = (raw['all'] as num).toDouble();
      return [value, value, value, value];
    }
    if (raw['horizontal'] is num && raw['vertical'] is num) {
      final horizontal = (raw['horizontal'] as num).toDouble();
      final vertical = (raw['vertical'] as num).toDouble();
      return [vertical, horizontal, vertical, horizontal];
    }
    return [
      (raw['top'] as num?)?.toDouble() ?? 0,
      (raw['left'] as num?)?.toDouble() ?? 0,
      (raw['bottom'] as num?)?.toDouble() ?? 0,
      (raw['right'] as num?)?.toDouble() ?? 0,
    ];
  }
  return null;
}

String? normalizeErrorImage(Object? raw) {
  if (raw is String) return raw;
  if (raw is Map<String, Object?>) return raw['errorSrc']?.toString();
  return null;
}

bool containsSubset(Object? actual, Object? expected) {
  if (expected is Map<String, dynamic>) {
    if (actual is! Map) return false;
    for (final entry in expected.entries) {
      if (!actual.containsKey(entry.key)) return false;
      if (!containsSubset(actual[entry.key], entry.value)) return false;
    }
    return true;
  }

  if (expected is List) {
    if (actual is! List || actual.length != expected.length) return false;
    for (var index = 0; index < expected.length; index++) {
      if (!containsSubset(actual[index], expected[index])) return false;
    }
    return true;
  }

  if (actual is num && expected is num) {
    return (actual - expected).abs() < 0.0001;
  }

  return actual == expected;
}
