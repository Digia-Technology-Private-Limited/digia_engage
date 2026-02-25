package com.digia.digiaui.framework.utils

class NumUtil {
    companion object {
        /**
         * Attempts to parse a dynamic input into a double.
         *
         * Handles the following cases:
         * - String representations of numbers, including hexadecimal
         * - Special string values 'inf' or 'infinity' (case-insensitive)
         * - Numeric types (int, double)
         *
         * Returns:
         * - A valid double if parsing is successful
         * - Double.POSITIVE_INFINITY for 'inf' or 'infinity' strings
         * - null if parsing fails or input is of an unsupported type
         */
        fun toDouble(input: Any?): Double? {
            return when (input) {
                is String -> {
                    val lower = input.lowercase()
                    if (lower == "inf" || lower == "infinity") {
                        Double.POSITIVE_INFINITY
                    } else if (input.startsWith("0x")) {
                        try {
                            input.substring(2).toInt(16).toDouble()
                        } catch (e: NumberFormatException) {
                            null
                        }
                    } else {
                        input.toDoubleOrNull()
                    }
                }
                is Number -> input.toDouble()
                else -> null
            }
        }

        /**
         * Attempts to parse a dynamic input into an integer.
         *
         * Handles the following cases:
         * - String representations of numbers, including hexadecimal
         * - Numeric types (int, double)
         *
         * Returns:
         * - A valid integer if parsing is successful
         * - null if parsing fails or input is of an unsupported type
         */
        fun toInt(value: Any?): Int? {
            return when (value) {
                is String -> {
                    val lower = value.lowercase()
                    if (lower.startsWith("0x")) {
                        try {
                            value.substring(2).toInt(16)
                        } catch (e: NumberFormatException) {
                            null
                        }
                    } else {
                        value.toIntOrNull()
                    }
                }
                is Number -> value.toInt()
                else -> null
            }
        }

        /**
         * Attempts to parse a dynamic input into a boolean value.
         *
         * Handles the following cases:
         * - Boolean values
         * - String representations of boolean values (case-insensitive)
         *
         * Returns:
         * - A valid boolean if parsing is successful
         * - null if parsing fails or input is of an unsupported type
         */
        fun toBool(value: Any?): Boolean? {
            return when (value) {
                is Boolean -> value
                is String -> when (value.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
                else -> null
            }
        }

        /**
         * Attempts to parse a dynamic input into a float.
         *
         * Handles the following cases:
         * - String representations of numbers, including hexadecimal
         * - Special string values 'inf' or 'infinity' (case-insensitive)
         * - Numeric types (int, double)
         *
         * Returns:
         * - A valid float if parsing is successful
         * - Float.POSITIVE_INFINITY for 'inf' or 'infinity' strings
         * - null if parsing fails or input is of an unsupported type
         */
        fun toFloat(input: Any?): Float? {
            return when (input) {
                is String -> {
                    val lower = input.lowercase()
                    if (lower == "inf" || lower == "infinity") {
                        Float.POSITIVE_INFINITY
                    } else if (input.startsWith("0x")) {
                        try {
                            input.substring(2).toInt(16).toFloat()
                        } catch (e: NumberFormatException) {
                            null
                        }
                    } else {
                        input.toFloatOrNull()
                    }
                }
                is Number -> input.toFloat()
                else -> null
            }
        }

        fun toNum(value: Any?): Number? {
            return toInt(value) ?: toFloat(value) ?: toDouble(value)
        }
    }
}