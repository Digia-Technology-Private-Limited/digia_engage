// STUB: flutter_js removed for APK size analysis.
// This file replaces mobile_js_functions.dart when flutter_js is not in pubspec.
import 'js_functions.dart';

/// Returns a no-op [JSFunctions] that compiles without flutter_js.
JSFunctions getJSFunction() => _NoopJsFunctions();

class _NoopJsFunctions implements JSFunctions {
  @override
  dynamic callJs(String fnName, dynamic data) =>
      throw UnsupportedError('JS engine excluded in this build variant');

  @override
  Future<dynamic> callAsyncJs(String fnName, dynamic data) async =>
      throw UnsupportedError('JS engine excluded in this build variant');

  @override
  Future<bool> initFunctions(FunctionInitStrategy strategy) async => false;
}
