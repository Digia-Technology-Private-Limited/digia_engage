package com.digia.digiaui.framework.widgets

import LocalUIResources
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.color
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.textStyle
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.NumUtil
import com.digia.digiaui.framework.utils.ToUtils
import com.digia.digiaui.framework.widgets.textfield.CommonDecorationBox
import com.digia.digiaui.framework.widgets.textfield.DigiaTextFieldColors
import com.digia.digiaui.framework.widgets.textfield.TextFieldContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------------- Debouncer Utility ---------------- */

/**
 * A debouncer that delays execution of a function until a specified time has passed without the
 * function being called again.
 */
class Debouncer(private val delayMillis: Long, private val scope: CoroutineScope) {
        private var job: Job? = null

        fun call(action: () -> Unit) {
                job?.cancel()
                job =
                        scope.launch {
                                delay(delayMillis)
                                action()
                        }
        }

        fun cancel() {
                job?.cancel()
        }
}

/* ---------------- Validation ---------------- */

/** Represents a validation rule with associated error message */
data class ValidationIssue(
        val type: String,
        val data: ExprOr<Any>? = null,
        val errorMessage: ExprOr<String>?
) {
        companion object {
                fun fromJson(json: JsonLike): ValidationIssue {
                        return ValidationIssue(
                                type = json["type"] as? String ?: "",
                                data = ExprOr.fromValue(json["data"]),
                                errorMessage =
                                        ExprOr.fromValue<String>(
                                                json["errorMessage"] ?: "Validation Failed"
                                        )
                        )
                }
        }
}

/* ---------------- Border Configuration ---------------- */

/** Configuration for text field border */
data class BorderConfig(
        val borderType: JsonLike? = null,
        val borderStyle: String? = null,
        val borderWidth: Double? = null,
        val borderColor: String? = null,
        val borderRadius: Any? = null
) {
        companion object {
                fun fromJson(json: JsonLike?): BorderConfig? {
                        if (json == null) return null
                        return BorderConfig(
                                borderType = json["borderType"] as? JsonLike,
                                borderStyle = json["borderStyle"] as? String,
                                borderWidth = NumUtil.toDouble(json["borderWidth"]),
                                borderColor = json["borderColor"] as? String,
                                borderRadius = json["borderRadius"]
                        )
                }
        }
}

/* ---------------- Icon Constraints ---------------- */

/** Configuration for icon constraints (min/max width/height) */
data class IconConstraints(
        val minWidth: Double? = null,
        val minHeight: Double? = null,
        val maxWidth: Double? = null,
        val maxHeight: Double? = null
) {
        companion object {
                fun fromJson(json: JsonLike?): IconConstraints? {
                        if (json == null) return null
                        return IconConstraints(
                                minWidth = NumUtil.toDouble(json["minWidth"]),
                                minHeight = NumUtil.toDouble(json["minHeight"]),
                                maxWidth = NumUtil.toDouble(json["maxWidth"]),
                                maxHeight = NumUtil.toDouble(json["maxHeight"])
                        )
                }
        }
}

/* ---------------- TextFormField Props ---------------- */

