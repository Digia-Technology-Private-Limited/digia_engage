package com.digia.digiaui.framework.appstate



import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.expr.ScopeContext
import kotlin.collections.Map


class AppStateScopeContext(
    private val values: Map<String, ReactiveValue<Any?>>,
    variables: Map<String, Any?> = emptyMap(),
    enclosing: ScopeContext? = null
) : DefaultScopeContext(
    name = "appState",
    variables = variables,
    enclosing = enclosing
) {

    override fun getValue(key: String): Pair<Boolean, Any?> {

        // appState → expose full map
        if (key == "appState") {
            val stateMap = mutableMapOf<String, Any?>()

            values.forEach { (k, v) ->
                stateMap[k] = v.value
                stateMap[v.streamName] = v.flow   // Flow instead of Stream
            }

            return Pair(true, stateMap)
        }

        // direct value access
        values[key]?.let {
            return Pair(true, it.value)
        }

        if (values.values.any { it.streamName == key }) {
            return Pair(true, values.values.first { it.streamName == key }.flow)
        }

        return super.getValue(key)
    }
}
