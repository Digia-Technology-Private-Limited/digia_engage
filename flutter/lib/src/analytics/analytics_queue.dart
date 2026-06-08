import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import '../preferences_store.dart';

const _kAnalyticsQueueKey = 'digia_analytics.event_queue';

/// Represents a single queued analytics event
class AnalyticsQueueEntry {
  final String eventId;
  final Map<String, dynamic> payload;
  final int createdAt;
  final int attempt;

  AnalyticsQueueEntry({
    required this.eventId,
    required this.payload,
    required this.createdAt,
    required this.attempt,
  });

  factory AnalyticsQueueEntry.fromJson(Map<String, dynamic> json) {
    return AnalyticsQueueEntry(
      eventId: json['event_id'] as String,
      payload: Map<String, dynamic>.from(json['payload'] as Map),
      createdAt: json['created_at'] as int,
      attempt: json['attempt'] as int,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'event_id': eventId,
      'payload': payload,
      'created_at': createdAt,
      'attempt': attempt,
    };
  }
}

/// Manages persistent queue of analytics events
class AnalyticsQueue {
  Future<List<AnalyticsQueueEntry>> _loadEntries() async {
    final raw =
        PreferencesStore.instance.read<String>(_kAnalyticsQueueKey, '[]');
    if (raw == null || raw.isEmpty) {
      return <AnalyticsQueueEntry>[];
    }

    try {
      final decoded = jsonDecode(raw) as List<dynamic>;
      return decoded
          .whereType<Map<String, dynamic>>()
          .map((json) => AnalyticsQueueEntry.fromJson(json))
          .toList();
    } catch (_) {
      return <AnalyticsQueueEntry>[];
    }
  }

  Future<void> _saveEntries(List<AnalyticsQueueEntry> entries) async {
    final encoded = jsonEncode(entries.map((e) => e.toJson()).toList());
    await PreferencesStore.instance.write<String>(_kAnalyticsQueueKey, encoded);
  }

  Future<void> appendEvent(
    Map<String, dynamic> payload,
    int maxQueueSize,
  ) async {
    try {
      final entries = await _loadEntries();
      final now = DateTime.now().millisecondsSinceEpoch;
      final eventId = payload['event_id'] as String? ?? const Uuid().v4();
      entries.add(AnalyticsQueueEntry(
        eventId: eventId,
        payload: payload,
        createdAt: now,
        attempt: 0,
      ));

      final overflow = entries.length - maxQueueSize;
      if (overflow > 0) {
        final dropped = entries.take(overflow).map((e) => e.eventId).toList();
        entries.removeRange(0, overflow);
        debugPrint(
          '[Digia Analytics] Queue exceeded $maxQueueSize events. Dropping ${dropped.length} oldest event(s).',
        );
      }

      await _saveEntries(entries);
    } catch (error) {
      debugPrint('[Digia Analytics] appendEvent failed: $error');
    }
  }

  Future<List<AnalyticsQueueEntry>> peek(int count) async {
    try {
      final entries = await _loadEntries();
      if (entries.length <= count) {
        return entries;
      }
      return entries.sublist(0, count);
    } catch (_) {
      return const [];
    }
  }

  Future<void> removeByEventIds(List<String> eventIds) async {
    try {
      final entries = await _loadEntries();
      final filtered = entries
          .where((entry) => !eventIds.contains(entry.eventId))
          .toList(growable: false);
      await _saveEntries(filtered);
    } catch (error) {
      debugPrint('[Digia Analytics] removeByEventIds failed: $error');
    }
  }

  Future<void> incrementAttempt(List<String> eventIds) async {
    try {
      final entries = await _loadEntries();
      final updated = entries.map((entry) {
        if (eventIds.contains(entry.eventId)) {
          return AnalyticsQueueEntry(
            eventId: entry.eventId,
            payload: entry.payload,
            createdAt: entry.createdAt,
            attempt: entry.attempt + 1,
          );
        }
        return entry;
      }).toList(growable: false);
      await _saveEntries(updated);
    } catch (error) {
      debugPrint('[Digia Analytics] incrementAttempt failed: $error');
    }
  }

  Future<int> length() async {
    final entries = await _loadEntries();
    return entries.length;
  }
}
