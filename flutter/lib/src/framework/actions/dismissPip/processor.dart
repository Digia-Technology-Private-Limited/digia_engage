import 'package:digia_inspector_core/digia_inspector_core.dart';
import 'package:flutter/material.dart';

import '../../expr/scope_context.dart';
import '../../pip/pip_manager.dart';
import '../base/processor.dart';
import 'action.dart';

class DismissPipProcessor extends ActionProcessor<DismissPipAction> {
  @override
  Future<Object?>? execute(
    BuildContext context,
    DismissPipAction action,
    ScopeContext? scopeContext, {
    required String id,
    String? parentActionId,
    ObservabilityContext? observabilityContext,
  }) async {
    PipManager.instance.dismiss();
    return null;
  }
}
