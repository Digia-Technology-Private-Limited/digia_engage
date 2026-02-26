package com.digia.digiaui.framework.appstate

import com.digia.digiaui.framework.utils.JsonLike
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.encodeToString


interface StateDescriptorParser<T> {
    fun parse(json: JsonLike): StateDescriptor<T>
}

class StateDescriptorFactory {
    private val parsers: Map<String, StateDescriptorParser<*>> = mapOf(
        "number" to NumDescriptorParser(),
        "string" to StringDescriptorParser(),
        "bool" to BoolDescriptorParser(),
        "json" to JsonDescriptorParser(),
        "list" to JsonArrayDescriptorParser()
    )

    private val typeAliases: Map<String, String> = mapOf(
        "boolean" to "bool",
        "numeric" to "number",
        "array" to "list"
    )

    fun fromJson(json: JsonLike): StateDescriptor<*> {
        val type = json["type"] as? String
        val parser = parsers[type] ?: parsers[typeAliases[type]]
        if (parser == null) {
            throw UnsupportedOperationException("Unknown state type: $type")
        }
        return parser.parse(json)
    }
}


class NumDescriptorParser : StateDescriptorParser<Number> {
    override fun parse(json: JsonLike): StateDescriptor<Number> {
        fun toNumber(value: Any?): Number {
            return when (value) {
                is Number -> value
                is String -> value.toDoubleOrNull() ?: 0
                else -> 0
            }
        }

        val key = json["name"] as String
        val initialValue = toNumber(json["value"])
        val shouldPersist = (json["shouldPersist"] as? Boolean) ?: false
        val streamName = json["streamName"] as String

        return StateDescriptor(
            key = key,
            initialValue = initialValue,
            shouldPersist = shouldPersist,
            deserialize = { s -> toNumber(s) },
            serialize = { v -> v.toString() },
            description = "number",
            streamName = streamName
        )
    }
}

class StringDescriptorParser : StateDescriptorParser<String> {
    override fun parse(json: JsonLike): StateDescriptor<String> {
        val key = json["name"] as String
        val initialValue = json["value"]?.toString() ?: ""
        val shouldPersist = (json["shouldPersist"] as? Boolean) ?: false
        val streamName = json["streamName"] as String

        return StateDescriptor(
            key = key,
            initialValue = initialValue,
            shouldPersist = shouldPersist,
            deserialize = { s -> s?.toString() ?: "" },
            serialize = { v -> v },
            description = "string",
            streamName = streamName
        )
    }
}

class BoolDescriptorParser : StateDescriptorParser<Boolean> {
    override fun parse(json: JsonLike): StateDescriptor<Boolean> {
        fun parseBool(value: Any?): Boolean {
            return when (value) {
                is Number -> value.toInt() != 0
                is Boolean -> value
                is String -> value.toBoolean()
                else -> false
            }
        }

        val key = json["name"] as String
        val initialValue = parseBool(json["value"])
        val shouldPersist = (json["shouldPersist"] as? Boolean) ?: false
        val streamName = json["streamName"] as String

        return StateDescriptor(
            key = key,
            initialValue = initialValue,
            shouldPersist = shouldPersist,
            deserialize = { s -> parseBool(s) },
            serialize = { v -> v.toString() },
            description = "bool",
            streamName = streamName
        )
    }
}

class JsonDescriptorParser : StateDescriptorParser<JsonLike> {
    override fun parse(json: JsonLike): StateDescriptor<JsonLike> {
        fun parseJson(value: Any?): JsonLike {
            return when (value) {
                is Map<*, *> -> value as? JsonLike ?: emptyMap()
                else -> emptyMap()
            }
        }

        val key = json["name"] as String
        val initialValue = parseJson(json["value"])
        val shouldPersist = (json["shouldPersist"] as? Boolean) ?: false
        val streamName = json["streamName"] as String

        return StateDescriptor(
            key = key,
            initialValue = initialValue,
            shouldPersist = shouldPersist,
            deserialize = { s -> parseJson(s) },
            serialize = { v -> Json.encodeToString(v) },
            description = "json",
            streamName = streamName
        )
    }
}

class JsonArrayDescriptorParser : StateDescriptorParser<List<Any?>> {
    override fun parse(json: JsonLike): StateDescriptor<List<Any?>> {
        fun parseList(value: Any?): List<Any?> {
            return when (value) {
                is List<*> -> value
                else -> emptyList()
            }
        }

        val key = json["name"] as String
        val initialValue = parseList(json["value"])
        val shouldPersist = (json["shouldPersist"] as? Boolean) ?: false
        val streamName = json["streamName"] as String

        return StateDescriptor(
            key = key,
            initialValue = initialValue,
            shouldPersist = shouldPersist,
            deserialize = { s -> parseList(s) },
            serialize = { v -> Json.encodeToString(v) },
            description = "list",
            streamName = streamName
        )
    }
}