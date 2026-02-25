import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlin.random.Random

object ColorUtil {
    /// Regular expression pattern to validate hex color strings.
    ///
    /// The pattern checks for valid hex color strings in the following formats:
    /// - "#RGB" or "#RRGGBB" (with or without the leading "#").
    /// - "#RRGGBBAA" (with or without the leading "#").
    ///
    /// The `R`, `G`, `B`, and `A` represent hexadecimal digits (0-9, A-F, a-f) that
    /// define the red, green, blue, and alpha components of the color, respectively.
    ///
    /// If the alpha value is not provided, the pattern assumes it to be 255 (fully opaque).
    /// The alpha value can be either a double ranging from 0.0 to 1.0, or a hexadecimal value
    /// ranging from 00 to FF (0 to 255 in decimal).
    val hexColorPattern = Regex("^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    /// Checks if a given hex color string is a valid color value.
    ///
    /// Example valid hex color strings:
    /// - "#FF0000" (red color)
    /// - "00FF00" (green color)
    /// - "#12345F80" (blue color with alpha value of 128)
    ///
    /// Parameters:
    /// - [hex]: The hex color string to be validated.
    ///
    /// Returns:
    /// - `true` if the hex string is a valid color value, otherwise `false`.
    fun isValidColorHex(hex: String): Boolean {
        return hexColorPattern.matches(hex)
    }

    ///
    /// Converts the given [hex] color string to the corresponding int.
    ///
    /// Note that when no alpha/opacity is specified, 0xFF is assumed.
    ///
    fun hexToInt(hex: String): Int {
        var hexDigits = if (hex.startsWith('#')) hex.substring(1) else hex

        // Handle 3-digit hex (RGB -> RRGGBB)
        if (hexDigits.length == 3) {
            hexDigits = hexDigits.map { "$it$it" }.joinToString("")
        }

        // Handle 6-digit hex (RRGGBB -> FF RRGGBB)
        if (hexDigits.length == 6) {
            // Add alpha FF (fully opaque)
            hexDigits = "FF$hexDigits"
        }

        // Now hexDigits should be 8 characters (AARRGGBB format)
        if (hexDigits.length != 8) {
            throw IllegalArgumentException("Invalid hex color format: $hex")
        }

        // Parse as unsigned 32-bit integer
        return hexDigits.toLong(16).toInt()
    }

    ///
    /// Converts the given [hex] color string to [Color] Object.
    ///
    /// Returns null, if hex isn't valid.
    ///
    fun fromHexString(hex: String): Color {
        val hexIntValue = hexToInt(hex)
        return Color(hexIntValue)
    }

    fun tryFromHexString(hex: String): Color? {
        return try {
            fromHexString(hex)
        } catch (e: Exception) {
            null
        }
    }

    /// Tries to create a color from comma-separated red, green, blue, and opacity values
    /// in String.
    ///
    /// * 1st position is [red], from 0 to 255.
    /// * 2nd position is [green], from 0 to 255.
    /// * 3rd position is [blue], from 0 to 255.
    /// * 4th position (optional) is `opacity`.
    ///   Two formats are accepted.
    ///   * Double: 0.0 being transparent, 1.0 being fully opaque.
    ///   * Hex: 0 being transparent, 255 being full opaque.
    ///
    /// Out of range values are brought into range using modulo operator.
    ///
    fun fromRgbaString(rgba: String): Color {
        // Extract the individual components
        val components = rgba.split(',')

        // Invalid format, return null
        if (components.size < 3) {
            throw IllegalArgumentException("Invalid RGBA format")
        }

        // Extract R,G,B,A and ensure that the values are within the valid range
        val r = components[0].toInt().coerceIn(0, 255)
        val g = components[1].toInt().coerceIn(0, 255)
        val b = components[2].toInt().coerceIn(0, 255)
        var a = 1.0f
        if (components.size == 4) {
            val alphaStr = components[3]
            val alphaInt = alphaStr.toIntOrNull()
            if (alphaInt != null) {
                a = (alphaInt.coerceIn(0, 255) / 255.0f)
            } else {
                a = alphaStr.toFloatOrNull()?.coerceIn(0.0f, 1.0f) ?: 1.0f
            }
        }

        return Color(r / 255.0f, g / 255.0f, b / 255.0f, a)
    }

    fun tryFromRgbaString(rgba: String): Color? {
        return try {
            fromRgbaString(rgba)
        } catch (e: Exception) {
            null
        }
    }

    ///
    /// Tries to convert a string value to Color Object.
    ///
    fun fromString(value: String?): Color? {
        if (value == null) return null

        if (value.isEmpty()) return null

        // Try hex format first
        val trimmedValue = value.trim()
        if (isValidColorHex(trimmedValue)) {
            return tryFromHexString(trimmedValue)
        }

        // Try RGBA format
        return tryFromRgbaString(trimmedValue)
    }

    ///
    /// Converts a string value to Color Object.
    ///
    /// Accepts fallback color when string parsing fails.
    ///
    fun fromStringOrDefault(value: String, defaultColor: Color = Color.Transparent): Color {
        return fromString(value) ?: defaultColor
    }

    ///
    /// Converts the given integer [i] to a hex string with a leading #.
    ///
    /// Note that only the RGB values will be returned (like #RRGGBB), so
    /// and alpha/opacity value will be stripped.
    ///
    fun intToHex(i: Int): String {
        require(i in 0..0xFFFFFFFF)
        return "#${(i and 0xFFFFFF or 0x1000000).toString(16).substring(1).uppercase()}"
    }

    ///
    /// Fills up the given 3 char [hex] string to 6 char hex string.
    ///
    /// Will add a # to the [hex] string if it is missing.
    ///
    fun fillUpHex(hex: String): String {
        var h = hex
        if (!h.startsWith('#')) {
            h = "#$h"
        }

        if (h.length == 7 || h.length == 9) {
            return h
        }

        var filledUp = ""
        for (char in h) {
            if (char == '#') {
                filledUp += char
            } else {
                filledUp += "$char$char"
            }
        }
        return filledUp
    }

    /// Converts `androidx.compose.ui.graphics.Color` to the 6/8 digits HEX [String].
    ///
    /// Prefixes a hash (`#`) sign if [includeHashSign] is set to `true`.
    /// The result will be provided as UPPER CASE. Hex can be returned without alpha
    /// channel information (transparency), with the [skipAlphaIfOpaque] flag set to `true`.
    fun toHexString(
        color: Color,
        includeHashSign: Boolean = true,
        skipAlphaIfOpaque: Boolean = true
    ): String {
        val alpha = (color.alpha * 255).roundToInt() and 0xff
        val red = (color.red * 255).roundToInt() and 0xff
        val green = (color.green * 255).roundToInt() and 0xff
        val blue = (color.blue * 255).roundToInt() and 0xff

        return if (skipAlphaIfOpaque && alpha == 255) {
            // Return 6-digit hex (#RRGGBB)
            val hex = (if (includeHashSign) "#" else "") +
                    padRadix(red) +
                    padRadix(green) +
                    padRadix(blue)
            hex.uppercase()
        } else {
            // Return 8-digit hex (#AARRGGBB)
            val hex = (if (includeHashSign) "#" else "") +
                    padRadix(alpha) +
                    padRadix(red) +
                    padRadix(green) +
                    padRadix(blue)
            hex.uppercase()
        }
    }

    // Shorthand for padLeft of RadixString, DRY.
    private fun padRadix(value: Int) = value.toString(16).padStart(2, '0')

    fun randomColor(opacity: Float = 0.3f): Color {
        val rgb = Random.nextInt(0xFFFFFF)
        return Color(rgb).copy(alpha = opacity)
    }

    /// Extension function to parse color with better error handling
    fun String.toColorOrNull(): Color? = fromString(this)

    fun String.toColor(default: Color = Color.Transparent): Color = fromStringOrDefault(this, default)
}