package com.digia.digiaui.framework.widgets

import LocalUIResources
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.textStyle
import com.digia.digiaui.framework.utils.JsonLike

/** RichText widget properties Maps to the Flutter VWRichText props structure from rich_text.dart */
data class RichTextProps(
        val textSpans: ExprOr<Any>?, // Can be String or List inside ExprOr
        val maxLines: ExprOr<Int>?,
        val overflow: ExprOr<String>?,
        val alignment: ExprOr<String>?,
        val textStyle: JsonLike?
) {
    companion object {
        fun fromJson(json: JsonLike): RichTextProps {
            return RichTextProps(
                    textSpans = ExprOr.fromValue(json["textSpans"]),
                    maxLines = ExprOr.fromValue(json["maxLines"]),
                    overflow = ExprOr.fromValue(json["overflow"]),
                    alignment = ExprOr.fromValue(json["alignment"]),
                    textStyle = json["textStyle"] as? JsonLike
            )
        }
    }
}

/** Data class representing a single text span within the RichText widget. */
data class TextSpanProps(
        val text: ExprOr<String>?,
        val style: JsonLike?,
        val onClick: ActionFlow?,
        val children: List<Any>? = null // Logic for nested children if supported in future
) {
    companion object {
        fun fromJson(json: JsonLike): TextSpanProps {
            return TextSpanProps(
                    text = ExprOr.fromValue(json["text"]),
                    // Support multiple keys for style as seen in legacy code: spanStyle, textStyle,
                    // style
                    style = (json["spanStyle"] ?: json["textStyle"] ?: json["style"]) as? JsonLike,
                    onClick = (json["onClick"] as? JsonLike)?.let { ActionFlow.fromJson(it) },
                    children = json["children"] as? List<Any>
            )
        }
    }
}

