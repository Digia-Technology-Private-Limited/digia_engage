import '../../models/types.dart';
import '../../utils/types.dart';
import '../base/action.dart';

class ShowPipAction extends Action {
  final JsonLike data;

  ShowPipAction({required this.data, super.disableActionIf});

  @override
  ActionType get actionType => ActionType.showPip;

  @override
  Map<String, dynamic> toJson() => {'type': actionType.toString(), 'data': data};

  factory ShowPipAction.fromJson(Map<String, Object?> json) {
    return ShowPipAction(
      data: (json as JsonLike?) ?? {},
      disableActionIf: ExprOr.fromJson<bool>(json['disableActionIf']),
    );
  }
}
