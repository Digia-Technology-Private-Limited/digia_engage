package com.digia.digiaui.framework.widgets.textfield

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.LayoutIdParentData
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

internal const val TextFieldAnimationDuration = 150
private const val PlaceholderAnimationDuration = 83
private const val PlaceholderAnimationDelayOrDuration = 67
internal const val ContainerId = "Container"

internal val IntrinsicMeasurable.layoutId: Any?
    get() = (parentData as? LayoutIdParentData)?.layoutId

internal const val TextFieldId = "TextField"
internal const val PlaceholderId = "Hint"
internal const val LabelId = "Label"
internal const val LeadingId = "Leading"
internal const val TrailingId = "Trailing"
internal const val PrefixId = "Prefix"
internal const val SuffixId = "Suffix"
internal const val SupportingId = "Supporting"
internal val ZeroConstraints = Constraints(0, 0, 0, 0)

internal val TextFieldPadding = 16.dp
internal val HorizontalIconPadding = 12.dp
internal val SupportingTopPadding = 4.dp
internal val PrefixSuffixTextPadding = 2.dp
internal val MinTextLineHeight = 24.dp
internal val MinFocusedLabelLineHeight = 16.dp
internal val MinSupportingTextLineHeight = 16.dp

internal val IconDefaultSizeModifier = Modifier.defaultMinSize(48.dp, 48.dp)

private enum class InputPhase {
    // Text field is focused
    Focused,

    // Text field is not focused and input text is empty
    UnfocusedEmpty,

    // Text field is not focused but input text is not empty
    UnfocusedNotEmpty
}