/** Virtual RichText widget - Jetpack Compose implementation */
class VWRichText(
        refName: String?,
        commonProps: CommonProps?,
        parent: VirtualNode?,
        parentProps: Props? = null,
        props: RichTextProps
) :
        VirtualLeafNode<RichTextProps>(
                props = props,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val context = LocalContext.current
        val actionExecutor = LocalActionExecutor.current
        val stateContext = LocalStateContextProvider.current
        val resources = LocalUIResources.current

        // 1. Evaluate properties
        val rawTextSpans = payload.evalObserve(props.textSpans)
        val maxLines = payload.evalObserve(props.maxLines) ?: Int.MAX_VALUE
        val overflowStr = payload.evalObserve(props.overflow)
        val alignmentStr = payload.evalObserve(props.alignment)

        // 2. Map Enums
        val overflow =
                when (overflowStr) {
                    "ellipsis" -> TextOverflow.Ellipsis
                    "clip" -> TextOverflow.Clip
                    "visible" -> TextOverflow.Visible
                    else -> TextOverflow.Clip // Default match Flutter
                }

        val textAlign =
                when (alignmentStr?.lowercase()) {
                    "left", "start" -> TextAlign.Start
                    "right", "end" -> TextAlign.End
                    "center" -> TextAlign.Center
                    "justify" -> TextAlign.Justify
                    else -> TextAlign.Start
                }

        // 3. Base Style
        // If external style is provided, use it, otherwise inherit or default
        var textStyle = props.textStyle
        if (textStyle != null && textStyle["fontToken"] == null && textStyle["value"] != null) {
            val mutableStyle = textStyle.toMutableMap()
            mutableStyle["fontToken"] = textStyle["value"]
            textStyle = mutableStyle
        }
        val baseStyle = payload.textStyle(textStyle) ?: MaterialTheme.typography.bodyMedium

        // 4. Build Annotated String
        // We need to collect actions to execute them later
        val spanActions = remember { mutableMapOf<String, ActionFlow>() }

        // Clear actions on recomposition before rebuilding
        spanActions.clear()

        // We cannot use buildAnnotatedString { ... } because we need to call @Composable functions
        // like payload.textStyle() inside the loop. The lambda of buildAnnotatedString is not
        // @Composable.
        val annotatedString =
                if (rawTextSpans != null) {
                    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
                    AppendSpansRecursive(
                            builder = builder,
                            payload = payload,
                            content = rawTextSpans,
                            actionsMap = spanActions,
                            parentAction = null
                    )
                    builder.toAnnotatedString()
                } else {
                    androidx.compose.ui.text.AnnotatedString("")
                }

        // 5. Render
        // We use Text with ClickableText logic or just ClickableText
        // ClickableText is easier for handling offset-based clicks on spans

        ClickableText(
                text = annotatedString,
                modifier = Modifier.buildModifier(payload).fillMaxWidth(),
                style = baseStyle.merge(androidx.compose.ui.text.TextStyle(textAlign = textAlign)),
                overflow = overflow,
                maxLines = maxLines,
                onClick = { offset ->
                    // Check for annotations at the clicked offset
                    // We use "action_id" tag to find the action key
                    annotatedString
                            .getStringAnnotations(tag = "action_id", start = offset, end = offset)
                            .firstOrNull()
                            ?.let { annotation ->
                                val actionId = annotation.item
                                val action = spanActions[actionId]
                                if (action != null) {
                                    payload.executeAction(
                                            context = context,
                                            actionFlow = action,
                                            actionExecutor = actionExecutor,
                                            stateContext = stateContext,
                                            resourcesProvider = resources
                                    )
                                }
                            }
                }
        )
    }

    @Composable
    private fun AppendSpansRecursive(
            builder: androidx.compose.ui.text.AnnotatedString.Builder,
            payload: RenderPayload,
            content: Any,
            actionsMap: MutableMap<String, ActionFlow>,
            parentAction: ActionFlow?
    ) {
        when (content) {
            is String -> {
                builder.append(content)
            }
            is List<*> -> {
                content.forEach { item ->
                    if (item != null) {
                        AppendSpansRecursive(
                                builder = builder,
                                payload = payload,
                                content = item,
                                actionsMap = actionsMap,
                                parentAction = parentAction
                        )
                    }
                }
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST") val spanJson = content as? JsonLike ?: return
                // Convert to typed object for clarity
                val spanProps = TextSpanProps.fromJson(spanJson)

                // Extract text
                val text = payload.evalExpr(spanProps.text) ?: ""

                // Extract Style
                var styleJson = spanProps.style

                // Normalize style: if 'value' is present but 'fontToken' is not, use 'value' as
                // 'fontToken'
                if (styleJson != null &&
                                styleJson["fontToken"] == null &&
                                styleJson["value"] != null
                ) {
                    val mutableStyle = styleJson.toMutableMap()
                    mutableStyle["fontToken"] = styleJson["value"]
                    styleJson = mutableStyle
                }

                // Construct SpanStyle
                var spanStyle = payload.textStyle(styleJson)?.toSpanStyle() ?: SpanStyle()

                // Handle Gradient
                if (styleJson != null) {
                    val gradientConfig =
                            (styleJson["gradient"] as? JsonLike)?.let {
                                // Logic to resolve gradient expression if needed,
                                // but usually it's a direct map in the style
                                payload.evalExpr(ExprOr.fromValue<JsonLike>(it))
                            }

                    if (gradientConfig != null) {
                        val gradientProps = GradientProps.fromJson(gradientConfig)
                        val brush = gradientProps.toBrush(payload)
                        if (brush != null) {
                            // TODO: Brush support in SpanStyle requires Compose 1.5.0+.
                            // Current version (BOM 2023.03.00) does not support it.
                            // Falling back to a solid color if available or ignoring gradient.
                            spanStyle =
                                    spanStyle.copy(
                                            // brush = brush, // Uncomment when Compose is updated
                                            color = Color.Unspecified
                                    )
                        }
                    }
                }

                // Handle onClick
                val onClickAction = spanProps.onClick ?: parentAction

                val actionId =
                        if (onClickAction != null) {
                            val id = "action_${actionsMap.size}"
                            actionsMap[id] = onClickAction
                            id
                        } else null

                // Apply style and annotation
                builder.withStyle(spanStyle) {
                    if (actionId != null) {
                        builder.pushStringAnnotation(tag = "action_id", annotation = actionId)
                    }

                    builder.append(text)

                    if (actionId != null) {
                        builder.pop()
                    }
                }
            }
        }
    }
}

/** Builder function used by registry */
fun richTextBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        @Suppress("UNUSED_PARAMETER") registry: VirtualWidgetRegistry
): VirtualNode {
    return VWRichText(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = RichTextProps.fromJson(data.props.value)
    )
}
