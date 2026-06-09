/// Configuration for Digia analytics collection and dispatch.
class DigiaAnalyticsConfig {
  /// Whether analytics collection is enabled.
  final bool enabled;

  /// Flush interval in milliseconds for low-volume queues.
  final int flushIntervalMs;

  /// Number of queued events required to flush immediately.
  final int flushBatchSize;

  /// Maximum number of events sent in one batch.
  final int maxBatchSize;

  /// Maximum number of events stored in the local queue.
  final int queueMaxEvents;

  /// Session idle timeout in milliseconds.
  final int sessionTimeoutMs;

  /// Analytics endpoint URL.
  final String endpointUrl;

  const DigiaAnalyticsConfig({
    this.enabled = true,
    this.flushIntervalMs = 5000,
    this.flushBatchSize = 10,
    this.maxBatchSize = 100,
    this.queueMaxEvents = 5000,
    this.sessionTimeoutMs = 30 * 60 * 1000,
    this.endpointUrl = 'https://app.digia.tech/api/v1/engage/sdk/track',
  });
}
