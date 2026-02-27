package com.digia.digiaui.framework.models

import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.JsonUtil.Companion.tryKeys
import com.digia.digiaui.utils.asType

data class CommonStyle(
        val padding: Any? = null,
        val margin: Any? = null,
        val bgColor: ExprOr<String>? = null,
        val border: JsonLike? = null,
        val borderRadius: Any? = null,
        val height: String? = null,
        val width: String? = null,
        val clipBehavior: String? = null
) {
    companion object {
        fun fromJson(json: JsonLike): CommonStyle {
            return CommonStyle(
                    padding = json["padding"],
                    margin = json["margin"],
                    bgColor =
                            tryKeys(json, listOf("bgColor", "backgroundColor")) { v ->
                                ExprOr.fromJson<String>(v)
                            },
                    borderRadius = tryKeys(json, listOf("borderRadius", "border.borderRadius")),
                    // Backward compatibility:
                    // If "border" exists → use it
                    // else → use full json (same as Dart)
                    border = asType<JsonLike>(json["border"]) ?: json,
                    height = asType(json["height"]),
                    width = asType(json["width"]),
                    clipBehavior = asType(json["clipBehavior"])
            )
        }
    }
}


data class CommonProps(
    val visibility: ExprOr<Boolean>?,
    val align: String?,
    val style: CommonStyle?,
    val onClick: ActionFlow?
    // parentProps intentionally omitted (same as Dart)
) {
    companion object {
        fun fromJson(json: JsonLike?): CommonProps? {
            if (json == null) return null

            return CommonProps(
                visibility = ExprOr.fromJson<Boolean>(json["visibility"]),
                align = asType(json["align"]),
                style = tryKeys(
                    json,
                    listOf("style", "styleClass")
                ) { value ->
                    asType<JsonLike>(value)?.let(CommonStyle::fromJson)
                },
                onClick = (json["onClick"] as? JsonLike)?.let(ActionFlow::fromJson
                )
            )
        }
    }
}