/** Text Form Field widget properties */
data class TextFormFieldProps(
        val controller: ExprOr<Any>? = null,
        val initialValue: ExprOr<String>? = null,
        val textStyle: JsonLike? = null,
        val maxLength: ExprOr<Int>? = null,
        val minLines: ExprOr<Int>? = null,
        val maxLines: ExprOr<Int>? = null,
        val labelText: ExprOr<String>? = null,
        val labelStyle: JsonLike? = null,
        val keyboardType: ExprOr<String>? = null,
        val textInputAction: ExprOr<String>? = null,
        val textAlign: ExprOr<String>? = null,
        val readOnly: ExprOr<Boolean>? = null,
        val obscureText: ExprOr<Boolean>? = null,
        val cursorColor: ExprOr<String>? = null,
        //    val regex: ExprOr<String>? = null,
        //    val errorText: ExprOr<String>? = null,
        val errorStyle: JsonLike? = null,
        val validationRules: List<ValidationIssue>? = null,
        val enabledBorder: BorderConfig? = null,
        val disabledBorder: BorderConfig? = null,
        val focusedBorder: BorderConfig? = null,
        val focusedErrorBorder: BorderConfig? = null,
        val errorBorder: BorderConfig? = null,
        val focusColor: ExprOr<String>? = null,
        val autoFocus: ExprOr<Boolean>? = null,
        val enabled: ExprOr<Boolean>? = null,
        val fillColor: ExprOr<String>? = null,
        val hintText: ExprOr<String>? = null,
        val hintStyle: JsonLike? = null,
        val contentPadding: Any? = null,
        val onChanged: ActionFlow? = null,
        val onSubmit: ActionFlow? = null,
        val debounceValue: ExprOr<Int>? = null,
        val prefixIconConstraints: IconConstraints? = null,
        val suffixIconConstraints: IconConstraints? = null
) {
        companion object {
                @Suppress("UNCHECKED_CAST")
                fun fromJson(json: JsonLike): TextFormFieldProps {
                        return TextFormFieldProps(
                                controller = ExprOr.fromValue(json["controller"]),
                                initialValue = ExprOr.fromValue(json["initialValue"]),
                                textStyle = json["textStyle"] as? JsonLike,
                                maxLength = ExprOr.fromValue(json["maxLength"]),
                                minLines = ExprOr.fromValue(json["minLines"]),
                                maxLines = ExprOr.fromValue(json["maxLines"]),
                                labelText = ExprOr.fromValue(json["labelText"]),
                                labelStyle = json["labelStyle"] as? JsonLike,
                                keyboardType = ExprOr.fromValue(json["keyboardType"]),
                                textInputAction = ExprOr.fromValue(json["textInputAction"]),
                                textAlign = ExprOr.fromValue(json["textAlign"]),
                                readOnly = ExprOr.fromValue(json["readOnly"]),
                                obscureText = ExprOr.fromValue(json["obscureText"]),
                                cursorColor = ExprOr.fromValue(json["cursorColor"]),
                                //                regex = ExprOr.fromValue(json["regex"]),
                                //                errorText = ExprOr.fromValue(json["errorText"]),
                                errorStyle = json["errorStyle"] as? JsonLike,
                                validationRules =
                                        (json["validationRules"] as? List<Any?>)?.mapNotNull { item
                                                ->
                                                (item as? JsonLike)?.let {
                                                        ValidationIssue.fromJson(it)
                                                }
                                        },
                                enabledBorder =
                                        BorderConfig.fromJson(json["enabledBorder"] as? JsonLike),
                                disabledBorder =
                                        BorderConfig.fromJson(json["disabledBorder"] as? JsonLike),
                                focusedBorder =
                                        BorderConfig.fromJson(json["focusedBorder"] as? JsonLike),
                                focusedErrorBorder =
                                        BorderConfig.fromJson(
                                                json["focusedErrorBorder"] as? JsonLike
                                        ),
                                errorBorder =
                                        BorderConfig.fromJson(json["errorBorder"] as? JsonLike),
                                focusColor = ExprOr.fromValue(json["focusColor"]),
                                autoFocus = ExprOr.fromValue(json["autoFocus"]),
                                enabled = ExprOr.fromValue(json["enabled"]),
                                fillColor = ExprOr.fromValue(json["fillColor"]),
                                hintText = ExprOr.fromValue(json["hintText"]),
                                hintStyle = json["hintStyle"] as? JsonLike,
                                contentPadding = json["contentPadding"],
                                onChanged =
                                        (json["onChanged"] as? JsonLike)?.let {
                                                ActionFlow.fromJson(it)
                                        },
                                onSubmit =
                                        (json["onSubmit"] as? JsonLike)?.let {
                                                ActionFlow.fromJson(it)
                                        },
                                debounceValue = ExprOr.fromValue(json["debounceValue"]),
                                prefixIconConstraints =
                                        IconConstraints.fromJson(
                                                json["prefixIconConstraints"] as? JsonLike
                                        ),
                                suffixIconConstraints =
                                        IconConstraints.fromJson(
                                                json["suffixIconConstraints"] as? JsonLike
                                        )
                        )
                }
        }
}

/* ---------------- Virtual TextFormField Widget ---------------- */

