package com.digia.engage.internal

// ── Regex patterns ────────────────────────────────────────────────────────────

private val PLACEHOLDER_PATTERN = Regex("""\{\{([^}]*)\}\}""")
private val PLAIN_IDENT_REGEX   = Regex("""^[a-z][a-z0-9_]*$""")

// ── Public interpolate overloads ──────────────────────────────────────────────

/** Legacy overload — kept for backward compatibility. */
internal fun interpolate(text: String, variables: Map<String, String>?): String {
    if (!text.contains("{{")) return text
    return PLACEHOLDER_PATTERN.replace(text) { result ->
        val inner = result.groupValues[1].trim()
        variables?.get(inner) ?: ""
    }
}

/** New overload — uses VariableContext for arithmetic + fallback support. */
internal fun interpolate(text: String, context: VariableContext?): String {
    if (!text.contains("{{")) return text
    val ctx = context ?: VariableContext.empty
    return PLACEHOLDER_PATTERN.replace(text) { result ->
        val inner = result.groupValues[1].trim()
        if (PLAIN_IDENT_REGEX.matches(inner)) {
            ctx.values[inner] ?: ""          // plain substitution
        } else {
            evaluateArithmetic(inner, ctx) ?: ""  // arithmetic
        }
    }
}
