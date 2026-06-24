package com.digia.engage.internal

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class InterpolateTest {
    @Test
    fun goldenVectors() {
        val json = File("src/test/resources/expr-test-vectors.json").readText()
        val vectors = JSONArray(json)
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val expr = v.getString("expr")
            val out = v.getString("out")
            val vars = v.getJSONObject("vars")
            val fallbackMap = if (vars.has("_fallback")) {
                val fb = vars.getJSONObject("_fallback")
                fb.keys().asSequence().associateWith { fb.getString(it) }
            } else emptyMap()
            val schemas = mutableListOf<VariableSchema>()
            val cepVars = mutableMapOf<String, String>()
            for (name in vars.keys()) {
                if (name == "_fallback") continue
                val entry = vars.getJSONObject(name)
                val type = entry.optString("type", "string")
                val fb = fallbackMap[name] ?: ""
                schemas.add(VariableSchema(name, type, fb))
                if (entry.has("value")) cepVars[name] = entry.getString("value")
            }
            val context = buildVariableContext(schemas, cepVars)
            val result = interpolate("{{ $expr }}", context)
            assertEquals("vector[$i] '${v.optString("description", expr)}'", out, result)
        }
    }
}
