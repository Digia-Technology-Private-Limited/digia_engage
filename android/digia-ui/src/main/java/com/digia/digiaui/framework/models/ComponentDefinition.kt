package com.digia.digiaui.framework.models

import com.digia.digiaui.framework.datatype.Variable
import com.digia.digiaui.framework.datatype.VariableConverter
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.JsonUtil.Companion.tryKeys
import com.digia.digiaui.utils.asSafe


/**
 * Component definition - reusable UI component configuration Mirrors Flutter DUIComponentDefinition
 */
data class ComponentDefinition(
    val id: String,
    val argDefs: Map<String, Variable>? = null, // Component arguments
    val initStateDefs: Map<String, Variable>? = null, // Initial state
    val layout: ComponentLayout? = null // Widget tree
) {
    companion object {
        fun fromJson(json: JsonLike): ComponentDefinition {
            return ComponentDefinition(
                    id = json["id"] as? String
                                    ?: json["uid"] as? String ?: json["componentId"] as? String
                                            ?: "",
                    argDefs = tryKeys(json, listOf("argDefs"), parse = {
it ->asSafe<JsonLike>(it).let { VariableConverter.fromJson(it) }
                    }),
                    initStateDefs = tryKeys(json, listOf("initStateDefs"), parse = {
                            it ->asSafe<JsonLike>(it).let { VariableConverter.fromJson(it) }
                    }),
                    layout = asSafe<JsonLike>(json["layout"])?.let { ComponentLayout.fromJson(it) }
            )
        }
    }
}

/** Component layout wrapper */
data class ComponentLayout(val root: VWData?) {
    companion object {
        fun fromJson(json: JsonLike): ComponentLayout {
            return ComponentLayout(root = asSafe<JsonLike>(json["root"])?.let { VWData.fromJson(it) })
        }
    }
}
