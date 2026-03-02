/// Plugin health report returned by [DigiaCEPPlugin.healthCheck].
class DiagnosticReport {
  const DiagnosticReport({
    required this.isHealthy,
    this.issue,
    this.resolution,
    this.metadata = const <String, dynamic>{},
  });

  final bool isHealthy;
  final String? issue;
  final String? resolution;
  final Map<String, dynamic> metadata;
}
