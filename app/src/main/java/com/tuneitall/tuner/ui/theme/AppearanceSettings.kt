package com.tuneitall.tuner.ui.theme

import org.json.JSONException
import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ThemePalette { INTONIVA, MATERIAL_YOU, OCEAN, EMBER, IRIS, SLATE, PAPER, CUSTOM }
enum class ThemeLayout { CLASSIC, MATERIAL, CUSTOM }
enum class ThemeFont { SYSTEM, SERIF, MONOSPACE }

data class ThemeColors(
    val accent: Int,
    val background: Int,
    val surface: Int,
    val text: Int,
    val muted: Int,
    val outline: Int,
) {
    init { require(listOf(accent, background, surface, text, muted, outline).all { it in 0..0xFFFFFF }) }
}

fun paletteColors(palette: ThemePalette, dark: Boolean): ThemeColors = when (palette) {
    ThemePalette.INTONIVA, ThemePalette.MATERIAL_YOU, ThemePalette.CUSTOM -> if (dark) {
        ThemeColors(0x63D17A, 0x101010, 0x101010, 0xF4F1EA, 0xF4F1EA, 0xF4F1EA)
    } else {
        ThemeColors(0x166534, 0xFAF9F6, 0xFAF9F6, 0x111111, 0x111111, 0x111111)
    }
    ThemePalette.OCEAN -> if (dark) {
        ThemeColors(0x74B9F7, 0x0E1622, 0x172536, 0xEEF5FA, 0xA7B8CA, 0x7890A7)
    } else {
        ThemeColors(0x1574B8, 0xF5F9FC, 0xE6F1FA, 0x102C42, 0x4A667A, 0x5E7687)
    }
    ThemePalette.EMBER -> if (dark) {
        ThemeColors(0xFFB785, 0x201612, 0x302019, 0xFCECE1, 0xD9B8A3, 0xA18370)
    } else {
        ThemeColors(0x9C4B14, 0xFFF8F2, 0xFFE8D9, 0x322011, 0x725949, 0x9F7255)
    }
    ThemePalette.IRIS -> if (dark) {
        ThemeColors(0xCDB3F4, 0x1A1522, 0x282032, 0xF3EBFB, 0xBFB0CE, 0x9E8DAD)
    } else {
        ThemeColors(0x6F459C, 0xFAF7FE, 0xF1EAFD, 0x251A37, 0x675878, 0x917EA5)
    }
    ThemePalette.SLATE -> if (dark) {
        ThemeColors(0xADC6DF, 0x141920, 0x202832, 0xEEF3FA, 0xB2BBCB, 0x7E8B9F)
    } else {
        ThemeColors(0x495B70, 0xF7F8FA, 0xE8EDF3, 0x17202B, 0x5B697A, 0x7B8999)
    }
    ThemePalette.PAPER -> if (dark) {
        ThemeColors(0xD8C07E, 0x201D16, 0x302A1C, 0xF9EDD0, 0xCABB93, 0x9B8B63)
    } else {
        ThemeColors(0x806D32, 0xFCF6E9, 0xF4EAD1, 0x332B1F, 0x71664D, 0x9C8C69)
    }
}

data class AppearanceSettings(
    val lightTheme: ThemePalette = ThemePalette.INTONIVA,
    val darkTheme: ThemePalette = ThemePalette.INTONIVA,
    val customLight: ThemeColors = paletteColors(ThemePalette.INTONIVA, false),
    val customDark: ThemeColors = paletteColors(ThemePalette.INTONIVA, true),
    val layout: ThemeLayout = ThemeLayout.CLASSIC,
    val cornerRadius: Int = 8,
    val font: ThemeFont = ThemeFont.SYSTEM,
    val textScale: Int = 100,
    val highContrast: Boolean = false,
    val blackBackground: Boolean = false,
) {
    init {
        require(cornerRadius in 0..28)
        require(textScale in 85..130)
    }

    fun palette(dark: Boolean): ThemePalette = if (dark) darkTheme else lightTheme
    fun colors(dark: Boolean): ThemeColors = if (palette(dark) == ThemePalette.CUSTOM) {
        if (dark) customDark else customLight
    } else paletteColors(palette(dark), dark)
}

