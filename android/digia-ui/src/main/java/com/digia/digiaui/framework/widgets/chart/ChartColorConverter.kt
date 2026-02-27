package com.digia.digiaui.framework.widgets.chart

/**
 * Utility for converting color formats between Flutter and CSS conventions.
 *
 * Flutter uses AARRGGBB format (alpha prefix):
 * - `0xffF10606` → Alpha: ff, R: F1, G: 06, B: 06
 *
 * CSS/Chart.js uses RRGGBBAA format (alpha suffix):
 * - `#F10606ff` → R: F1, G: 06, B: 06, Alpha: ff
 *
 * This converter handles the transformation for Chart.js compatibility.
 */
object ChartColorConverter {

    /**
     * Transforms Flutter AARRGGBB hex colors to CSS RRGGBBAA format. Passes through functional
     * notations (rgba, hsla, var) and named colors.
     *
     * @param color The color string in Flutter or CSS format
     * @return Normalized CSS color string, or null if input is invalid
     */
    fun normalizeColor(color: Any?): String? {
        if (color !is String || color.isEmpty()) return null

        // Passthrough for functional notations (rgba, hsla, var) or named colors
        if (color.startsWith("rgb") ||
                        color.startsWith("hsl") ||
                        color.startsWith("var") ||
                        !color.contains(Regex("[0-9a-fA-F]"))
        ) {
            return color
        }

        // Strip common prefixes (# and 0x)
        val cleanHex = color.replace("#", "").replace("0x", "").replace("0X", "")

        // Strict hex check to avoid corrupting named colors like 'red'
        if (!cleanHex.matches(Regex("^[0-9a-fA-F]+$"))) {
            return color
        }

        return when (cleanHex.length) {
            // Standard RGB (RRGGBB) → CSS #RRGGBB
            6 -> "#$cleanHex"

            // Flutter ARGB (AARRGGBB) → CSS #RRGGBBAA
            8 -> {
                val alpha = cleanHex.substring(0, 2)
                val rgb = cleanHex.substring(2)
                "#$rgb$alpha"
            }

            // Short hex formats
            3 -> "#$cleanHex" // #RGB
            4 -> {
                // #ARGB → #RGBA
                val alpha = cleanHex.substring(0, 1)
                val rgb = cleanHex.substring(1)
                "#$rgb$alpha"
            }

            // Return original if unknown format
            else -> color
        }
    }

    /**
     * Normalizes all color properties in a map recursively. Used for processing dataset and options
     * objects.
     */
    @Suppress("UNCHECKED_CAST")
    fun normalizeColorsInMap(map: Map<String, Any?>): Map<String, Any?> {
        val colorKeys =
                setOf(
                        "borderColor",
                        "backgroundColor",
                        "pointBackgroundColor",
                        "pointBorderColor",
                        "hoverBackgroundColor",
                        "hoverBorderColor",
                        "color",
                        "textColor"
                )

        return map.mapValues { (key, value) ->
            when {
                colorKeys.contains(key) && value is String -> normalizeColor(value)
                colorKeys.contains(key) && value is List<*> -> value.map { normalizeColor(it) }
                value is Map<*, *> -> normalizeColorsInMap(value as Map<String, Any?>)
                else -> value
            }
        }
    }
}
