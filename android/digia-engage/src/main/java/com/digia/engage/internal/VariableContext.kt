package com.digia.engage.internal

internal data class VariableContext(
    val values: Map<String, String>,  // pre-resolved: CEP → fallback → ""
    val types: Map<String, String>,   // name → "string"|"number"
) {
    companion object {
        val empty = VariableContext(emptyMap(), emptyMap())
    }
}

/** Builds a [VariableContext] from schemas, letting non-empty CEP values win. */
internal fun buildVariableContext(
    schemas: List<VariableSchema>,
    cepVars: Map<String, String>?,
): VariableContext {
    val values = mutableMapOf<String, String>()
    val types  = mutableMapOf<String, String>()
    for (schema in schemas) {
        val cepVal = cepVars?.get(schema.name)
        values[schema.name] = if (!cepVal.isNullOrEmpty()) cepVal else schema.fallbackValue
        types[schema.name]  = schema.type
    }
    return VariableContext(values = values, types = types)
}
