package com.digia.engage.internal

internal data class VariableSchema(
    val name: String,
    val type: String,        // "string" | "number"
    val fallbackValue: String,
)

/** Normalises a raw JSON entry into a [VariableSchema]; null if it has no name. */
internal fun normalizeVariable(entry: org.json.JSONObject): VariableSchema? {
    val name = entry.optString("name").ifBlank { return null }
    val type = entry.optString("type").ifBlank { "string" }
    val fallbackValue = entry.optString("fallbackValue").ifBlank {
        entry.optString("sampleValue", "")
    }
    return VariableSchema(name = name, type = type, fallbackValue = fallbackValue)
}