@Composable
fun CommonDecorationBox(
        value: String,
        innerTextField: @Composable () -> Unit,
        visualTransformation: VisualTransformation,
        label: @Composable (() -> Unit)?,
        placeholder: @Composable (() -> Unit)? = null,
        leadingIcon: @Composable (() -> Unit)? = null,
        trailingIcon: @Composable (() -> Unit)? = null,
        prefix: @Composable (() -> Unit)? = null,
        suffix: @Composable (() -> Unit)? = null,
        supportingText: @Composable (() -> Unit)? = null,
        enabled: Boolean = true,
        isError: Boolean = false,
        singleLine: Boolean = false,
        interactionSource: InteractionSource,
        contentPadding: PaddingValues,
        fillColor: Color? = null,
        focusColor: Color? = null,
        colors: DigiaTextFieldColors? = null,
        container: @Composable (Modifier) -> Unit,
) {
    val transformedText =
            remember(value, visualTransformation) {
                        visualTransformation.filter(AnnotatedString(value))
                    }
                    .text
                    .text

    val isFocused = interactionSource.collectIsFocusedAsState().value
    val inputState =
            when {
                isFocused -> InputPhase.Focused
                transformedText.isEmpty() -> InputPhase.UnfocusedEmpty
                else -> InputPhase.UnfocusedNotEmpty
            }

    //    val labelColor = colors?.labelColor(enabled, isError, isFocused)
    //
    //    val typography = MaterialTheme.typography
    //    val bodyLarge = typography.bodyLarge
    //    val bodySmall = typography.bodySmall
    //    val overrideLabelTextStyleColor =
    //        (bodyLarge.color == Color.Unspecified && bodySmall.color != Color.Unspecified) ||
    //                (bodyLarge.color != Color.Unspecified && bodySmall.color == Color.Unspecified)

    TextFieldTransitionScope(
            inputState = inputState,
            focusedLabelTextStyleColor = Color.Black,
            unfocusedLabelTextStyleColor = Color.Black,
            labelColor = colors?.labelColor(enabled, isError, isFocused) ?: Color.Black,
            showLabel = label != null,
    ) { labelProgress, labelTextStyleColor, labelContentColor, placeholderAlpha, prefixSuffixAlpha
        ->
        val labelProgressValue = labelProgress.value
        val decoratedLabel: @Composable (() -> Unit)? =
                label?.let {
                    @Composable
                    {
                        label()
                        //                    Decoration(labelContentColor.value, labelTextStyle,
                        // it)
                    }
                }

        // Transparent components interfere with Talkback (b/261061240), so if any components below
        // have alpha == 0, we set the component to null instead.

        val placeholderColor = colors?.placeholderColor(enabled, isError, isFocused) ?: Color.Black
        val showPlaceholder by remember {
            derivedStateOf(structuralEqualityPolicy()) { placeholderAlpha.value > 0f }
        }
        val decoratedPlaceholder: @Composable ((Modifier) -> Unit)? =
                if (placeholder != null && transformedText.isEmpty() && showPlaceholder) {
                    @Composable
                    { modifier ->
                        Box(modifier.graphicsLayer { alpha = placeholderAlpha.value }) {
                            placeholder()
                            //                        Decoration(
                            //                            contentColor = placeholderColor,
                            //                            textStyle = bodyLarge,
                            //                            content = placeholder
                            //                        )
                        }
                    }
                } else null

        val prefixColor = colors?.prefixColor(enabled, isError, isFocused) ?: Color.Black
        val showPrefixSuffix by remember {
            derivedStateOf(structuralEqualityPolicy()) { prefixSuffixAlpha.value > 0f }
        }
        val decoratedPrefix: @Composable (() -> Unit)? =
                if (prefix != null && showPrefixSuffix) {
                    @Composable
                    {
                        Box(Modifier.graphicsLayer { alpha = prefixSuffixAlpha.value }) {
                            prefix()
                            //                        Decoration(
                            //                            contentColor = prefixColor,
                            //                            textStyle = bodyLarge,
                            //                            content = prefix
                            //                        )
                        }
                    }
                } else null

        val suffixColor = colors?.suffixColor(enabled, isError, isFocused) ?: Color.Black
        val decoratedSuffix: @Composable (() -> Unit)? =
                if (suffix != null && showPrefixSuffix) {
                    @Composable
                    {
                        Box(Modifier.graphicsLayer { alpha = prefixSuffixAlpha.value }) {
                            suffix()
                            //                        Decoration(
                            //                            contentColor = suffixColor,
                            //                            textStyle = bodyLarge,
                            //                            content = suffix
                            //                        )
                        }
                    }
                } else null

        val leadingIconColor = colors?.leadingIconColor(enabled, isError, isFocused) ?: Color.Black
        val decoratedLeading: @Composable (() -> Unit)? =
                leadingIcon?.let {
                    @Composable { Decoration(contentColor = leadingIconColor, content = it) }
                }

        val trailingIconColor =
                colors?.trailingIconColor(enabled, isError, isFocused) ?: Color.Black
        val decoratedTrailing: @Composable (() -> Unit)? =
                trailingIcon?.let {
                    @Composable { Decoration(contentColor = trailingIconColor, content = it) }
                }

        val supportingTextColor =
                colors?.supportingTextColor(enabled, isError, isFocused) ?: Color.Black
        val decoratedSupporting: @Composable (() -> Unit)? =
                supportingText?.let {
                    @Composable
                    {
                        supportingText()
                        //                    Decoration(
                        //                        contentColor = supportingTextColor,
                        //                        textStyle = bodySmall,
                        //                        content = it
                        //                    )
                    }
                }

        // Outlined cutout
        val labelSize = remember { mutableStateOf(Size.Zero) }
        val borderContainerWithId: @Composable () -> Unit = {
            val cutoutModifier = Modifier.outlineCutout(labelSize::value, contentPadding)
            Box(Modifier.layoutId(ContainerId), propagateMinConstraints = true) {
                container(cutoutModifier)
            }
        }

        OutlinedTextFieldLayout(
                modifier = Modifier,
                textField = innerTextField,
                placeholder = decoratedPlaceholder,
                label = decoratedLabel,
                leading = decoratedLeading,
                trailing = decoratedTrailing,
                prefix = decoratedPrefix,
                suffix = decoratedSuffix,
                supporting = decoratedSupporting,
                singleLine = singleLine,
                onLabelMeasured = {
                    val labelWidth = it.width * labelProgressValue
                    val labelHeight = it.height * labelProgressValue
                    if (labelSize.value.width != labelWidth || labelSize.value.height != labelHeight
                    ) {
                        labelSize.value = Size(labelWidth, labelHeight)
                    }
                },
                // TODO(b/271000818): progress state read should be deferred to layout phase
                animationProgress = labelProgressValue,
                container = borderContainerWithId,
                paddingValues = contentPadding
        )
    }
}

