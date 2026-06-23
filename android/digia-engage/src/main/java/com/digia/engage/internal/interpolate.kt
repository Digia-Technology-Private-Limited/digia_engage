package com.digia.engage.internal

private val PLACEHOLDER_PATTERN = Regex("""\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}""")

internal fun interpolate(text: String, variables: Map<String, String>?): String {
    if (variables.isNullOrEmpty()) return text
    return PLACEHOLDER_PATTERN.replace(text) { result ->
        variables[result.groupValues[1]] ?: ""
    }
}
