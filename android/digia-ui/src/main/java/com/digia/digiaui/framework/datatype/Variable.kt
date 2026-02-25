package com.digia.digiaui.framework.datatype

import com.digia.digiaui.framework.utils.JsonUtil.Companion.tryKeys
import kotlin.collections.Map

data class Variable(
    val type: DataType,
    val name: String,
    val defaultValue: Any? = null
) {
    companion object {
        fun fromJson(json: Map<String, Any?>?): Variable? {
            if (json == null) return null

            val type = DataType.fromString(json["type"] as? String)
            val name = json["name"] as? String

            if (type == null || name == null) return null

            return Variable(
                name = name,
                type = type,
                defaultValue = tryKeys(json, listOf("default", "defaultValue"))
            )
        }
    }

    fun toJson(): Map<String, Any?> {
        return mapOf(
            "type" to type.id,
            "name" to name,
            "default" to defaultValue
        )
    }

    fun copyWith(
        type: DataType? = null,
        name: String? = null,
        defaultValue: Any? = null
    ): Variable {
        return Variable(
            type = type ?: this.type,
            name = name ?: this.name,
            defaultValue = defaultValue ?: this.defaultValue
        )
    }
}