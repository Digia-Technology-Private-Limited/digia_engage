package com.digia.digiaui.framework.models

import com.digia.digiaui.utils.asSafe
import com.digia.digiaui.framework.datatype.Variable
import com.digia.digiaui.framework.datatype.VariableConverter
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.JsonUtil.Companion.tryKeys

enum class NodeType {
    Widget,
    State,
    Component;

    companion object {
        fun fromString(value: String): NodeType? {
            return NodeType.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}

/**
 * Virtual Widget Data - sealed class representing different types of widget nodes Mirrors the
 * Flutter VWData structure
 */
sealed class VWData {
    abstract val refName: String?

    companion object {
        fun fromJson(json: JsonLike): VWData {
            val nodeType =
                NodeType.fromString(asSafe<String>(tryKeys(json, listOf("category", "nodeType")))?:"widget")
                    ?: NodeType.Widget

            return when (nodeType) {
                NodeType.Widget -> VWNodeData.fromJson(json)
                NodeType.Component -> VWComponentData.fromJson(json)
                NodeType.State -> VWStateData.fromJson(json)
            }
        }
    }
}

/** Regular widget node (Text, Button, Container, etc.) */
data class VWNodeData(
    override val refName: String? = null,
    val type: String, // Widget type (e.g., "digia/text")
    val props: Props = Props.empty(), // Widget-specific properties
    val commonProps: CommonProps? = null, // Common properties
    val parentProps: Props? = null, // Props for parent container
    val childGroups: Map<String, List<VWData>>? = null // Child widgets
//    val repeatData: VWRepeatData? = null
) : VWData() {
    companion object {
        fun fromJson(json: JsonLike): VWNodeData {
            return VWNodeData(
                refName = tryKeys<String>(json, listOf("varName", "refName")),
                type = json["type"] as? String ?: "",
                props = asSafe<JsonLike>(json["props"])?.let(::Props) ?: Props.empty(),
                commonProps = asSafe<JsonLike>(json["containerProps"])?.let {
                    CommonProps.fromJson(it)
                },
                parentProps = asSafe<JsonLike>(json["parentProps"])?.let(::Props) ?: Props.empty(),
                childGroups = tryKeys(
                    json,
                    listOf("childGroups", "children", "composites"),
                    VWNodeData::parseChildGroups
                ),
            )
        }

        internal fun parseChildGroups(value: Any?): Map<String, List<VWData>>? {
            if (value !is Map<*, *>) return null

            return value.entries.associate { (key, children) ->
                val childList =
                    when (children) {
                        is List<*> ->
                            children.mapNotNull { it ->
                                asSafe<JsonLike>(it)?.let {
                                    VWData.fromJson(
                                        it
                                    )
                                }
                            }

                        else -> emptyList()
                    }
                (key as String) to childList
            }
        }
    }
}

/** Component reference node */
data class VWComponentData(
    override val refName: String? = null,
    val id: String, // Component ID
    val args: Map<String, ExprOr<Any>?>? = null, // Arguments to pass
    val commonProps: CommonProps? = null,
    val parentProps: Props? = null
) : VWData() {
    companion object {
        fun fromJson(json: JsonLike): VWComponentData {
            return VWComponentData(
                refName = tryKeys(json, listOf("varName", "refName")),
                id = json["componentId"] as? String ?: "",
                args =
                    asSafe<JsonLike>(json["componentArgs"])?.mapValues {
                        ExprOr.fromValue<Any>(it.value)
                    },
                commonProps = CommonProps.fromJson(asSafe<JsonLike>(json["containerProps"])),
                parentProps = asSafe<JsonLike>(json["parentProps"])?.let(::Props) ?: Props.empty()
            )
        }
    }
}

/** State container node */
data class VWStateData(
    override val refName: String? = null,
    val initStateDefs: Map<String, Variable>, // State variables
    val childGroups: Map<String, List<VWData>>? = null,
    val parentProps: Props? = null
) : VWData() {
    companion object {
        fun fromJson(json: JsonLike): VWStateData {
            return VWStateData(
                refName = tryKeys(json, listOf("varName", "refName")),
                initStateDefs = tryKeys(json, listOf("initStateDefs"), parse = { it ->
                    asSafe<JsonLike>(it).let { VariableConverter.fromJson(it) }
                }) ?: emptyMap(),
                childGroups = VWNodeData.parseChildGroups(
                    tryKeys(
                        json,
                        listOf("childGroups", "children", "composites")
                    )
                ),
                parentProps = asSafe<JsonLike>(json["parentProps"])?.let(::Props) ?: Props.empty()
            )
        }


    }
}

