import 'package:flutter/foundation.dart';

import 'survey_config.dart';
import 'survey_logic_handler.dart';

/// Holds the in-progress state of one survey showing: the answers collected so
/// far, the position in the (possibly branching) node graph, and the back-stack
/// for the Back button.
///
/// A [ChangeNotifier] port of the Android `SurveyViewModel`. Lives for the
/// lifetime of one showing so answers survive rebuilds.
class SurveyController extends ChangeNotifier {
  final SurveyConfigModel survey;

  /// nodeId → answer.
  final Map<String, SurveyAnswer> answers = {};

  final List<String> _backStack = [];

  late String _currentNodeId;
  late bool _isComplete;
  String? _redirectUrl;

  SurveyController(this.survey) {
    _currentNodeId = SurveyLogicHandler.firstNodeId(survey, const {});
    _isComplete = _currentNodeId == surveyFinished;
  }

  String get currentNodeId => _currentNodeId;
  bool get isComplete => _isComplete;
  String? get redirectUrl => _redirectUrl;

  SurveyNode? get currentNode => survey.nodeById(_currentNodeId);

  SurveyBlock? get currentBlock {
    final node = currentNode;
    return node == null ? null : survey.blockFor(node);
  }

  bool get canGoBack =>
      _backStack.isNotEmpty && survey.settings.pagination.backButton;

  /// Respondent traversal depth (1-based) — the number of nodes reached so far,
  /// accounting for back navigation. Mirrors Android's `SurveyViewModel`
  /// `progressStep` and is reported as analytics `item_index`.
  int get progressStep => _backStack.length + 1;

  /// Coarse progress estimate based on traversal depth, not graph topology.
  double get progress {
    if (survey.nodes.isEmpty) return 0;
    return ((_backStack.length + 1) / survey.nodes.length).clamp(0.0, 1.0);
  }

  /// Whether the current node may be left — required questions must be answered.
  bool canAdvance() {
    final block = currentBlock;
    if (block == null) return false;
    if (block.type.isContent) return true;
    if (!block.required) return true;
    return answers[_currentNodeId]?.isAnswered == true;
  }

  void setAnswer(String nodeId, SurveyAnswer answer) {
    answers[nodeId] = answer;
    notifyListeners();
  }

  bool nextBlockIsResultPage() {
    if (_isComplete || _currentNodeId == surveyFinished) return false;
    final navigation =
        SurveyLogicHandler.nextStep(survey, _currentNodeId, answers);
    final nextNode = survey.nodeById(navigation.nextNodeId);
    if (nextNode == null) return false;
    return survey.blockFor(nextNode)?.type == SurveyBlockType.resultPage;
  }

  /// Records the current answer and moves to the branching-decided next node.
  void advance() {
    if (_isComplete) return;
    final from = _currentNodeId;
    if (from == surveyFinished) return;
    final navigation = SurveyLogicHandler.nextStep(survey, from, answers);
    _backStack.add(from);
    _redirectUrl = navigation.redirectUrl;
    if (navigation.nextNodeId == surveyFinished) {
      _isComplete = true;
    } else {
      _currentNodeId = navigation.nextNodeId;
    }
    notifyListeners();
  }

  void back() {
    if (_backStack.isEmpty) return;
    _currentNodeId = _backStack.removeLast();
    _isComplete = false;
    notifyListeners();
  }

  /// The collected answers as a serialisable map, for the `Completed` event.
  Map<String, dynamic> responsePayload() =>
      answers.map((key, answer) => MapEntry(key, answer.toMap()));
}
