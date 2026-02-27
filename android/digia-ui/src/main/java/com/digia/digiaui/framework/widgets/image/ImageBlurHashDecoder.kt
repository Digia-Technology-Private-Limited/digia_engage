package com.digia.digiaui.framework.widgets.image

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign

/**
 * BlurHash decoder for Android
 * Decodes BlurHash strings to Bitmap images
 * 
 * Based on: https://github.com/woltapp/blurhash
 */
object BlurHashDecoder {
    
    private val charMap = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"
        .mapIndexed { index, char -> char to index }
        .toMap()

    fun decode(blurHash: String?, width: Int = 32, height: Int = 32, punch: Float = 1f): Bitmap? {
        if (blurHash == null || blurHash.length < 6) return null

        val sizeFlag = decode83(blurHash, 0, 1)
        val numCompX = (sizeFlag % 9) + 1
        val numCompY = (sizeFlag / 9) + 1

        val expectedLength = 4 + 2 * numCompX * numCompY
        if (blurHash.length != expectedLength) return null

        val quantisedMaximumValue = decode83(blurHash, 1, 2)
        val maximumValue = ((quantisedMaximumValue + 1).toFloat() / 166f) * punch

        val colors = Array(numCompX * numCompY) { i ->
            if (i == 0) {
                decodeDC(decode83(blurHash, 2, 6))
            } else {
                val startIndex = 4 + i * 2
                decodeAC(decode83(blurHash, startIndex, startIndex + 2), maximumValue)
            }
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f

                for (j in 0 until numCompY) {
                    for (i in 0 until numCompX) {
                        val basis = (cos((Math.PI * x * i) / width) *
                                cos((Math.PI * y * j) / height)).toFloat()
                        val color = colors[i + j * numCompX]
                        r += color[0] * basis
                        g += color[1] * basis
                        b += color[2] * basis
                    }
                }

                pixels[x + y * width] = android.graphics.Color.rgb(
                    linearToSRGB(r),
                    linearToSRGB(g),
                    linearToSRGB(b)
                )
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun decode83(str: String, from: Int, to: Int): Int {
        var value = 0
        for (i in from until to) {
            value = value * 83 + (charMap[str[i]] ?: 0)
        }
        return value
    }

    private fun decodeDC(value: Int): FloatArray {
        val r = value shr 16
        val g = (value shr 8) and 255
        val b = value and 255
        return floatArrayOf(sRGBToLinear(r), sRGBToLinear(g), sRGBToLinear(b))
    }

    private fun decodeAC(value: Int, maximumValue: Float): FloatArray {
        val quantR = value / (19 * 19)
        val quantG = (value / 19) % 19
        val quantB = value % 19
        return floatArrayOf(
            signPow((quantR - 9f) / 9f, 2f) * maximumValue,
            signPow((quantG - 9f) / 9f, 2f) * maximumValue,
            signPow((quantB - 9f) / 9f, 2f) * maximumValue
        )
    }

    private fun signPow(value: Float, exp: Float): Float =
        sign(value) * abs(value).pow(exp)

    private fun linearToSRGB(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) {
            (v * 12.92f * 255f + 0.5f).toInt()
        } else {
            ((1.055f * v.pow(1f / 2.4f) - 0.055f) * 255f + 0.5f).toInt()
        }
    }

    private fun sRGBToLinear(value: Int): Float {
        val v = value / 255f
        return if (v <= 0.04045f) v / 12.92f
        else ((v + 0.055f) / 1.055f).pow(2.4f)
    }
}