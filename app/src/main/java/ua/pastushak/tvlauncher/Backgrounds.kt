package ua.pastushak.tvlauncher

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/** Готові фони-градієнти. */
object Backgrounds {

    class Preset(val name: String, val colors: IntArray)

    val presets: List<Preset> = listOf(
        Preset("Нічне небо", colors(0xFF0F2027, 0xFF203A43, 0xFF2C5364)),
        Preset("Фіолетовий вечір", colors(0xFF1A1A2E, 0xFF16213E, 0xFF533483)),
        Preset("Карпатський ліс", colors(0xFF0B1F17, 0xFF12372A, 0xFF1F5E46)),
        Preset("Глибокий океан", colors(0xFF000428, 0xFF002C5F, 0xFF004E92)),
        Preset("Захід сонця", colors(0xFF23074D, 0xFF5B1A4A, 0xFF8E2E3B)),
        Preset("Графіт", colors(0xFF101010, 0xFF1E1E1E, 0xFF2E2E2E)),
    )

    fun drawable(index: Int): Drawable {
        val preset = presets[index.coerceIn(0, presets.lastIndex)]
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, preset.colors)
    }

    private fun colors(vararg values: Long): IntArray = IntArray(values.size) { values[it].toInt() }
}
