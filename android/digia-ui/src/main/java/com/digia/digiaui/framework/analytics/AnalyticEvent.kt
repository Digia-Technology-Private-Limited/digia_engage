package com.digia.digiaui.framework.analytics

import com.digia.digiaui.framework.utils.JsonLike

/**
 * Represents an analytics event that can be tracked
 *
 * @param name The name of the event
 * @param payload Optional key-value pairs containing event data
 */
data class AnalyticEvent(
    val name: String,
    val payload: Map<String, Any?>? = null
) {
    fun toJson(): JsonLike =
        mapOf(
            "name" to name,
            "payload" to payload
        )

    companion object {
        fun fromJson(json: JsonLike): AnalyticEvent {
            return AnalyticEvent(
                name = json["name"] as String,
                payload = json["payload"] as? Map<String, Any?>
            )
        }
    }
}
