package com.digia.digiaui.framework.widgets

import LocalUIResources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.*
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.utils.*
import com.digia.digiaui.framework.utils.ToUtils

class VWPinfield(
        refName: String? = null,
        commonProps: CommonProps? = null,
        private val pinfieldProps: PinfieldProps,
        parent: VirtualNode? = null,
        parentProps: Props? = null
) :
        VirtualLeafNode<PinfieldProps>(
                props = pinfieldProps,
                commonProps = commonProps,
                parentProps = parentProps,
                parent = parent,
                refName = refName
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val context = LocalContext.current.applicationContext
        val actionExecutor = LocalActionExecutor.current
        val stateContext = LocalStateContextProvider.current
        val resources = LocalUIResources.current

        val length = payload.evalExpr(pinfieldProps.length)?.toInt() ?: 4
        val autoFocus = payload.evalExpr(pinfieldProps.autoFocus) ?: false
        val enabled = payload.evalExpr(pinfieldProps.enabled) ?: true
        val obscureText = payload.evalExpr(pinfieldProps.obscureText) ?: false
        val obscureSymbol = payload.evalExpr(pinfieldProps.obscureSymbol) ?: "*"

        // Default Pin Theme
        val defaultTheme = pinfieldProps.defaultPinTheme
        val width = (defaultTheme?.width ?: 56.0).dp
        val height = (defaultTheme?.height ?: 60.0).dp
        // margin/padding might need evaluation if they are expressions, but here treating as
        // raw/utils
        val margin = ToUtils.edgeInsets(defaultTheme?.margin)
        val padding = ToUtils.edgeInsets(defaultTheme?.padding)
        val cornerRadius =
                ToUtils.borderRadius(defaultTheme?.borderRadius) ?: RoundedCornerShape(8.dp)

        val fillColor = payload.evalColor(defaultTheme?.fillColor) ?: Color.Transparent
        val borderColor = payload.evalColor(defaultTheme?.borderColor) ?: Color.Black
        val borderWidth = (defaultTheme?.borderWidth ?: 1.0).dp

        // Handle TextStyle
        val textStyleRaw = defaultTheme?.textStyle as? Map<String, Any?>
        val textStyle = payload.textStyle(textStyleRaw) ?: MaterialTheme.typography.bodyLarge

        // State for input value
        var text by remember { mutableStateOf("") }

        BasicTextField(
                value = text,
                onValueChange = { newText ->
                    if (newText.length <= length && enabled) {
                        text = newText

                        // OnChanged
                        pinfieldProps.onChanged?.let { actionFlow ->
                            payload.executeAction(
                                    context = context,
                                    actionFlow = actionFlow,
                                    stateContext = stateContext,
                                    resourcesProvider = resources,
                                    actionExecutor = actionExecutor,
                                    incomingScopeContext =
                                            DefaultScopeContext(variables = mapOf("pin" to newText))
                            )
                        }

                        // OnCompleted
                        if (newText.length == length) {
                            pinfieldProps.onCompleted?.let { actionFlow ->
                                payload.executeAction(
                                        context = context,
                                        actionFlow = actionFlow,
                                        stateContext = stateContext,
                                        resourcesProvider = resources,
                                        actionExecutor = actionExecutor,
                                        incomingScopeContext =
                                                DefaultScopeContext(
                                                        variables = mapOf("pin" to newText)
                                                )
                                )
                            }
                        }
                    }
                },
                decorationBox = {
                    Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(length) { index ->
                            val char =
                                    if (index < text.length) {
                                        if (obscureText) obscureSymbol else text[index].toString()
                                    } else ""

                            Box(
                                    modifier =
                                            Modifier.size(width, height)
                                                    .clip(cornerRadius)
                                                    .background(fillColor)
                                                    .border(borderWidth, borderColor, cornerRadius)
                                                    .padding(padding),
                                    contentAlignment = Alignment.Center
                            ) { Text(text = char, style = textStyle, textAlign = TextAlign.Center) }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.buildModifier(payload)
        )
    }
}

// Data Classes

data class PinfieldProps(
        val length: ExprOr<Double>? = null,
        val autoFocus: ExprOr<Boolean>? = null,
        val enabled: ExprOr<Boolean>? = null,
        val obscureText: ExprOr<Boolean>? = null,
        val obscureSymbol: ExprOr<String>? = null,
        val defaultPinTheme: PinThemeProps? = null,
        val onChanged: ActionFlow? = null,
        val onCompleted: ActionFlow? = null
) {
    companion object {
        fun fromJson(json: JsonLike): PinfieldProps {
            return PinfieldProps(
                    length = ExprOr.fromValue(json["length"]),
                    autoFocus = ExprOr.fromValue(json["autoFocus"]),
                    enabled = ExprOr.fromValue(json["enabled"]),
                    obscureText = ExprOr.fromValue(json["obscureText"]),
                    obscureSymbol = ExprOr.fromValue(json["obscureSymbol"]),
                    defaultPinTheme =
                            (json["defaultPinTheme"] as? JsonLike)?.let {
                                PinThemeProps.fromJson(it)
                            },
                    onChanged = (json["onChanged"] as? JsonLike)?.let { ActionFlow.fromJson(it) },
                    onCompleted =
                            (json["onCompleted"] as? JsonLike)?.let { ActionFlow.fromJson(it) }
            )
        }
    }
}

data class PinThemeProps(
        val width: Double? = null,
        val height: Double? = null,
        val margin: Any? = null,
        val padding: Any? = null,
        val textStyle: Any? = null,
        val fillColor: Any? = null,
        val borderColor: Any? = null,
        val borderWidth: Double? = null,
        val borderRadius: Any? = null
) {
    companion object {
        fun fromJson(json: JsonLike): PinThemeProps {
            return PinThemeProps(
                    width = NumUtil.toDouble(json["width"]),
                    height = NumUtil.toDouble(json["height"]),
                    margin = json["margin"],
                    padding = json["padding"],
                    textStyle = json["textStyle"],
                    fillColor = json["fillColor"],
                    borderColor = json["borderColor"],
                    borderWidth = NumUtil.toDouble(json["borderWidth"]),
                    borderRadius = json["borderRadius"]
            )
        }
    }
}

// Builder
fun pinFieldBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWPinfield(
            refName = data.refName,
            commonProps = data.commonProps,
            pinfieldProps = PinfieldProps.fromJson(data.props.value),
            parent = parent,
            parentProps = data.parentProps
    )
}
