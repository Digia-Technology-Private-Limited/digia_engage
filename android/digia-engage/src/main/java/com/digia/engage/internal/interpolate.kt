package com.digia.engage.internal

private val PLACEHOLDER_PATTERN = Regex("""\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}""")

internal fun interpolate(text: String, variables: Map<String, String>?): String {
    if (variables.isNullOrEmpty()) return text
    return PLACEHOLDER_PATTERN.replace(text) { result ->
        variables[result.groupValues[1]] ?: ""
    }
}

@Suppress("UNCHECKED_CAST")
internal fun extractVariables(content: Map<String, Any?>): Map<String, String>? {
    val raw = content["variables"] ?: return null
    val map = (raw as? Map<*, *>) ?: return null
    val result = mutableMapOf<String, String>()
    for ((k, v) in map) {
        val key = k as? String ?: continue
        when (v) {
            is String -> result[key] = v
            is Number -> result[key] = v.toString()
            is Boolean -> result[key] = v.toString()
        }
    }
    return if (result.isNotEmpty()) result else null
}