/**
 * Virtual Text Form Field widget
 *
 * Renders a specialized implementation using BasicTextField to match Flutter's behavior regarding
 * borders (dashed/solid), layout, and validation.
 */
class VWTextFormField(
        refName: String?,
        commonProps: CommonProps?,
        parent: VirtualNode?,
        parentProps: Props? = null,
        props: TextFormFieldProps,
        slots: ((VirtualCompositeNode<TextFormFieldProps>) -> Map<String, List<VirtualNode>>?)? =
                null,
) :
        VirtualCompositeNode<TextFormFieldProps>(
                props = props,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps,
                _slots = slots
        ) {

        @Composable
        override fun Render(payload: RenderPayload) {
                // Evaluate props
                val initialValue = payload.evalExpr(props.initialValue) ?: ""
                val enabled = payload.evalExpr(props.enabled) ?: true
                val readOnly = payload.evalExpr(props.readOnly) ?: false
                val obscureText = payload.evalExpr(props.obscureText) ?: false
                val autoFocus = payload.evalExpr(props.autoFocus) ?: false
                val maxLines = payload.evalExpr(props.maxLines)
                val minLines = payload.evalExpr(props.minLines)
                val maxLength = payload.evalExpr(props.maxLength)
                val debounceMs = payload.evalExpr(props.debounceValue) ?: 0

                // Text styles
                val textStyle = payload.textStyle(props.textStyle) ?: LocalTextStyle.current
                val labelStyle = payload.textStyle(props.labelStyle) ?: LocalTextStyle.current
                val hintStyle = payload.textStyle(props.hintStyle) ?: LocalTextStyle.current
                val errorStyle = payload.textStyle(props.errorStyle)

                // Text content
                val labelText = payload.evalExpr(props.labelText)
                val hintText = payload.evalExpr(props.hintText)

                // Colors
                val cursorColor = payload.evalExpr(props.cursorColor)?.let { payload.color(it) }
                val fillColor = payload.evalExpr(props.fillColor)?.let { payload.color(it) }
                val focusColor = payload.evalExpr(props.focusColor)?.let { payload.color(it) }

                // Keyboard configuration
                val keyboardTypeStr = payload.evalExpr(props.keyboardType)
                val textInputActionStr = payload.evalExpr(props.textInputAction)

                val textAlignStr = payload.evalExpr(props.textAlign)

                val context = LocalContext.current
                val actionExecutor = LocalActionExecutor.current
                val stateContext = LocalStateContextProvider.current
                val resources = LocalUIResources.current

                // Borders
                val enabledBorder = toInputBorder(props.enabledBorder, payload)
                val disabledBorder = toInputBorder(props.disabledBorder, payload)
                val focusedBorder = toInputBorder(props.focusedBorder, payload)
                val focusedErrorBorder = toInputBorder(props.focusedErrorBorder, payload)
                val errorBorder = toInputBorder(props.errorBorder, payload)

                // Validation
                val validationRules = props.validationRules

                var textValue by rememberSaveable { mutableStateOf(initialValue) }
                val scope = rememberCoroutineScope()
                val debouncer =
                        remember(debounceMs) {
                                if (debounceMs > 0) Debouncer(debounceMs.toLong(), scope) else null
                        }

                // Validation logic matching Flutter implementation
                fun validateField(value: String): String? {
                        validationRules?.forEach { rule ->
                                val data =
                                        rule.data?.evaluate<Any>(
                                                scopeContext = payload.scopeContext
                                        )
                                val errorMsg =
                                        rule.errorMessage?.evaluate<String>(
                                                scopeContext = payload.scopeContext
                                        )
                                when (rule.type) {
                                        "required" -> {
                                                if (value.trim().isEmpty()) {
                                                        return errorMsg
                                                }
                                        }
                                        "minLength" -> {
                                                val minLen = NumUtil.toDouble(data)?.toInt() ?: 0
                                                if (value.length < minLen) {
                                                        return errorMsg
                                                }
                                        }
                                        "maxLength" -> {
                                                val maxLen =
                                                        NumUtil.toDouble(data)?.toInt()
                                                                ?: Int.MAX_VALUE
                                                if (value.length > maxLen) {
                                                        return errorMsg
                                                }
                                        }
                                        "pattern" -> {
                                                val pattern = data as? String
                                                if (pattern != null) {
                                                        val regex = Regex(pattern)
                                                        if (!regex.matches(value)) {
                                                                return errorMsg
                                                        }
                                                }
                                        }
                                }
                        }
                        return null
                }

                // On value change handler
                fun onValueChange(newValue: String) {
                        // Apply max length constraint
                        val constrainedValue =
                                if (maxLength != null && newValue.length > maxLength) {
                                        newValue.take(maxLength)
                                } else {
                                        newValue
                                }

                        textValue = constrainedValue

                        // Trigger onChanged action with debouncing if configured
                        val triggerAction = {
                                props.onChanged?.let {
                                        payload.executeAction(
                                                context = context,
                                                actionFlow = it,
                                                actionExecutor = actionExecutor,
                                                stateContext = stateContext,
                                                resourcesProvider = resources,
                                                incomingScopeContext =
                                                        payload.scopeContext?.copyAndExtend(
                                                                mapOf("text" to constrainedValue)
                                                        )
                                        )
                                }
                                        ?: Unit
                        }

                        if (debouncer != null) {
                                debouncer.call(triggerAction)
                        } else {
                                triggerAction()
                        }
                }

                val onSubmitHandler = { value: String ->
                        props.onSubmit?.let {
                                payload.executeAction(
                                        context = context,
                                        actionFlow = it,
                                        actionExecutor = actionExecutor,
                                        stateContext = stateContext,
                                        resourcesProvider = resources,
                                        incomingScopeContext =
                                                payload.scopeContext?.copyAndExtend(
                                                        mapOf("text" to value)
                                                )
                                )
                        }
                                ?: Unit
                }

                // Keyboard options
                val keyboardType =
                        when (keyboardTypeStr?.lowercase()) {
                                "text" -> KeyboardType.Text
                                "number" -> KeyboardType.Number
                                "phone" -> KeyboardType.Phone
                                "email", "emailaddress" -> KeyboardType.Email
                                "password" -> KeyboardType.Password
                                "numberpassword" -> KeyboardType.NumberPassword
                                "url" -> KeyboardType.Uri
                                "decimal" -> KeyboardType.Decimal
                                else -> KeyboardType.Text
                        }

                val imeAction =
                        when (textInputActionStr) {
                                "done" -> ImeAction.Done
                                "go" -> ImeAction.Go
                                "next" -> ImeAction.Next
                                "previous" -> ImeAction.Previous
                                "search" -> ImeAction.Search
                                "send" -> ImeAction.Send
                                "none" -> ImeAction.None
                                else -> ImeAction.Default
                        }

                val textAlign =
                        when (textAlignStr) {
                                "left" -> TextAlign.Left
                                "right" -> TextAlign.Right
                                "center" -> TextAlign.Center
                                "start" -> TextAlign.Start
                                "end" -> TextAlign.End
                                "justify" -> TextAlign.Justify
                                else -> TextAlign.Start
                        }

                // Content padding
                val contentPadding =
                        ToUtils.edgeInsets(props.contentPadding) ?: PaddingValues(12.dp)

                // Render prefix and suffix widgets
                val prefixWidget: @Composable (() -> Unit)? =
                        slot("prefix")?.let { prefixNodes -> { prefixNodes.ToWidget(payload) } }

                val suffixWidget: @Composable (() -> Unit)? =
                        slot("suffix")?.let { suffixNodes -> { suffixNodes.ToWidget(payload) } }

                // Colors for DigiaTextFieldColors
                val colors =
                        DigiaTextFieldColors(
                                focusedTextColor = textStyle.color,
                                unfocusedTextColor = textStyle.color,
                                disabledTextColor = textStyle.color.copy(alpha = 0.5f),
                                errorTextColor = textStyle.color,
                                focusedContainerColor = fillColor ?: Color.Transparent,
                                unfocusedContainerColor = fillColor ?: Color.Transparent,
                                disabledContainerColor =
                                        (fillColor ?: Color.Transparent).copy(alpha = 0.5f),
                                errorContainerColor = fillColor ?: Color.Transparent,
                                cursorColor = cursorColor ?: MaterialTheme.colorScheme.primary,
                                errorCursorColor = MaterialTheme.colorScheme.error,
                                textSelectionColors =
                                        androidx.compose.foundation.text.selection
                                                .LocalTextSelectionColors.current,
                                focusedIndicatorColor = focusColor
                                                ?: MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                                disabledIndicatorColor =
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                errorIndicatorColor = MaterialTheme.colorScheme.error,
                                focusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedLeadingIconColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLeadingIconColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                errorLeadingIconColor = MaterialTheme.colorScheme.error,
                                focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTrailingIconColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                errorTrailingIconColor = MaterialTheme.colorScheme.error,
                                focusedLabelColor = focusColor ?: MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor =
                                        labelStyle.color.takeOrElse {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                disabledLabelColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                errorLabelColor = MaterialTheme.colorScheme.error,
                                focusedPlaceholderColor =
                                        hintStyle.color.takeOrElse {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                unfocusedPlaceholderColor =
                                        hintStyle.color.takeOrElse {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                disabledPlaceholderColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                errorPlaceholderColor = MaterialTheme.colorScheme.error,
                                focusedSupportingTextColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedSupportingTextColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledSupportingTextColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                errorSupportingTextColor = MaterialTheme.colorScheme.error,
                                focusedPrefixColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedPrefixColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledPrefixColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                errorPrefixColor = MaterialTheme.colorScheme.error,
                                focusedSuffixColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedSuffixColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledSuffixColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                errorSuffixColor = MaterialTheme.colorScheme.error
                        )

                InternalTextFormField(
                        controller = TextController(initialText = textValue),
                        autoFocus = autoFocus,
                        enabled = enabled,
                        readOnly = readOnly,
                        obscureText = obscureText,
                        keyboardType = keyboardType,
                        imeAction = imeAction,
                        textAlign = textAlign,
                        textStyle = textStyle,
                        labelStyle = labelStyle,
                        hintStyle = hintStyle,
                        errorStyle = errorStyle
                                        ?: MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.error
                                        ),
                        maxLines = maxLines,
                        minLines = minLines,
                        maxLength = maxLength,
                        labelText = labelText,
                        hintText = hintText,
                        validate = ::validateField,
                        cursorColor = cursorColor ?: MaterialTheme.colorScheme.primary,
                        focusedBorder = focusedBorder ?: VWInputBorder.None,
                        enabledBorder = enabledBorder ?: VWInputBorder.None,
                        errorBorder = errorBorder ?: VWInputBorder.None,
                        disabledBorder = disabledBorder ?: VWInputBorder.None,
                        focusedErrorBorder = focusedErrorBorder ?: VWInputBorder.None,
                        onValueChange = ::onValueChange,
                        onSubmit = { value -> onSubmitHandler(value) },
                        prefixWidget = prefixWidget,
                        suffixWidget = suffixWidget,
                        contentPadding = contentPadding,
                        colors = colors
                )

                // Cleanup debouncer on dispose
                DisposableEffect(Unit) { onDispose { debouncer?.cancel() } }
        }

        @Composable
        private fun toInputBorder(config: BorderConfig?, payload: RenderPayload): VWInputBorder? {
                if (config == null) return null

                val borderTypeData = config.borderType
                val typeValue = borderTypeData?.get("value") as? String

                val borderColor = config.borderColor?.let { payload.color(it) } ?: Color.Black
                val borderWidth = config.borderWidth?.let { it.dp } ?: 1.dp

                // Handle dash pattern from nested borderType object
                val dashPatternList =
                        (borderTypeData?.get("dashPattern") as? List<*>)?.filterIsInstance<Number>()
                                ?.map { it.toFloat() }

                val strokeCapStr = borderTypeData?.get("strokeCap") as? String
                val strokeCap =
                        when (strokeCapStr) {
                                "round" -> StrokeCap.Round
                                "square" -> StrokeCap.Square
                                else -> StrokeCap.Butt
                        }

                val shape = ToUtils.borderRadius(config.borderRadius) ?: RoundedCornerShape(4.dp)

                return when (typeValue) {
                        "outlineInputBorder" ->
                                VWInputBorder.Outline(
                                        strokeWidth = borderWidth,
                                        shape = shape,
                                        color = borderColor,
                                        dashed = false,
                                        strokeCap = strokeCap
                                )
                        "underlineInputBorder" ->
                                VWInputBorder.Underline(
                                        strokeWidth = borderWidth,
                                        color = borderColor,
                                        dashed = false
                                )
                        "outlineDashedInputBorder" ->
                                VWInputBorder.Outline(
                                        strokeWidth = borderWidth,
                                        shape = shape,
                                        color = borderColor,
                                        dashed = true,
                                        dashPattern = dashPatternList,
                                        strokeCap = strokeCap
                                )
                        "underlineDashedInputBorder" ->
                                VWInputBorder.Underline(
                                        strokeWidth = borderWidth,
                                        color = borderColor,
                                        dashed = true
                                )
                        else -> VWInputBorder.None
                }
        }
}

/* ---------------- Builder Function ---------------- */

/** Builder function for TextFormField widget */
fun textFormFieldBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
        return VWTextFormField(
                refName = data.refName,
                commonProps = data.commonProps,
                parent = parent,
                parentProps = data.props,
                props = TextFormFieldProps.fromJson(data.props.value),
                slots = { self -> registerAllChildern(data.childGroups, self, registry) }
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalTextFormField(
        controller: TextController,
        autoFocus: Boolean = false,
        enabled: Boolean = true,
        readOnly: Boolean = false,
        obscureText: Boolean = false,
        keyboardType: KeyboardType = KeyboardType.Text,
        imeAction: ImeAction = ImeAction.Done,
        textAlign: TextAlign = TextAlign.Start,
        textStyle: TextStyle = LocalTextStyle.current,
        labelStyle: TextStyle = LocalTextStyle.current,
        hintStyle: TextStyle = LocalTextStyle.current,
        errorStyle: TextStyle = LocalTextStyle.current,
        contentPadding: PaddingValues = PaddingValues(0.dp),
        maxLines: Int? = null,
        minLines: Int? = null,
        maxLength: Int? = null,
        labelText: String? = null,
        hintText: String? = null,
        validate: ((String) -> String?)? = null,
        cursorColor: Color = MaterialTheme.colorScheme.primary,
        focusedBorder: VWInputBorder = VWInputBorder.None,
        enabledBorder: VWInputBorder = VWInputBorder.None,
        errorBorder: VWInputBorder = focusedBorder,
        disabledBorder: VWInputBorder = enabledBorder,
        focusedErrorBorder: VWInputBorder = errorBorder,
        onValueChange: (String) -> Unit,
        onSubmit: (String) -> Unit,
        prefixWidget: (@Composable () -> Unit)? = null,
        suffixWidget: (@Composable () -> Unit)? = null,
        colors: DigiaTextFieldColors? = null
) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        var errorText by remember { mutableStateOf<String?>(null) }
        var showError by remember {
                mutableStateOf(false)
        } // Only show error after focus loss or submit

        fun validateNow(value: String): String? {
                return validate?.invoke(value)
        }

        LaunchedEffect(isFocused) {
                if (!isFocused) {
                        // Validate on blur
                        val err = validateNow(controller.text)
                        if (err != null) {
                                showError = true
                                errorText = err
                        }
                }
        }

        // Determine effective border based on state
        // Priority: Disabled, Focused Error, Error, Focused, Enabled
        // But we need to pass individual borders to TextFieldContainer, it handles logic?
        // TextFieldContainer takes enabledBorder, etc.
        // However, TextFieldContainer logic:
        // val currentBorder = when {
        //    !enabled -> disabledBorder
        //    isError && focused -> focusedErrorBorder
        //    isError -> errorBorder
        //    focused -> focusedBorder
        //    else -> enabledBorder
        // }
        // So we just pass all of them.

        val effectiveMaxLines = if (obscureText) 1 else maxLines ?: Int.MAX_VALUE
        val effectiveMinLines = if (obscureText) 1 else minLines ?: 1
        val singleLine = effectiveMaxLines == 1

        BasicTextField(
                value = controller.text,
                onValueChange = {
                        if (maxLength == null || it.length <= maxLength) {
                                controller.text = it
                                onValueChange(it)
                                if (showError) errorText = validateNow(it)
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                readOnly = readOnly,
                textStyle =
                        textStyle.copy(
                                textAlign = textAlign,
                                color =
                                        colors?.textColor(
                                                enabled,
                                                showError && errorText != null,
                                                isFocused
                                        )
                                                ?: textStyle.color
                        ),
                cursorBrush = SolidColor(cursorColor),
                keyboardOptions =
                        KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(onDone = { onSubmit(controller.text) }),
                interactionSource = interactionSource,
                singleLine = singleLine,
                maxLines = effectiveMaxLines,
                minLines = effectiveMinLines,
                visualTransformation =
                        if (obscureText) PasswordVisualTransformation()
                        else VisualTransformation.None,
                decorationBox = { innerTextField ->
                        CommonDecorationBox(
                                value = controller.text,
                                innerTextField = innerTextField,
                                visualTransformation =
                                        if (obscureText) PasswordVisualTransformation()
                                        else VisualTransformation.None,
                                label = labelText?.let { { Text(it, style = labelStyle) } },
                                placeholder =
                                        if (controller.text.isEmpty() && hintText != null) {
                                                {
                                                        Text(
                                                                hintText,
                                                                style =
                                                                        hintStyle.copy(
                                                                                textAlign =
                                                                                        textAlign
                                                                        ),
                                                                modifier = Modifier.fillMaxWidth()
                                                        )
                                                }
                                        } else null,
                                leadingIcon = prefixWidget,
                                trailingIcon = suffixWidget,
                                // prefix and suffix slots are for text/widget prefix/suffix inline
                                // with
                                // text,
                                // but we map schema 'prefix'/'suffix' to leading/trailing icons as
                                // per
                                // Flutter implementation behavior.

                                supportingText =
                                        if (showError && errorText != null) {
                                                {
                                                        Text(
                                                                errorText!!,
                                                                style =
                                                                        errorStyle.copy(
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .error
                                                                        )
                                                        )
                                                }
                                        } else null,
                                enabled = enabled,
                                isError = showError && errorText != null,
                                singleLine = singleLine,
                                interactionSource = interactionSource,
                                contentPadding = contentPadding,
                                colors = colors,
                                container = { cutoutModifier ->
                                        TextFieldContainer(
                                                enabled = enabled,
                                                isError = showError && errorText != null,
                                                interactionSource = interactionSource,
                                                colors = colors,
                                                enabledBorder = enabledBorder,
                                                disabledBorder = disabledBorder,
                                                focusedBorder = focusedBorder,
                                                focusedErrorBorder = focusedErrorBorder,
                                                errorBorder = errorBorder,
                                                cutoutModifier = cutoutModifier
                                        )
                                }
                        )
                }
        )
}

// Sealed class for borders (must be available for TextFieldContainer usage if it imports it from
// here)
sealed class VWInputBorder {
        data class Outline(
                val strokeWidth: Dp = 1.dp,
                val shape: Shape = RoundedCornerShape(4.dp),
                val color: Color = Color.Black,
                val dashed: Boolean = false,
                val strokeCap: StrokeCap = StrokeCap.Butt,
                val dashPattern: List<Float>? = null
        ) : VWInputBorder()

        data class Underline(
                val strokeWidth: Dp = 1.dp,
                val color: Color = Color.Black,
                val dashed: Boolean = false
        ) : VWInputBorder()

        object None : VWInputBorder()
}

// Extension function for dashed outline border
fun Modifier.dashedOutlineBorder(strokeWidth: Dp, radius: Dp, color: Color) = drawBehind {
        val strokePx = strokeWidth.toPx()
        val cornerRadiusPx = radius.toPx()
        drawRoundRect(
                color = color,
                size = size,
                cornerRadius = CornerRadius(cornerRadiusPx),
                style =
                        Stroke(
                                width = strokePx,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
        )
}

// TextController class
class TextController(initialText: String = "") {
        private val _text = mutableStateOf(initialText)
        var text: String
                get() = _text.value
                set(value) {
                        _text.value = value
                }
}
