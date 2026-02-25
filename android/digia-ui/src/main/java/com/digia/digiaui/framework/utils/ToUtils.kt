package com.digia.digiaui.framework.utils

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object ToUtils {
    /**
     * Converts a dynamic value to [PaddingValues].
     *
     * Supports various input types:
     * - List of numbers
     * - String of comma-separated numbers
     * - Single number
     * - Map with specific keys
     *
     * Returns [or] (default: PaddingValues(0.dp)) if conversion fails.
     */
    fun edgeInsets(value: Any?, or: PaddingValues = PaddingValues(0.dp)): PaddingValues {
        // Return default value if input is null
        if (value == null) return or

        // Handle List input
        if (value is List<*>) {
            val doubles = value.mapNotNull { NumUtil.toDouble(it) }
            return _toPaddingFromList(doubles) ?: or
        }

        // Handle String input (comma-separated values)
        if (value is String) {
            val doubles = value.split(',').mapNotNull { NumUtil.toDouble(it) }
            return _toPaddingFromList(doubles) ?: or
        }

        // Handle single number input
        if (value is Number) {
            return _toPaddingFromList(listOf(value.toDouble())) ?: or
        }

        // Handle Map input
        if (value is Map<*, *>) {
            // Check for 'all' key
            if (value["all"] != null) {
                val all = NumUtil.toDouble(value["all"]) ?: 0.0
                return PaddingValues(all.dp)
            }

            // Check for 'horizontal' and 'vertical' keys
            if (value["horizontal"] != null && value["vertical"] != null) {
                val horizontal = NumUtil.toDouble(value["horizontal"]) ?: 0.0
                val vertical = NumUtil.toDouble(value["vertical"]) ?: 0.0
                return PaddingValues(horizontal = horizontal.dp, vertical = vertical.dp)
            }

            // Use LTRB values
            val left = NumUtil.toDouble(value["left"]) ?: 0.0
            val top = NumUtil.toDouble(value["top"]) ?: 0.0
            val right = NumUtil.toDouble(value["right"]) ?: 0.0
            val bottom = NumUtil.toDouble(value["bottom"]) ?: 0.0
            return PaddingValues(start = left.dp, top = top.dp, end = right.dp, bottom = bottom.dp)
        }

        // Return default value if no conversion was possible
        return or
    }

    private fun _toPaddingFromList(values: List<Double>): PaddingValues? {
        return when (values.size) {
            1 -> PaddingValues(values[0].dp)
            2 -> PaddingValues(horizontal = values[0].dp, vertical = values[1].dp)
            4 -> PaddingValues(start = values[0].dp, top = values[1].dp, end = values[2].dp, bottom = values[3].dp)
            else -> null
        }
    }

    fun borderRadius(value: Any?, or: RoundedCornerShape = RoundedCornerShape(0.dp)): RoundedCornerShape {
        val fallBackValue = or
        if (value == null) return fallBackValue

        if (value is List<*>) {
            val doubles = value.map { NumUtil.toDouble(it) ?: 0.0 }
            return _toRoundedCornerShapeFromList(doubles) ?: fallBackValue
        }

        if (value is String) {
            val doubles = value.split(',').map { NumUtil.toDouble(it) ?: 0.0 }
            return _toRoundedCornerShapeFromList(doubles) ?: fallBackValue
        }

        if (value is Number) {
            return _toRoundedCornerShapeFromList(listOf(value.toDouble())) ?: fallBackValue
        }

        if (value is Map<*, *>) {
            return RoundedCornerShape(
                topStart = toRadius(value["topLeft"]),
                topEnd = toRadius(value["topRight"]),
                bottomEnd = toRadius(value["bottomRight"]),
                bottomStart = toRadius(value["bottomLeft"])
            )
        }

        return fallBackValue
    }

    private fun _toRoundedCornerShapeFromList(values: List<Double>): RoundedCornerShape? {
        return when (values.size) {
            1 -> RoundedCornerShape(values[0].dp)
            4 -> RoundedCornerShape(
                topStart = values[0].dp,
                topEnd = values[1].dp,
                bottomEnd = values[2].dp,
                bottomStart = values[3].dp
            )
            else -> null
        }
    }

    private fun toRadius(value: Any?): androidx.compose.ui.unit.Dp {
        return (NumUtil.toDouble(value) ?: 0.0).dp
    }
}