package com.digia.digiaui.framework.models

import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.NumUtil
import com.digia.digiaui.framework.utils.valueFor


class Props(val value: JsonLike) {

    fun get(keyPath: String?): Any? {
        if (keyPath == null) return null
        return value.valueFor(keyPath)
    }

    fun getString(keyPath: String): String? = get(keyPath) as? String

    fun getInt(keyPath: String): Int? = NumUtil.toInt(get(keyPath))

    fun getDouble(keyPath: String): Double? = NumUtil.toDouble(get(keyPath))

    fun getBool(keyPath: String): Boolean? = NumUtil.toBool(get(keyPath))

    fun getMap(keyPath: String): JsonLike? = get(keyPath) as? JsonLike

    fun getList(keyPath: String): List<Any?>? = get(keyPath) as? List<Any?>

    fun toProps(keyPath: String): Props? {
        val result = getMap(keyPath)
        return if (result == null) null else Props(result)
    }

    val isEmpty: Boolean
        get() = value.isEmpty()

    val isNotEmpty: Boolean
        get() = value.isNotEmpty()

    companion object {
        fun empty(): Props = Props(mapOf<String, Any?>())
    }
}


