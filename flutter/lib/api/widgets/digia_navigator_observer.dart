import 'package:flutter/material.dart';

import '../internal/digia_instance.dart';

/// A [NavigatorObserver] that automatically tracks screen transitions and
/// forwards them to the registered CEP plugin via [Digia.setCurrentScreen].
///
/// Add to [MaterialApp.navigatorObservers] for zero-effort screen tracking:
///
/// ```dart
/// MaterialApp(
///   navigatorObservers: [DigiaNavigatorObserver()],
///   builder: (context, child) => DigiaHost(child: child!),
/// )
/// ```
///
/// The screen name reported is the route name (e.g. `/checkout`). For edge
/// cases where the route name is unavailable or you need a custom name, call
/// [Digia.setCurrentScreen] directly instead.
class DigiaNavigatorObserver extends NavigatorObserver {
  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    final name = _extractName(route);
    if (name != null) {
      DigiaInstance.instance.setCurrentScreen(name);
    }
  }

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    final name = newRoute != null ? _extractName(newRoute) : null;
    if (name != null) {
      DigiaInstance.instance.setCurrentScreen(name);
    }
  }

  /// Extracts a human-readable screen name from a route.
  ///
  /// Prefers the explicit route name when set. Falls back to the runtime type
  /// name stripped of angle-brackets (e.g. `MaterialPageRoute` → ignored,
  /// named routes like `/home` are forwarded as-is).
  String? _extractName(Route<dynamic> route) {
    final name = route.settings.name;
    // Skip unnamed or internal Flutter overlay routes (e.g. dialogs).
    if (name == null || name.isEmpty) return null;
    return name;
  }
}
