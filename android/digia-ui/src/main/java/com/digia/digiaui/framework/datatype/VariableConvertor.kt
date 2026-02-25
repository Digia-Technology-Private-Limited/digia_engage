package com.digia.digiaui.framework.datatype

import com.digia.digiaui.framework.utils.JsonLike
import kotlin.collections.Map
import kotlin.collections.mutableMapOf

object VariableConverter {

    fun fromJson(json: Map<String, Any?>?): Map<String, Variable> {
        if (json == null) return emptyMap()

        val result = mutableMapOf<String, Variable>()
        for ((key, value) in json) {
            val mapValue = value as? JsonLike
            val v = Variable.fromJson(mapOf("name" to key) + (mapValue ?: emptyMap()))
            if (v != null) {
                result[key] = v
            }
        }
        return result
    }

    fun toJson(obj: Map<String, Variable>?): Map<String, Any?> {
        if (obj == null) return emptyMap()

        val result = mutableMapOf<String, Any?>()
        for ((key, value) in obj) {
            result[key] = value.toJson()
        }
        return result
    }
}