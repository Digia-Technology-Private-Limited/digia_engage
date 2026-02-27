package com.digia.digiaui.framework.widgets.textfield

import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse



@Immutable
class DigiaTextFieldColors
constructor(
    val focusedTextColor: Color,
    val unfocusedTextColor: Color,
    val disabledTextColor: Color,
    val errorTextColor: Color,
    val focusedContainerColor: Color,
    val unfocusedContainerColor: Color,
    val disabledContainerColor: Color,
    val errorContainerColor: Color,
    val cursorColor: Color,
    val errorCursorColor: Color,
    val textSelectionColors: TextSelectionColors,
    val focusedIndicatorColor: Color,
    val unfocusedIndicatorColor: Color,
    val disabledIndicatorColor: Color,
    val errorIndicatorColor: Color,
    val focusedLeadingIconColor: Color,
    val unfocusedLeadingIconColor: Color,
    val disabledLeadingIconColor: Color,
    val errorLeadingIconColor: Color,
    val focusedTrailingIconColor: Color,
    val unfocusedTrailingIconColor: Color,
    val disabledTrailingIconColor: Color,
    val errorTrailingIconColor: Color,
    val focusedLabelColor: Color,
    val unfocusedLabelColor: Color,
    val disabledLabelColor: Color,
    val errorLabelColor: Color,
    val focusedPlaceholderColor: Color,
    val unfocusedPlaceholderColor: Color,
    val disabledPlaceholderColor: Color,
    val errorPlaceholderColor: Color,
    val focusedSupportingTextColor: Color,
    val unfocusedSupportingTextColor: Color,
    val disabledSupportingTextColor: Color,
    val errorSupportingTextColor: Color,
    val focusedPrefixColor: Color,
    val unfocusedPrefixColor: Color,
    val disabledPrefixColor: Color,
    val errorPrefixColor: Color,
    val focusedSuffixColor: Color,
    val unfocusedSuffixColor: Color,
    val disabledSuffixColor: Color,
    val errorSuffixColor: Color,
) {

    /**
     * Returns a copy of this ChipColors, optionally overriding some of the values. This uses the
     * Color.Unspecified to mean “use the value from the source”
     */
    fun copy(
        focusedTextColor: Color = this.focusedTextColor,
        unfocusedTextColor: Color = this.unfocusedTextColor,
        disabledTextColor: Color = this.disabledTextColor,
        errorTextColor: Color = this.errorTextColor,
        focusedContainerColor: Color = this.focusedContainerColor,
        unfocusedContainerColor: Color = this.unfocusedContainerColor,
        disabledContainerColor: Color = this.disabledContainerColor,
        errorContainerColor: Color = this.errorContainerColor,
        cursorColor: Color = this.cursorColor,
        errorCursorColor: Color = this.errorCursorColor,
        textSelectionColors: TextSelectionColors? = this.textSelectionColors,
        focusedIndicatorColor: Color = this.focusedIndicatorColor,
        unfocusedIndicatorColor: Color = this.unfocusedIndicatorColor,
        disabledIndicatorColor: Color = this.disabledIndicatorColor,
        errorIndicatorColor: Color = this.errorIndicatorColor,
        focusedLeadingIconColor: Color = this.focusedLeadingIconColor,
        unfocusedLeadingIconColor: Color = this.unfocusedLeadingIconColor,
        disabledLeadingIconColor: Color = this.disabledLeadingIconColor,
        errorLeadingIconColor: Color = this.errorLeadingIconColor,
        focusedTrailingIconColor: Color = this.focusedTrailingIconColor,
        unfocusedTrailingIconColor: Color = this.unfocusedTrailingIconColor,
        disabledTrailingIconColor: Color = this.disabledTrailingIconColor,
        errorTrailingIconColor: Color = this.errorTrailingIconColor,
        focusedLabelColor: Color = this.focusedLabelColor,
        unfocusedLabelColor: Color = this.unfocusedLabelColor,
        disabledLabelColor: Color = this.disabledLabelColor,
        errorLabelColor: Color = this.errorLabelColor,
        focusedPlaceholderColor: Color = this.focusedPlaceholderColor,
        unfocusedPlaceholderColor: Color = this.unfocusedPlaceholderColor,
        disabledPlaceholderColor: Color = this.disabledPlaceholderColor,
        errorPlaceholderColor: Color = this.errorPlaceholderColor,
        focusedSupportingTextColor: Color = this.focusedSupportingTextColor,
        unfocusedSupportingTextColor: Color = this.unfocusedSupportingTextColor,
        disabledSupportingTextColor: Color = this.disabledSupportingTextColor,
        errorSupportingTextColor: Color = this.errorSupportingTextColor,
        focusedPrefixColor: Color = this.focusedPrefixColor,
        unfocusedPrefixColor: Color = this.unfocusedPrefixColor,
        disabledPrefixColor: Color = this.disabledPrefixColor,
        errorPrefixColor: Color = this.errorPrefixColor,
        focusedSuffixColor: Color = this.focusedSuffixColor,
        unfocusedSuffixColor: Color = this.unfocusedSuffixColor,
        disabledSuffixColor: Color = this.disabledSuffixColor,
        errorSuffixColor: Color = this.errorSuffixColor,
    ) =
        DigiaTextFieldColors(
            focusedTextColor.takeOrElse { this.focusedTextColor },
            unfocusedTextColor.takeOrElse { this.unfocusedTextColor },
            disabledTextColor.takeOrElse { this.disabledTextColor },
            errorTextColor.takeOrElse { this.errorTextColor },
            focusedContainerColor.takeOrElse { this.focusedContainerColor },
            unfocusedContainerColor.takeOrElse { this.unfocusedContainerColor },
            disabledContainerColor.takeOrElse { this.disabledContainerColor },
            errorContainerColor.takeOrElse { this.errorContainerColor },
            cursorColor.takeOrElse { this.cursorColor },
            errorCursorColor.takeOrElse { this.errorCursorColor },
            textSelectionColors.takeOrElse { this.textSelectionColors },
            focusedIndicatorColor.takeOrElse { this.focusedIndicatorColor },
            unfocusedIndicatorColor.takeOrElse { this.unfocusedIndicatorColor },
            disabledIndicatorColor.takeOrElse { this.disabledIndicatorColor },
            errorIndicatorColor.takeOrElse { this.errorIndicatorColor },
            focusedLeadingIconColor.takeOrElse { this.focusedLeadingIconColor },
            unfocusedLeadingIconColor.takeOrElse { this.unfocusedLeadingIconColor },
            disabledLeadingIconColor.takeOrElse { this.disabledLeadingIconColor },
            errorLeadingIconColor.takeOrElse { this.errorLeadingIconColor },
            focusedTrailingIconColor.takeOrElse { this.focusedTrailingIconColor },
            unfocusedTrailingIconColor.takeOrElse { this.unfocusedTrailingIconColor },
            disabledTrailingIconColor.takeOrElse { this.disabledTrailingIconColor },
            errorTrailingIconColor.takeOrElse { this.errorTrailingIconColor },
            focusedLabelColor.takeOrElse { this.focusedLabelColor },
            unfocusedLabelColor.takeOrElse { this.unfocusedLabelColor },
            disabledLabelColor.takeOrElse { this.disabledLabelColor },
            errorLabelColor.takeOrElse { this.errorLabelColor },
            focusedPlaceholderColor.takeOrElse { this.focusedPlaceholderColor },
            unfocusedPlaceholderColor.takeOrElse { this.unfocusedPlaceholderColor },
            disabledPlaceholderColor.takeOrElse { this.disabledPlaceholderColor },
            errorPlaceholderColor.takeOrElse { this.errorPlaceholderColor },
            focusedSupportingTextColor.takeOrElse { this.focusedSupportingTextColor },
            unfocusedSupportingTextColor.takeOrElse { this.unfocusedSupportingTextColor },
            disabledSupportingTextColor.takeOrElse { this.disabledSupportingTextColor },
            errorSupportingTextColor.takeOrElse { this.errorSupportingTextColor },
            focusedPrefixColor.takeOrElse { this.focusedPrefixColor },
            unfocusedPrefixColor.takeOrElse { this.unfocusedPrefixColor },
            disabledPrefixColor.takeOrElse { this.disabledPrefixColor },
            errorPrefixColor.takeOrElse { this.errorPrefixColor },
            focusedSuffixColor.takeOrElse { this.focusedSuffixColor },
            unfocusedSuffixColor.takeOrElse { this.unfocusedSuffixColor },
            disabledSuffixColor.takeOrElse { this.disabledSuffixColor },
            errorSuffixColor.takeOrElse { this.errorSuffixColor },
        )

    internal fun TextSelectionColors?.takeOrElse(
        block: () -> TextSelectionColors
    ): TextSelectionColors = this ?: block()

    /**
     * Represents the color used for the leading icon of this text field.
     *
     * @param enabled whether the text field is enabled
     * @param isError whether the text field's current value is in error
     * @param focused whether the text field is in focus
     */
    @Stable
    internal fun leadingIconColor(
        enabled: Boolean,
        isError: Boolean,
        focused: Boolean,
    ): Color =
        when {
            !enabled -> disabledLeadingIconColor
            isError -> errorLeadingIconColor
            focused -> focusedLeadingIconColor
            else -> unfocusedLeadingIconColor
        }

    /**
     * Represents the color used for the trailing icon of this text field.
     *
     * @param enabled whether the text field is enabled
     * @param isError whether the text field's current value is in error
     * @param focused whether the text field is in focus
     */
    @Stable
    internal fun trailingIconColor(
        enabled: Boolean,
        isError: Boolean,
        focused: Boolean,
    ): Color =
        when {
            !enabled -> disabledTrailingIconColor
            isError -> errorTrailingIconColor
            focused -> focusedTrailingIconColor
            else -> unfocusedTrailingIconColor
        }

    /**
     * Represents the color used for the border indicator of this text field.
     *
     * @param enabled whether the text field is enabled
     * @param isError whether the text field's current value is in error
     * @param focused whether the text field is in focus
     */
    @Stable
    internal fun indicatorColor(
        enabled: Boolean,
        isError: Boolean,
        focused: Boolean,
    ): Color =
        when {
            !enabled -> disabledIndicatorColor
            isError -> errorIndicatorColor
            focused -> focusedIndicatorColor
            else -> unfocusedIndicatorColor
        }

    /**
     * Represents the container color for this text field.
     *
     * @param enabled whether the text field is enabled
     * @param isError whether the text field's current value is in error
     * @param focused whether the text field is in focus
     */
    @Stable
    internal fun containerColor(
        enabled: Boolean,
        isError: Boolean,
        focused: Boolean,
    ): Color =
        when {
            !enabled -> disabledContainerColor
            isError -> errorContainerColor
            focused -> focusedContainerColor
            else -> unfocusedContainerColor
        }

    /**
     * Represents the color used for the placeholder of this text field.
     *
     * @param enabled whether the text field is enabled
     * @param isError whether the text field's current value is in error
     * @param focused whether the text field is in focus
     */
    @Stable
    internal fun placeholderColor(
        enabled: Boolean,
        isError: Boolean,
        focused: Boolean,
    ): Color =
        when {
            !enabled -> disabledPlaceholderColor
            isError -> errorPlaceholderColor
            focused -> focusedPlaceholderColor
            else -> unfocusedPlaceholderColor
        }

    /**
     * Represents the color used for the label of this text field.
     *
     * @param enabled whether the text field is enabled
     * @param isError whether the text field's current value is in error
     * @param focused whether the text field is in focus
     */
    @Stable
    internal fun labelColor(
        enabled: Boolean,
        isError: Boolean,
        focused: Boolean,
    ): Color =
        when {
            !enabled -> disabledLabelColor
            isError -> errorLabelColor
            focused -> focusedLabelColor
            else -> unfocusedLabelColor
        }

    /**
     * Represents the color used for the input field of this text field.
     *
     * @param enabled whether the text field is enabled
     * @param isError whether the text field's current value is in error
     * @param focused whether the text field is in focus
     */
    @Stable
    internal fun textColor(
        enabled: Boolean,
        isError: Boolean,
        focused: Boolean,
    ): Color =
        when {
            !enabled -> disabledTextColor
            isError -> errorTextColor
            focused -> focusedTextColor
            else -> unfocusedTextColor
        }

    /**
     * Represents the colors used for the supporting text of this text field.
     *
     * @param enabled whether the text field is enabled
     * @param isError whether the text field's current value is in error
     * @param focused whether the text field is in focus
     */
    @Stable
    internal fun supportingTextColor(
        enabled: Boolean,
        isError: Boolean,
        focused: Boolean,
    ): Color =
        when {
            !enabled -> disabledSupportingTextColor
            isError -> errorSupportingTextColor
            focused -> focusedSupportingTextColor
            else -> unfocusedSupportingTextColor
        }

    /**
     * Represents the color used for the prefix of this text field.
     *
     * @param enabled whether the text field is enabled
     * @param isError whether the text field's current value is in error
     * @param focused whether the text field is in focus
     */
    @Stable
    internal fun prefixColor(
        enabled: Boolean,
        isError: Boolean,
        focused: Boolean,
    ): Color =
        when {
            !enabled -> disabledPrefixColor
            isError -> errorPrefixColor
            focused -> focusedPrefixColor
            else -> unfocusedPrefixColor
        }

    /**
     * Represents the color used for the suffix of this text field.
     *
     * @param enabled whether the text field is enabled
     * @param isError whether the text field's current value is in error
     * @param focused whether the text field is in focus
     */
    @Stable
    internal fun suffixColor(
        enabled: Boolean,
        isError: Boolean,
        focused: Boolean,
    ): Color =
        when {
            !enabled -> disabledSuffixColor
            isError -> errorSuffixColor
            focused -> focusedSuffixColor
            else -> unfocusedSuffixColor
        }

    /**
     * Represents the color used for the cursor of this text field.
     *
     * @param isError whether the text field's current value is in error
     */
    @Stable
    internal fun cursorColor(isError: Boolean): Color =
        if (isError) errorCursorColor else cursorColor

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is androidx.compose.material3.TextFieldColors) return false

        if (focusedTextColor != other.focusedTextColor) return false
        if (unfocusedTextColor != other.unfocusedTextColor) return false
        if (disabledTextColor != other.disabledTextColor) return false
        if (errorTextColor != other.errorTextColor) return false
        if (focusedContainerColor != other.focusedContainerColor) return false
        if (unfocusedContainerColor != other.unfocusedContainerColor) return false
        if (disabledContainerColor != other.disabledContainerColor) return false
        if (errorContainerColor != other.errorContainerColor) return false
        if (cursorColor != other.cursorColor) return false
        if (errorCursorColor != other.errorCursorColor) return false
        if (textSelectionColors != other.textSelectionColors) return false
        if (focusedIndicatorColor != other.focusedIndicatorColor) return false
        if (unfocusedIndicatorColor != other.unfocusedIndicatorColor) return false
        if (disabledIndicatorColor != other.disabledIndicatorColor) return false
        if (errorIndicatorColor != other.errorIndicatorColor) return false
        if (focusedLeadingIconColor != other.focusedLeadingIconColor) return false
        if (unfocusedLeadingIconColor != other.unfocusedLeadingIconColor) return false
        if (disabledLeadingIconColor != other.disabledLeadingIconColor) return false
        if (errorLeadingIconColor != other.errorLeadingIconColor) return false
        if (focusedTrailingIconColor != other.focusedTrailingIconColor) return false
        if (unfocusedTrailingIconColor != other.unfocusedTrailingIconColor) return false
        if (disabledTrailingIconColor != other.disabledTrailingIconColor) return false
        if (errorTrailingIconColor != other.errorTrailingIconColor) return false
        if (focusedLabelColor != other.focusedLabelColor) return false
        if (unfocusedLabelColor != other.unfocusedLabelColor) return false
        if (disabledLabelColor != other.disabledLabelColor) return false
        if (errorLabelColor != other.errorLabelColor) return false
        if (focusedPlaceholderColor != other.focusedPlaceholderColor) return false
        if (unfocusedPlaceholderColor != other.unfocusedPlaceholderColor) return false
        if (disabledPlaceholderColor != other.disabledPlaceholderColor) return false
        if (errorPlaceholderColor != other.errorPlaceholderColor) return false
        if (focusedSupportingTextColor != other.focusedSupportingTextColor) return false
        if (unfocusedSupportingTextColor != other.unfocusedSupportingTextColor) return false
        if (disabledSupportingTextColor != other.disabledSupportingTextColor) return false
        if (errorSupportingTextColor != other.errorSupportingTextColor) return false
        if (focusedPrefixColor != other.focusedPrefixColor) return false
        if (unfocusedPrefixColor != other.unfocusedPrefixColor) return false
        if (disabledPrefixColor != other.disabledPrefixColor) return false
        if (errorPrefixColor != other.errorPrefixColor) return false
        if (focusedSuffixColor != other.focusedSuffixColor) return false
        if (unfocusedSuffixColor != other.unfocusedSuffixColor) return false
        if (disabledSuffixColor != other.disabledSuffixColor) return false
        if (errorSuffixColor != other.errorSuffixColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = focusedTextColor.hashCode()
        result = 31 * result + unfocusedTextColor.hashCode()
        result = 31 * result + disabledTextColor.hashCode()
        result = 31 * result + errorTextColor.hashCode()
        result = 31 * result + focusedContainerColor.hashCode()
        result = 31 * result + unfocusedContainerColor.hashCode()
        result = 31 * result + disabledContainerColor.hashCode()
        result = 31 * result + errorContainerColor.hashCode()
        result = 31 * result + cursorColor.hashCode()
        result = 31 * result + errorCursorColor.hashCode()
        result = 31 * result + textSelectionColors.hashCode()
        result = 31 * result + focusedIndicatorColor.hashCode()
        result = 31 * result + unfocusedIndicatorColor.hashCode()
        result = 31 * result + disabledIndicatorColor.hashCode()
        result = 31 * result + errorIndicatorColor.hashCode()
        result = 31 * result + focusedLeadingIconColor.hashCode()
        result = 31 * result + unfocusedLeadingIconColor.hashCode()
        result = 31 * result + disabledLeadingIconColor.hashCode()
        result = 31 * result + errorLeadingIconColor.hashCode()
        result = 31 * result + focusedTrailingIconColor.hashCode()
        result = 31 * result + unfocusedTrailingIconColor.hashCode()
        result = 31 * result + disabledTrailingIconColor.hashCode()
        result = 31 * result + errorTrailingIconColor.hashCode()
        result = 31 * result + focusedLabelColor.hashCode()
        result = 31 * result + unfocusedLabelColor.hashCode()
        result = 31 * result + disabledLabelColor.hashCode()
        result = 31 * result + errorLabelColor.hashCode()
        result = 31 * result + focusedPlaceholderColor.hashCode()
        result = 31 * result + unfocusedPlaceholderColor.hashCode()
        result = 31 * result + disabledPlaceholderColor.hashCode()
        result = 31 * result + errorPlaceholderColor.hashCode()
        result = 31 * result + focusedSupportingTextColor.hashCode()
        result = 31 * result + unfocusedSupportingTextColor.hashCode()
        result = 31 * result + disabledSupportingTextColor.hashCode()
        result = 31 * result + errorSupportingTextColor.hashCode()
        result = 31 * result + focusedPrefixColor.hashCode()
        result = 31 * result + unfocusedPrefixColor.hashCode()
        result = 31 * result + disabledPrefixColor.hashCode()
        result = 31 * result + errorPrefixColor.hashCode()
        result = 31 * result + focusedSuffixColor.hashCode()
        result = 31 * result + unfocusedSuffixColor.hashCode()
        result = 31 * result + disabledSuffixColor.hashCode()
        result = 31 * result + errorSuffixColor.hashCode()
        return result
    }
}
