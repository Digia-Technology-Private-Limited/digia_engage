import 'dart:async';
import 'base.dart';
import 'register_bindings.dart';

class MethodBindingRegistry {
  final Map<Type, Map<String, MethodCommand>> _bindings = {};

  MethodBindingRegistry() {
    registerBindings(this);
  }

  // Registers a command for a given method name
  void registerMethods<T>(Map<String, MethodCommand<T>> commands) {
    _bindings[T] = commands;
  }

  // Executes the command by name, passing the instance and arguments
  void execute<T>(T instance, String methodName, Map<String, Object?> args) {
    Type effectiveType = instance.runtimeType;
    if (!_bindings.containsKey(effectiveType) && instance is StreamController) {
      effectiveType = StreamController;
    }
    if (_bindings.containsKey(effectiveType) &&
        _bindings[effectiveType]!.containsKey(methodName)) {
      final command = _bindings[effectiveType]![methodName];
      command?.run(instance, args);
      return;
    }

    throw Exception(
        'Method $methodName not found on instance of type: ${instance.runtimeType}');
  }

  void dispose() {
    _bindings.clear();
  }
}
