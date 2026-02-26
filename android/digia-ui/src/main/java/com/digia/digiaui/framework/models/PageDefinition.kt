package com.digia.digiaui.framework.models

import com.digia.digiaui.utils.asSafe
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.datatype.Variable
import com.digia.digiaui.framework.datatype.VariableConverter
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.JsonUtil.Companion.tryKeys

/** Page definition - complete configuration for a page Mirrors Flutter DUIPageDefinition */
data class PageDefinition(
    val pageId: String,
    val pageArgDefs: Map<String, Variable>? = null, // Input argument definitions
    val initStateDefs: Map<String, Variable>? = null, // Initial state variables
    val layout: PageLayout? = null, // Widget tree layout
    val onPageLoad: ActionFlow? = null, // Actions to run on load
    val onBackPress: ActionFlow? = null // Actions on back button
) {
    companion object {
        fun fromJson(json: JsonLike): PageDefinition {
            val actions : JsonLike? = asSafe<JsonLike>((json["actions"]))
            return PageDefinition(
                    pageId = json["pageId"] as? String
                                    ?: json["uid"] as? String ?: json["pageUid"] as? String ?: "",
                    pageArgDefs =tryKeys(json, listOf("pageArgDefs","inputArgs","argDefs"), parse = {
                            it ->asSafe<JsonLike>(it).let { VariableConverter.fromJson(it) }
                    })
                          ,
                    initStateDefs =tryKeys(json, listOf("initStateDefs","variables"), parse = {
                            it ->asSafe<JsonLike>(it).let { VariableConverter.fromJson(it) }
                    }),
                 layout = asSafe<JsonLike>(json["layout"])?.let { PageLayout.fromJson(it) },
                    onPageLoad =
                      asSafe<JsonLike>(actions?.get("onPageLoadAction") )
                                    ?.let { ActionFlow.fromJson(it) },
                    onBackPress =
                        asSafe<JsonLike>(actions?.get("onBackPress") )
                            ?.let { ActionFlow.fromJson(it) },
            )
        }

    }
}

/** Page layout wrapper */
data class PageLayout(val root: VWData?) {
    companion object {
        fun fromJson(json: JsonLike): PageLayout {
            return PageLayout(root = asSafe<JsonLike>(json["root"])?.let { VWData.fromJson(it) })
        }
    }
}
