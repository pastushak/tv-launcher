package ua.pastushak.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import kotlin.math.max

/** Фони: фото або градієнти. Уміє готувати зображення під екран і його розмиту копію для скла. */
object Backgrounds {

    class Preset(val name: String, val colors: IntArray, val imageRes: Int = 0)

    val presets: List<Preset> = listOf(
        Preset("Карпати (фото)", IntArray(0), R.drawable.bg_carpathians),
        Preset("Теплий вечір", colors(0xFF1F1E1D, 0xFF3A2A22, 0xFF6B3A2A)),
        Preset("Графіт", colors(0xFF141413, 0xFF262624, 0xFF3A3936)),
        Preset("Нічне небо", colors(0xFF0F2027, 0xFF203A43, 0xFF2C5364)),
        Preset("Глибокий океан", colors(0xFF000428, 0xFF002C5F, 0xFF004E92)),
        Preset("Карпатський ліс", colors(0xFF0B1F17, 0xFF12372A, 0xFF1F5E46)),
    )

    fun clampIndex(index: Int): Int = index.coerceIn(0, presets.lastIndex)

    /** Малює фон точно під розмір екрана (з обрізанням по центру, як centerCrop). */
    fun render(context: Context, index: Int, width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val preset = presets[clampIndex(index)]

        if (preset.imageRes != 0) {
            val res = context.resources
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(res, preset.imageRes, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) {
                sample *= 2
            }
            val src = BitmapFactory.decodeResource(
                res, preset.imageRes, BitmapFactory.Options().apply { inSampleSize = sample }
            )
            if (src != null) {
                val scale = max(width / src.width.toFloat(), height / src.height.toFloat())
                val dw = src.width * scale
                val dh = src.height * scale
                val left = (width - dw) / 2f
                val top = (height - dh) / 2f
                canvas.drawBitmap(src, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                src.recycle()
            }
        } else {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, preset.colors).apply {
                setBounds(0, 0, width, height)
                draw(canvas)
            }
        }
        return out
    }

    /** Сильно розмита зменшена копія — основа для «скла». */
    fun blur(source: Bitmap): Bitmap {
        val w = 192
        val h = max(1, source.height * w / source.width)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== source) small.recycle()

        repeat(3) { boxBlur(pixels, w, h, 5) }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var a = 0; var red = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val c = px[row + (x + k).coerceIn(0, w - 1)]
                    a += c ushr 24; red += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                    n++
                }
                tmp[row + x] = ((a / n) shl 24) or ((red / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var a = 0; var red = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val c = tmp[(y + k).coerceIn(0, h - 1) * w + x]
                    a += c ushr 24; red += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                    n++
                }
                px[y * w + x] = ((a / n) shl 24) or ((red / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }

    private fun colors(vararg values: Long): IntArray = IntArray(values.size) { values[it].toInt() }
}
