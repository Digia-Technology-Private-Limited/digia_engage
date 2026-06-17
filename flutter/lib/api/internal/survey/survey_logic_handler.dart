import 'survey_config.dart';

/// A single user answer. [values] holds the comparable tokens used by branching
/// (option ids, or `[score]`, or `[text]`); [comment] holds free text such as
/// an "other" option's note.
class SurveyAnswer {
  final List<String> values;
  final String? comment;

  const SurveyAnswer({this.values = const [], this.comment});

  bool get isAnswered =>
      values.any((v) => v.trim().isNotEmpty) || (comment != null && comment!.trim().isNotEmpty);

  Map<String, dynamic> toMap() => {'values': values, 'comment': comment};

  /// Numeric view of a scalar answer (score / numeric input), or `null`.
  double? asNumber() {
    if (values.isEmpty) return null;
    return double.tryParse(values.first.trim());
  }
}

/// Sentinel id meaning "the survey is finished".
const String surveyFinished = '__digia_survey_finished__';

class SurveyNavigation {
  /// Target node id, or [surveyFinished] when the survey should end.
  final String nextNodeId;
  final String? redirectUrl;

  const SurveyNavigation(this.nextNodeId, {this.redirectUrl});
}

/// Pure branching runtime — operates on the node graph (no Flutter
/// dependencies). Resolves node-owned branching rules and conditional block
/// visibility (`showWhen`).
abstract final class SurveyLogicHandler {
  /// Id of the first node that should be shown, honouring `showWhen`.
  static String firstNodeId(SurveyConfigModel survey, Map<String, SurveyAnswer> answers) {
    final root = survey.rootNode();
    if (root == null) return surveyFinished;
    return _scanForwardFrom(survey, root, answers, <String>{});
  }

  /// Decides the next node after [currentNodeId] has been answered. Always
  /// returns a valid node id or [surveyFinished].
  static SurveyNavigation nextStep(
    SurveyConfigModel survey,
    String currentNodeId,
    Map<String, SurveyAnswer> answers,
  ) {
    final node = survey.nodeById(currentNodeId);
    if (node == null) return const SurveyNavigation(surveyFinished);
    final branching = node.branching;

    if (branching.type != BranchingType.linear) {
      for (final rule in branching.rules) {
        if (_evaluateExpr(rule.whenExpr, node, branching, answers)) {
          return _resolveTarget(survey, currentNodeId, rule.target, answers);
        }
      }
    }
    return _resolveTarget(survey, currentNodeId, branching.defaultTarget, answers);
  }

  /// Whether [block] passes its authored `showWhen` gate.
  static bool isVisible(
    SurveyBlock block,
    String ownerNodeId,
    Map<String, SurveyAnswer> answers,
  ) {
    final expr = block.showWhen;
    if (expr == null) return true;
    return _evaluateExprForNode(expr, ownerNodeId, answers);
  }

  // ── internal helpers ──────────────────────────────────────────────────────

  static String _scanForwardFrom(
    SurveyConfigModel survey,
    SurveyNode startNode,
    Map<String, SurveyAnswer> answers,
    Set<String> visited,
  ) {
    SurveyNode? current = startNode;
    while (current != null) {
      if (!visited.add(current.id)) return surveyFinished; // cycle guard
      final block = survey.blockFor(current);
      if (block == null || isVisible(block, current.id, answers)) return current.id;
      current = _nextNodeAfter(survey, current, answers);
    }
    return surveyFinished;
  }

  static SurveyNode? _nextNodeAfter(
    SurveyConfigModel survey,
    SurveyNode node,
    Map<String, SurveyAnswer> answers,
  ) {
    final target = node.branching.defaultTarget;
    switch (target.kind) {
      case BranchTargetKind.node:
        return survey.nodeById(target.nodeId);
      case BranchTargetKind.next:
        final idx = survey.nodes.indexWhere((n) => n.id == node.id);
        return (idx >= 0 && idx + 1 < survey.nodes.length) ? survey.nodes[idx + 1] : null;
      case BranchTargetKind.url:
      case BranchTargetKind.end:
        return null;
    }
  }

