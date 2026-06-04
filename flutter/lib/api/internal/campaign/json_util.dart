/// Safe JSON readers mirroring Android's `JSONObject.optX` helpers.
///
/// The campaign config arrives as untyped `Map<String, dynamic>` decoded from
/// the backend. These helpers coerce values defensively so a malformed field
/// never throws — matching the lenient parsing behaviour of the Android SDK.
library;

String optString(Map<String, dynamic> json, String key, [String fallback = '']) {
  final value = json[key];
  if (value == null) return fallback;
  if (value is String) return value;
  return value.toString();
}

int optInt(Map<String, dynamic> json, String key, int fallback) {
  final value = json[key];
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value) ?? fallback;
  return fallback;
}

double optDouble(Map<String, dynamic> json, String key, double fallback) {
  final value = json[key];
  if (value is num) return value.toDouble();
  if (value is String) return double.tryParse(value) ?? fallback;
  return fallback;
}

bool optBool(Map<String, dynamic> json, String key, bool fallback) {
  final value = json[key];
  if (value is bool) return value;
  if (value is String) {
    if (value.toLowerCase() == 'true') return true;
    if (value.toLowerCase() == 'false') return false;
  }
  return fallback;
}

Map<String, dynamic>? optMap(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is Map) return value.cast<String, dynamic>();
  return null;
}

List<dynamic>? optList(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is List) return value;
  return null;
}
