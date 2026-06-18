import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../../models/digia_config.dart';
import '../digia_endpoints.dart';
import 'campaign_model.dart';

/// Fetches campaign configurations from the Digia backend during SDK init.
///
/// Dart port of the Android `CampaignFetcher`. Posts to
/// `{baseUrl}/api/v1/engage/sdk/getCampaigns` with the project id and a
/// device id, then parses the response array into [CampaignModel]s.
class CampaignFetcher {
  final DigiaConfig config;
  final String deviceId;

  CampaignFetcher(this.config, this.deviceId);

  Future<List<CampaignModel>> fetch() async {
    final fullUrl = DigiaEndpoints.campaigns;

    final dio = Dio(BaseOptions(
      connectTimeout: const Duration(milliseconds: 10000),
      receiveTimeout: const Duration(milliseconds: 10000),
      headers: {
        'X-Digia-Project-Id': config.apiKey,
        'X-Digia-Device-Id': deviceId,
        Headers.contentTypeHeader: Headers.jsonContentType,
      },
    ));

    final response = await dio.post<dynamic>(fullUrl, data: '{}');
    final code = response.statusCode ?? 0;
    if (code != 200) {
      throw Exception('getCampaigns failed: HTTP $code');
    }

    return _parseCampaigns(_extractCampaignArray(response.data));
  }

  /// Unwraps the campaign array from the response, accepting either a bare
  /// array, `{ data: { response: [...] } }`, or `{ response: [...] }`.
  List<dynamic> _extractCampaignArray(dynamic body) {
    if (body is List) return body;

    if (body is Map) {
      final map = body.cast<String, dynamic>();
      final data = map['data'];
      if (data is Map) {
        final response = data['response'];
        if (response is List) return response;
      }
      final response = map['response'];
      if (response is List) return response;
    }

    throw Exception('getCampaigns response missing data.response');
  }

  List<CampaignModel> _parseCampaigns(List<dynamic> arr) {
    final result = <CampaignModel>[];
    for (final raw in arr) {
      if (raw is! Map) continue;
      try {
        final campaign = CampaignModel.fromJson(raw.cast<String, dynamic>());
        if (campaign != null) result.add(campaign);
      } catch (e) {
        // Skip a single malformed campaign rather than failing the whole fetch.
        debugPrint('[CampaignFetcher] skipped malformed campaign: $e');
      }
    }
    return result;
  }
}