  static SurveyNavigation _resolveTarget(
    SurveyConfigModel survey,
    String currentNodeId,
    BranchTarget target,
    Map<String, SurveyAnswer> answers,
  ) {
    switch (target.kind) {
      case BranchTargetKind.end:
        return const SurveyNavigation(surveyFinished);
      case BranchTargetKind.url:
        return SurveyNavigation(surveyFinished,
            redirectUrl: target.url.isNotEmpty ? target.url : null);
      case BranchTargetKind.node:
        final next = survey.nodeById(target.nodeId);
        if (next == null) return const SurveyNavigation(surveyFinished);
        return SurveyNavigation(_scanForwardFrom(survey, next, answers, <String>{}));
      case BranchTargetKind.next:
        final idx = survey.nodes.indexWhere((n) => n.id == currentNodeId);
        final next = (idx >= 0 && idx + 1 < survey.nodes.length) ? survey.nodes[idx + 1] : null;
        if (next == null) return const SurveyNavigation(surveyFinished);
        return SurveyNavigation(_scanForwardFrom(survey, next, answers, <String>{}));
    }
  }

  static bool _evaluateExpr(
    ConditionExpr expr,
    SurveyNode ownerNode,
    NodeBranching branching,
    Map<String, SurveyAnswer> answers,
  ) {
    // `by_parent` rewrites a condition's null nodeId to the configured parent;
    // `by_condition` (and `linear` fallback) treats null nodeId as the owner.
    final defaultAnswerNodeId = branching.type == BranchingType.byParent
        ? (branching.parentNodeId ?? ownerNode.id)
        : ownerNode.id;
    return _evaluateExprForNode(expr, defaultAnswerNodeId, answers);
  }

  static bool _evaluateExprForNode(
    ConditionExpr expr,
    String defaultAnswerNodeId,
    Map<String, SurveyAnswer> answers,
  ) {
    final groupResults = expr.groups.map((group) {
      final conditionResults = group.conditions.map((condition) {
        final answerNodeId = condition.nodeId ?? defaultAnswerNodeId;
        return _evaluate(condition.operator, condition.values, group.operator, answers[answerNodeId]);
      });
      return switch (group.operator) {
        BoolOp.and => conditionResults.every((r) => r),
        BoolOp.or => conditionResults.any((r) => r),
      };
    });
    return switch (expr.operator) {
      BoolOp.and => groupResults.every((r) => r),
      BoolOp.or => groupResults.any((r) => r),
    };
  }

  static bool _evaluate(
    ConditionOperator operator,
    List<String> targets,
    BoolOp logicOp,
    SurveyAnswer? answer,
  ) {
    final answered = answer?.isAnswered == true;
    switch (operator) {
      case ConditionOperator.isAnswered:
        return answered;
      case ConditionOperator.isNotAnswered:
        return !answered;
      default:
        if (!answered) return false;
    }

    final values = answer!.values;
    final text = ([...values, if (answer.comment != null) answer.comment!]).join(' ').toLowerCase();

    switch (operator) {
      case ConditionOperator.equals:
      case ConditionOperator.isExactly:
        return _setEquals(values, targets);
      case ConditionOperator.notEquals:
        return !_setEquals(values, targets);
      case ConditionOperator.includesAny:
        return targets.any(values.contains);
      case ConditionOperator.includesAll:
        return targets.every(values.contains);
      case ConditionOperator.contains:
        return _combine(logicOp, targets, (t) => text.contains(t.toLowerCase()));
      case ConditionOperator.notContains:
        return !_combine(logicOp, targets, (t) => text.contains(t.toLowerCase()));
      case ConditionOperator.greaterThan:
        final n = answer.asNumber();
        final t = targets.isEmpty ? null : double.tryParse(targets.first);
        if (n == null || t == null) return false;
        return n > t;
      case ConditionOperator.lessThan:
        final n = answer.asNumber();
        final t = targets.isEmpty ? null : double.tryParse(targets.first);
        if (n == null || t == null) return false;
        return n < t;
      case ConditionOperator.isBetween:
        final n = answer.asNumber();
        final low = targets.isNotEmpty ? double.tryParse(targets[0]) : null;
        final high = targets.length > 1 ? double.tryParse(targets[1]) : null;
        if (n == null || low == null || high == null) return false;
        final lo = low < high ? low : high;
        final hi = low < high ? high : low;
        return n >= lo && n <= hi;
      case ConditionOperator.isAnswered:
      case ConditionOperator.isNotAnswered:
        return true;
    }
  }

  static bool _setEquals(List<String> a, List<String> b) {
    final sa = a.toSet();
    final sb = b.toSet();
    return sa.length == sb.length && sa.containsAll(sb);
  }

  static bool _combine(BoolOp logicOp, List<String> targets, bool Function(String) predicate) {
    if (targets.isEmpty) return false;
    return switch (logicOp) {
      BoolOp.and => targets.every(predicate),
      BoolOp.or => targets.any(predicate),
    };
  }
}
