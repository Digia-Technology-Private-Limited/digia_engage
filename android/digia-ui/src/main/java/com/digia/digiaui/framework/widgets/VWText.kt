package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.textStyle
import com.digia.digiaui.framework.utils.JsonLike

/** Text widget properties */
data class TextProps(
        val text: ExprOr<String>?,
        val textStyle: JsonLike? = null,
        val maxLines: ExprOr<Int>? = null,
        val textAlign: ExprOr<String>? = null,
        val overflow: ExprOr<String>? = null
) {
        companion object {
                @Suppress("UNCHECKED_CAST")
                fun fromJson(json: JsonLike): TextProps {
                        return TextProps(
                                text = ExprOr.fromValue(json["text"]),
                                textStyle = json["textStyle"] as? JsonLike,
                                maxLines = ExprOr.fromValue(json["maxLines"]),
                                textAlign = ExprOr.fromValue(json["alignment"]),
                                overflow = ExprOr.fromValue(json["overflow"])
                        )
                }
        }
}

/** Virtual Text widget */
class VWText(
        refName: String?,
        commonProps: CommonProps?,
        parent: VirtualNode?,
        parentProps: Props? = null,
        props: TextProps
) :
        VirtualLeafNode<TextProps>(
                props = props,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps
        ) {

        @Composable
        override fun Render(payload: RenderPayload) {
                // Resolve text alignment to determine if we should fill max width by default
                val textAlignStr = payload.evalExpr(props.textAlign)
                val shouldFillWidth =
                        textAlignStr?.lowercase() in listOf("center", "right", "justify", "end")

                var modifier = Modifier.buildModifier(payload)

                // If text alignment is non-default, ensure the widget fills available width
                // unless explicitly overridden by sizing properties in buildModifier (which run
                // last if we prepend,
                // but buildModifier logic applies sizing internally. Here we modify the result).
                // Appending fillMaxWidth here means "Fill Width" takes precedence over "Wrap
                // Content" from buildModifier defaults.
                if (shouldFillWidth) {
                        modifier = Modifier.fillMaxWidth().then(modifier)
                }

                CommonTextRender(props = props, payload = payload, modifier = modifier)
        }
}

@Composable
internal fun CommonTextRender(
        props: TextProps,
        payload: RenderPayload,
        modifier: Modifier = Modifier
) {
        // Evaluate expressions
        val text = payload.evalExpr(props.text) ?: ""
        val style = payload.textStyle(props.textStyle)
        val maxLines = payload.evalExpr(props.maxLines)
        val textAlignStr = payload.evalExpr(props.textAlign)
        val overflowStr = payload.evalExpr(props.overflow)

        // Convert string values to Compose types
        val textAlign =
                when (textAlignStr?.lowercase()) {
                        "center" -> TextAlign.Center
                        "left" -> TextAlign.Left
                        "right" -> TextAlign.Right
                        "justify" -> TextAlign.Justify
                        "start" -> TextAlign.Start
                        "end" -> TextAlign.End
                        else -> TextAlign.Left
                }

        val textOverflow =
                when (overflowStr) {
                        "clip" -> TextOverflow.Clip
                        "ellipsis" -> TextOverflow.Ellipsis
                        "visible" -> TextOverflow.Visible
                        else -> TextOverflow.Clip
                }

        // Gradient logic
        var finalModifier = modifier
        var finalStyle = style ?: androidx.compose.ui.text.TextStyle.Default

        val gradientConfig =
                (payload.evalExpr(ExprOr.fromValue(props.textStyle?.get("gradient")))) as? JsonLike
        if (gradientConfig != null) {
                val gradientBrush = GradientProps.fromJson(gradientConfig).toBrush(payload)
                if (gradientBrush != null) {
                        // Apply gradient shader mask
                        finalStyle = finalStyle.copy(color = Color.White)
                        finalModifier =
                                finalModifier.graphicsLayer(alpha = 0.99f).drawWithCache {
                                        onDrawWithContent {
                                                drawContent()
                                                drawRect(
                                                        brush = gradientBrush,
                                                        blendMode = BlendMode.SrcIn
                                                )
                                        }
                                }
                }
        }

        // Render Material3 Text
        Text(
                text = text.toString(),
                style = finalStyle,
                maxLines = maxLines ?: Int.MAX_VALUE,
                textAlign = textAlign,
                overflow = textOverflow,
                modifier = finalModifier
        )
}

/** Builder function for Text widget */
fun textBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
        return VWText(
                refName = data.refName,
                commonProps = data.commonProps,
                parent = parent,
                parentProps = data.parentProps,
                props = TextProps.fromJson(data.props.value)
        )
}
