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

  const DigiaAnalyticsConfig({
    this.enabled = true,
    this.flushIntervalMs = 30000,
    this.flushBatchSize = 50,
    this.maxBatchSize = 200,
    this.queueMaxEvents = 5000,
    this.sessionTimeoutMs = 30 * 60 * 1000,
  });
}
