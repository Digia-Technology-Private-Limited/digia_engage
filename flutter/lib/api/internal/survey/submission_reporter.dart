import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../../models/digia_config.dart';
import '../campaign/campaign_model.dart';
import '../digia_endpoints.dart';
import 'survey_config.dart';
import 'survey_logic_handler.dart';

/// Posts a completed survey's answers to the Digia backend
/// (`POST {baseUrl}/api/v1/engage/sdk/recordSubmission`).
///
/// Dart port of the Android `SubmissionReporter`. Fire-and-forget: a failed
/// post is logged and swallowed so it never affects the UI flow. The wire
/// shape (block-type strings, value/valueLabel encoding, `computed.npsBucket`,
/// ISO-8601 `occurredAt`) is kept byte-for-byte compatible with Android so the
/// backend sees identical submissions across platforms.
class SubmissionReporter {
  final DigiaConfig config;
  final String deviceId;

  SubmissionReporter(this.config, this.deviceId);

  /// Builds and posts the submission body. [answers] are the structured,
  /// per-node answers; [startedAtMs] is when the survey first became visible.
  void reportSurveyCompleted(
    CampaignModel campaign,
    Map<String, SurveyAnswer> answers,
    int startedAtMs,
  ) {
    final config = campaign.config;
    if (config is! SurveyCampaignConfig) return;
    final body = _buildBody(campaign, config.surveyConfig, answers, startedAtMs);
    // Fire-and-forget; never block or surface errors to the survey flow.
    unawaited(_post(body));
  }

  Future<void> _post(Map<String, dynamic> body) async {
    final fullUrl = DigiaEndpoints.submission;
    try {
      final dio = Dio(BaseOptions(
        connectTimeout: const Duration(milliseconds: 10000),
        receiveTimeout: const Duration(milliseconds: 10000),
        headers: {
          'X-Digia-Project-Id': config.apiKey,
          'X-Digia-Device-Id': deviceId,
          Headers.contentTypeHeader: Headers.jsonContentType,
        },
        // Don't throw on non-2xx; we log the code ourselves.
        validateStatus: (_) => true,
      ));
      final response = await dio.post<dynamic>(fullUrl, data: body);
      final code = response.statusCode ?? 0;
      debugPrint('[SubmissionReporter] recordSubmission HTTP $code');
      if (code < 200 || code > 299) {
        debugPrint('[SubmissionReporter] error body: ${response.data}');
      }
    } catch (e) {
      debugPrint('[SubmissionReporter] post failed: $e');
    }
  }

  Map<String, dynamic> _buildBody(
    CampaignModel campaign,
    SurveyConfigModel survey,
    Map<String, SurveyAnswer> answers,
    int startedAtMs,
  ) {
    final now = DateTime.now().millisecondsSinceEpoch;

    final answeredNodes = survey.nodes.where((node) {
      final block = survey.blockFor(node);
      if (block == null) return false;
      return !block.type.isContent && answers[node.id]?.isAnswered == true;
    }).toList();

    final promptNodes = survey.nodes
        .where((node) => survey.blockFor(node)?.type.isContent == false)
        .toList();

    final responses = <Map<String, dynamic>>[];
    for (final node in answeredNodes) {
      final block = survey.blockFor(node);
      final answer = answers[node.id];
      if (block == null || answer == null) continue;
      responses.add(_buildResponse(block, answer));
    }

    final payload = <String, dynamic>{
      'templateVersion': 'v1',
      'completion': {
        'answeredCount': answeredNodes.length,
        'totalCount': promptNodes.length,
      },
      'responses': responses,
    };

    final computed = <String, dynamic>{'durationMs': now - startedAtMs};
    final bucket = _npsBucketOf(survey, answers);
    if (bucket != null) computed['npsBucket'] = bucket;

    return <String, dynamic>{
      'campaignId': campaign.id,
      'submissionKey': 'attempt-$now',
      'submissionType': 'survey',
      'payload': payload,
      'computed': computed,
      'occurredAt': _isoTimestamp(now),
    };
  }

  Map<String, dynamic> _buildResponse(SurveyBlock block, SurveyAnswer answer) {
    final obj = <String, dynamic>{
      'blockId': block.id,
      'blockType': _blockTypeWire(block.type),
      'title': block.title.text,
    };

    final type = block.type;
    if (type == SurveyBlockType.nps ||
        type == SurveyBlockType.rating ||
        type == SurveyBlockType.number) {
      final n = answer.asNumber();
      if (n != null && n == n.truncateToDouble()) {
        obj['value'] = n.toInt();
      } else if (n != null) {
        obj['value'] = n;
      } else {
        obj['value'] = answer.values.isNotEmpty ? answer.values.first : '';
      }
    } else if (type.isMultiSelect) {
      final values = <String>[];
      final labels = <String>[];
      for (final v in answer.values) {
        values.add(v);
        final match = block.options.where((o) => o.id == v);
        if (match.isNotEmpty) labels.add(match.first.label);
      }
      obj['value'] = values;
      if (labels.isNotEmpty) obj['valueLabel'] = labels;
    } else if (type.isChoice) {
      final v = answer.values.isNotEmpty ? answer.values.first : '';
      obj['value'] = v;
      final match = block.options.where((o) => o.id == v);
      if (match.isNotEmpty) obj['valueLabel'] = match.first.label;
    } else {
      obj['value'] = answer.values.isNotEmpty ? answer.values.first : '';
    }

    final comment = answer.comment;
    if (comment != null && comment.trim().isNotEmpty) {
      obj['comment'] = comment;
    }
    return obj;
  }

  /// Snake-case wire name matching Android's `enum.name.lowercase()`.
  String _blockTypeWire(SurveyBlockType type) => switch (type) {
        SurveyBlockType.singleSelect => 'single_select',
        SurveyBlockType.multiSelect => 'multi_select',
        SurveyBlockType.rating => 'rating',
        SurveyBlockType.nps => 'nps',
        SurveyBlockType.npsEmoji => 'nps_emoji',
        SurveyBlockType.npsSmiley => 'nps_smiley',
        SurveyBlockType.reaction => 'reaction',
        SurveyBlockType.thisOrThat => 'this_or_that',
        SurveyBlockType.tierList => 'tier_list',
        SurveyBlockType.upvote => 'upvote',
        SurveyBlockType.shortText => 'short_text',
        SurveyBlockType.longText => 'long_text',
        SurveyBlockType.number => 'number',
        SurveyBlockType.email => 'email',
        SurveyBlockType.date => 'date',
        SurveyBlockType.welcome => 'welcome',
        SurveyBlockType.textMedia => 'text_media',
        SurveyBlockType.resultPage => 'result_page',
      };

  String? _npsBucketOf(SurveyConfigModel survey, Map<String, SurveyAnswer> answers) {
    final npsNode = survey.nodes
        .where((n) => survey.blockFor(n)?.type == SurveyBlockType.nps)
        .toList();
    if (npsNode.isEmpty) return null;
    final score = answers[npsNode.first.id]?.asNumber()?.toInt();
    if (score == null) return null;
    if (score >= 9) return 'promoter';
    if (score >= 7) return 'passive';
    return 'detractor';
  }

  String _isoTimestamp(int ms) =>
      DateTime.fromMillisecondsSinceEpoch(ms, isUtc: true).toIso8601String();
}
