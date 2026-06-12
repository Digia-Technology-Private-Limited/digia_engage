import 'package:digia_engage/api/internal/survey/survey_config.dart';
import 'package:digia_engage/api/internal/survey/survey_logic_handler.dart';
import 'package:flutter_test/flutter_test.dart';

/// Exercises the node-graph branching runtime ([SurveyLogicHandler]) against the
/// block/node model. Node id == block id throughout for readability. A Dart port
/// of the Android `SurveyLogicHandlerTest`.
void main() {
  SurveyBlock block(
    String id, {
    SurveyBlockType type = SurveyBlockType.singleSelect,
    ConditionExpr? showWhen,
    bool hidden = false,
  }) =>
      SurveyBlock(
        id: id,
        type: type,
        title: const RichText(''),
        body: null,
        options: const [],
        optionStyle: null,
        npsStyle: null,
        required: false,
        hidden: hidden,
        showMedia: false,
        media: BlockMedia.empty,
        showTag: true,
        showAnswerMedia: false,
        showAnswerDescriptions: false,
        shuffle: false,
        allowOther: false,
        flexibleHeight: false,
        answerLayout: AnswerLayout.column,
        backgroundColorHex: '',
        numberMin: null,
        numberMax: null,
        showWhen: showWhen,
      );

  SurveyNode linearNode(String id) => SurveyNode(id: id, blockId: id, branching: NodeBranching.linearNext);

  SurveyNode conditionalNode(String id, List<BranchRule> rules, {BranchTarget defaultTarget = BranchTarget.next}) =>
      SurveyNode(
        id: id,
        blockId: id,
        branching: NodeBranching(
          type: BranchingType.byCondition,
          rules: rules,
          parentNodeId: null,
          defaultTarget: defaultTarget,
        ),
      );

  Condition cond(ConditionOperator operator, {List<String> values = const [], String? nodeId}) =>
      Condition(nodeId: nodeId, operator: operator, values: values);

  ConditionExpr whenAny(List<Condition> conditions) =>
      ConditionExpr(operator: BoolOp.or, groups: [ConditionGroup(operator: BoolOp.and, conditions: conditions)]);

  BranchRule rule(String id, ConditionExpr whenExpr, BranchTarget target) =>
      BranchRule(id: id, whenExpr: whenExpr, target: target);

  BranchTarget nodeTarget(String id) => BranchTarget(kind: BranchTargetKind.node, nodeId: id, url: '');

  SurveyConfigModel survey(List<SurveyNode> nodes, {List<SurveyBlock>? blocks, String? rootNodeId}) =>
      SurveyConfigModel(
        id: 's',
        name: null,
        blocks: blocks ?? [for (final n in nodes) block(n.blockId)],
        nodes: nodes,
        rootNodeId: rootNodeId ?? nodes.first.id,
        settings: SurveySettings.defaults,
        theme: const SurveyTheme(0, 0),
        uiTemplateId: null,
        timeDelayMs: 0,
      );

  SurveyAnswer answer(List<String> values) => SurveyAnswer(values: values);

  test('linear flow advances node by node', () {
    final s = survey([linearNode('q1'), linearNode('q2'), linearNode('q3')]);
    expect(SurveyLogicHandler.firstNodeId(s, const {}), 'q1');
    expect(SurveyLogicHandler.nextStep(s, 'q1', const {}).nextNodeId, 'q2');
    expect(SurveyLogicHandler.nextStep(s, 'q2', const {}).nextNodeId, 'q3');
  });

  test('last node with linear next finishes', () {
    final s = survey([linearNode('q1'), linearNode('q2')]);
    expect(SurveyLogicHandler.nextStep(s, 'q2', const {}).nextNodeId, surveyFinished);
  });

  test('end target finishes immediately', () {
    final s = survey([
      conditionalNode('q1', [rule('r1', whenAny([cond(ConditionOperator.isAnswered)]), BranchTarget.end)]),
      linearNode('q2'),
    ]);
    expect(SurveyLogicHandler.nextStep(s, 'q1', {'q1': answer(['a'])}).nextNodeId, surveyFinished);
  });

  test('equals rule jumps to target node', () {
    final s = survey([
      conditionalNode('q1', [rule('r1', whenAny([cond(ConditionOperator.equals, values: ['yes'])]), nodeTarget('q3'))]),
      linearNode('q2'),
      linearNode('q3'),
    ]);
    expect(SurveyLogicHandler.nextStep(s, 'q1', {'q1': answer(['yes'])}).nextNodeId, 'q3');
  });

  test('non-matching rule falls through to next node', () {
    final s = survey([
      conditionalNode('q1', [rule('r1', whenAny([cond(ConditionOperator.equals, values: ['yes'])]), nodeTarget('q3'))]),
      linearNode('q2'),
      linearNode('q3'),
    ]);
    expect(SurveyLogicHandler.nextStep(s, 'q1', {'q1': answer(['no'])}).nextNodeId, 'q2');
  });

  test('first matching rule wins', () {
    final s = survey([
      conditionalNode('q1', [
        rule('r1', whenAny([cond(ConditionOperator.isAnswered)]), BranchTarget.end),
        rule('r2', whenAny([cond(ConditionOperator.isAnswered)]), nodeTarget('q2')),
      ]),
      linearNode('q2'),
    ]);
    expect(SurveyLogicHandler.nextStep(s, 'q1', {'q1': answer(['a'])}).nextNodeId, surveyFinished);
  });

  test('showWhen gates block visibility', () {
    final q2 = block('q2', showWhen: whenAny([cond(ConditionOperator.equals, values: ['show'], nodeId: 'q1')]));
    expect(SurveyLogicHandler.isVisible(q2, 'q2', {'q1': answer(['show'])}), isTrue);
    expect(SurveyLogicHandler.isVisible(q2, 'q2', {'q1': answer(['no'])}), isFalse);
  });

  test('greaterThan compares numeric answers', () {
    final s = survey(
      [
        conditionalNode('q1', [rule('r1', whenAny([cond(ConditionOperator.greaterThan, values: ['8'])]), BranchTarget.end)]),
        linearNode('q2'),
      ],
      blocks: [block('q1', type: SurveyBlockType.nps), block('q2')],
    );
    expect(SurveyLogicHandler.nextStep(s, 'q1', {'q1': answer(['9'])}).nextNodeId, surveyFinished);
    expect(SurveyLogicHandler.nextStep(s, 'q1', {'q1': answer(['7'])}).nextNodeId, 'q2');
  });

  test('includesAny matches multi-select answers', () {
    final s = survey(
      [
        conditionalNode('q1', [rule('r1', whenAny([cond(ConditionOperator.includesAny, values: ['c2', 'c4'])]), nodeTarget('q3'))]),
        linearNode('q2'),
        linearNode('q3'),
      ],
      blocks: [block('q1', type: SurveyBlockType.multiSelect), block('q2'), block('q3')],
    );
    expect(SurveyLogicHandler.nextStep(s, 'q1', {'q1': answer(['c1', 'c2'])}).nextNodeId, 'q3');
  });

  test('isNotAnswered is true for an unanswered node', () {
    final s = survey([
      conditionalNode('q1', [rule('r1', whenAny([cond(ConditionOperator.isNotAnswered)]), BranchTarget.end)]),
      linearNode('q2'),
    ]);
    expect(SurveyLogicHandler.nextStep(s, 'q1', const {}).nextNodeId, surveyFinished);
  });

  test('firstNodeId skips a leading showWhen-hidden node', () {
    final s = survey(
      [linearNode('q1'), linearNode('q2')],
      blocks: [
        block('q1', showWhen: whenAny([cond(ConditionOperator.isAnswered, nodeId: 'qX')])),
        block('q2'),
      ],
    );
    expect(SurveyLogicHandler.firstNodeId(s, const {}), 'q2');
  });

  test('hidden flag does not alter node traversal', () {
    final s = survey(
      [linearNode('q1'), linearNode('q2')],
      blocks: [block('q1', hidden: true), block('q2')],
    );
    expect(SurveyLogicHandler.firstNodeId(s, const {}), 'q1');
    expect(SurveyLogicHandler.isVisible(s.blocks.first, 'q1', const {}), isTrue);
  });
}