object AppearanceCodec {
    fun encode(value: AppearanceSettings): String = JSONObject()
        .put("light", value.lightTheme.name).put("dark", value.darkTheme.name)
        .put("customLight", encodeColors(value.customLight)).put("customDark", encodeColors(value.customDark))
        .put("layout", value.layout.name).put("corners", value.cornerRadius)
        .put("font", value.font.name).put("textScale", value.textScale)
        .put("contrast", value.highContrast).put("black", value.blackBackground).toString()

    fun decode(encoded: String): AppearanceSettings {
        val defaults = AppearanceSettings()
        if (encoded.length > 4096) return defaults
        val json = try { JSONObject(encoded) } catch (_: JSONException) { return defaults }
        return AppearanceSettings(
            lightTheme = json.enumValue("light", defaults.lightTheme),
            darkTheme = json.enumValue("dark", defaults.darkTheme),
            customLight = decodeColors(json.optJSONObject("customLight"), defaults.customLight),
            customDark = decodeColors(json.optJSONObject("customDark"), defaults.customDark),
            layout = json.enumValue("layout", defaults.layout),
            cornerRadius = json.intValue("corners", defaults.cornerRadius, 0..28),
            font = json.enumValue("font", defaults.font),
            textScale = json.intValue("textScale", defaults.textScale, 85..130),
            highContrast = json.opt("contrast") as? Boolean ?: defaults.highContrast,
            blackBackground = json.opt("black") as? Boolean ?: defaults.blackBackground,
        )
    }

    private fun encodeColors(colors: ThemeColors): JSONObject = JSONObject()
        .put("accent", colors.accent).put("background", colors.background).put("surface", colors.surface)
        .put("text", colors.text).put("muted", colors.muted).put("outline", colors.outline)

    private fun decodeColors(json: JSONObject?, fallback: ThemeColors): ThemeColors = if (json == null) fallback else ThemeColors(
        json.intValue("accent", fallback.accent, 0..0xFFFFFF),
        json.intValue("background", fallback.background, 0..0xFFFFFF),
        json.intValue("surface", fallback.surface, 0..0xFFFFFF),
        json.intValue("text", fallback.text, 0..0xFFFFFF),
        json.intValue("muted", fallback.muted, 0..0xFFFFFF),
        json.intValue("outline", fallback.outline, 0..0xFFFFFF),
    )

    private inline fun <reified T : Enum<T>> JSONObject.enumValue(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == opt(key) } ?: fallback

    private fun JSONObject.intValue(key: String, fallback: Int, range: IntRange): Int {
        val number = (opt(key) as? Number)?.toDouble() ?: return fallback
        return if (number.isFinite() && number % 1 == 0.0 && number >= range.first && number <= range.last) number.toInt() else fallback
    }
}

fun colorContrast(first: Int, second: Int): Double {
    fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val value = (rgb shr shift and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
    val a = luminance(first)
    val b = luminance(second)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

fun readableColor(preferred: Int, background: Int, minimum: Double = 4.5): Int =
    if (colorContrast(preferred, background) >= minimum) preferred
    else if (colorContrast(0, background) >= colorContrast(0xFFFFFF, background)) 0 else 0xFFFFFF

fun readableAccent(preferred: Int, background: Int): Int {
    if (colorContrast(preferred, background) >= 4.5) return preferred
    val target = readableColor(preferred, background)
    for (step in 1..20) {
        val amount = step / 20.0
        fun channel(shift: Int): Int = (((preferred shr shift and 255) * (1 - amount)) +
            ((target shr shift and 255) * amount)).roundToInt()
        val mixed = channel(16) shl 16 or (channel(8) shl 8) or channel(0)
        if (colorContrast(mixed, background) >= 4.5) return mixed
    }
    return target
}

fun inTuneRgb(background: Int): Int = listOf(0x63D17A, 0x166534, 0x001208)
    .firstOrNull { colorContrast(it, background) >= 3.0 } ?: readableColor(0x63D17A, background, 3.0)

fun usesLightSystemBarIcons(background: Int): Boolean = colorContrast(0xFFFFFF, background) > colorContrast(0, background)

fun parseHexColor(value: String): Int? {
    val digits = value.trim().removePrefix("#")
    return digits.takeIf { it.length == 6 && it.all { char -> char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F' } }?.toIntOrNull(16)
}