@Composable
private inline fun TextFieldTransitionScope(
        inputState: InputPhase,
        focusedLabelTextStyleColor: Color,
        unfocusedLabelTextStyleColor: Color,
        labelColor: Color,
        showLabel: Boolean,
        content:
                @Composable
                (
                        labelProgress: State<Float>,
                        labelTextStyleColor: State<Color>,
                        labelContentColor: State<Color>,
                        placeholderOpacity: State<Float>,
                        prefixSuffixOpacity: State<Float>,
                ) -> Unit
) {
    // Transitions from/to InputPhase.Focused are the most critical in the transition below.
    // UnfocusedEmpty <-> UnfocusedNotEmpty are needed when a single state is used to control
    // multiple text fields.
    val transition = updateTransition(inputState, label = "TextFieldInputState")

    val labelProgress =
            transition.animateFloat(
                    label = "LabelProgress",
                    transitionSpec = { tween(durationMillis = TextFieldAnimationDuration) }
            ) {
                when (it) {
                    InputPhase.Focused -> 1f
                    InputPhase.UnfocusedEmpty -> 0f
                    InputPhase.UnfocusedNotEmpty -> 1f
                }
            }

    val placeholderOpacity =
            transition.animateFloat(
                    label = "PlaceholderOpacity",
                    transitionSpec = {
                        if (InputPhase.Focused isTransitioningTo InputPhase.UnfocusedEmpty) {
                            tween(
                                    durationMillis = PlaceholderAnimationDelayOrDuration,
                                    easing = LinearEasing
                            )
                        } else if (InputPhase.UnfocusedEmpty isTransitioningTo InputPhase.Focused ||
                                        InputPhase.UnfocusedNotEmpty isTransitioningTo
                                                InputPhase.UnfocusedEmpty
                        ) {
                            tween(
                                    durationMillis = PlaceholderAnimationDuration,
                                    delayMillis = PlaceholderAnimationDelayOrDuration,
                                    easing = LinearEasing
                            )
                        } else {
                            spring()
                        }
                    }
            ) {
                when (it) {
                    InputPhase.Focused -> 1f
                    InputPhase.UnfocusedEmpty -> if (showLabel) 0f else 1f
                    InputPhase.UnfocusedNotEmpty -> 0f
                }
            }

    val prefixSuffixOpacity =
            transition.animateFloat(
                    label = "PrefixSuffixOpacity",
                    transitionSpec = { tween(durationMillis = TextFieldAnimationDuration) }
            ) {
                when (it) {
                    InputPhase.Focused -> 1f
                    InputPhase.UnfocusedEmpty -> if (showLabel) 0f else 1f
                    InputPhase.UnfocusedNotEmpty -> 1f
                }
            }

    val labelTextStyleColor =
            transition.animateColor(
                    transitionSpec = { tween(durationMillis = TextFieldAnimationDuration) },
                    label = "LabelTextStyleColor"
            ) {
                when (it) {
                    InputPhase.Focused -> focusedLabelTextStyleColor
                    else -> unfocusedLabelTextStyleColor
                }
            }

    @Suppress("UnusedTransitionTargetStateParameter")
    val labelContentColor =
            transition.animateColor(
                    transitionSpec = { tween(durationMillis = TextFieldAnimationDuration) },
                    label = "LabelContentColor",
                    targetValueByState = { labelColor }
            )

    content(
            labelProgress,
            labelTextStyleColor,
            labelContentColor,
            placeholderOpacity,
            prefixSuffixOpacity,
    )
}

@Composable
private fun Decoration(contentColor: Color, textStyle: TextStyle, content: @Composable () -> Unit) =
        ProvideContentColorTextStyle(contentColor, textStyle, content)

@Composable
internal fun ProvideContentColorTextStyle(
        contentColor: Color,
        textStyle: TextStyle,
        content: @Composable () -> Unit
) {
    val mergedStyle = LocalTextStyle.current.merge(textStyle)
    CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides mergedStyle,
            content = content
    )
}

@Composable
private fun Decoration(contentColor: Color, content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)

private val OutlinedTextFieldInnerPadding = 4.dp
