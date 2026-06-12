package com.digia.engage.internal.analytics

import org.json.JSONArray
import org.json.JSONObject

internal data class QueueEntry(
    val eventId: String,
    val payload: Map<String, Any?>,
    val createdAt: Long,
    val attempts: Int = 0,
)

internal class AnalyticsQueue(private val store: KeyValueStore) {

    fun append(entry: QueueEntry, maxEvents: Int) {
        val current = load().toMutableList()
        current.add(entry)
        while (current.size > maxEvents) current.removeAt(0)
        save(current)
    }

    fun peek(maxCount: Int): List<QueueEntry> = load().take(maxCount)

    fun remove(eventIds: List<String>) {
        val ids = eventIds.toSet()
        save(load().filter { it.eventId !in ids })
    }

    fun incrementAttempt(eventIds: List<String>) {
        val ids = eventIds.toSet()
        save(load().map { if (it.eventId in ids) it.copy(attempts = it.attempts + 1) else it })
    }

    fun size(): Int = load().size

    fun clear() = store.remove(KEY_QUEUE)

    private fun load(): List<QueueEntry> {
        val json = store.getString(KEY_QUEUE, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                QueueEntry(
                    eventId = obj.getString("event_id"),
                    payload = jsonToMap(obj.getJSONObject("payload")),
                    createdAt = obj.getLong("created_at"),
                    attempts = obj.optInt("attempts", 0),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(entries: List<QueueEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject().apply {
                    put("event_id", e.eventId)
                    put("payload", mapToJson(e.payload))
                    put("created_at", e.createdAt)
                    put("attempts", e.attempts)
                },
            )
        }
        store.putString(KEY_QUEUE, arr.toString())
    }

    companion object {
        private const val KEY_QUEUE = "digia_analytics_queue"

        internal fun mapToJson(map: Map<String, Any?>): JSONObject {
            val obj = JSONObject()
            for ((k, v) in map) {
                when (v) {
                    null -> { /* omit nulls */ }
                    is Map<*, *> -> @Suppress("UNCHECKED_CAST")
                        obj.put(k, mapToJson(v as Map<String, Any?>))
                    else -> obj.put(k, v)
                }
            }
            return obj
        }

        internal fun jsonToMap(obj: JSONObject): Map<String, Any?> =
            obj.keys().asSequence().associateWith { key ->
                when (val v = obj.get(key)) {
                    JSONObject.NULL -> null
                    is JSONObject -> jsonToMap(v)
                    else -> v
                }
            }
    }
}
