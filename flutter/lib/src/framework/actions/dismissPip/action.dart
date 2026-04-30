import '../../models/types.dart';
import '../../utils/types.dart';
import '../base/action.dart';

class DismissPipAction extends Action {
  DismissPipAction({super.disableActionIf});

  @override
  ActionType get actionType => ActionType.dismissPip;

  @override
  Map<String, dynamic> toJson() => {'type': actionType.toString()};

  factory DismissPipAction.fromJson(Map<String, Object?> json) {
    return DismissPipAction(
      disableActionIf: ExprOr.fromJson<bool>(json['disableActionIf']),
    );
  }
}
