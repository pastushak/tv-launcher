package ua.pastushak.tvlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Плитка застосунку на склі: банер або іконка + підпис. Під фокусом трохи збільшується. */
@SuppressLint("ViewConstructor")
class AppTile(
    context: Context,
    val app: AppEntry,
    val inFavorites: Boolean,
    widthPx: Int,
) : LinearLayout(context) {

    private val card = GlassLayout(context)
    private val focusRing = View(context)
    private val label = TextView(context)

    init {
        orientation = LinearLayout.VERTICAL
        isFocusable = true
        isClickable = true
        isLongClickable = true
        clipChildren = false
        clipToPadding = false

        val heightPx = widthPx * 9 / 16
        val radius = dp(14).toFloat()

        card.cornerRadius = radius
        card.tintColor = 0x14FFFFFF
        card.borderColor = 0x26FFFFFF
        card.borderWidth = dp(1).toFloat().coerceAtLeast(1f)
        card.elevation = dp(2).toFloat()

        val banner = app.banner
        if (banner != null) {
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(copyOf(banner))
            }
            card.addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
        } else {
            val size = (heightPx * 0.5f).toInt()
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(copyOf(app.icon))
            }
            card.addView(image, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        }

        focusRing.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(dp(2), Theme.CREAM)
        }
        focusRing.alpha = 0f
        card.addView(focusRing, FrameLayout.LayoutParams(MATCH, MATCH))

        addView(card, LinearLayout.LayoutParams(widthPx, heightPx))

        label.text = app.label
        label.textSize = 12f
        label.setTextColor(Theme.CREAM_60)
        label.setSingleLine(true)
        label.ellipsize = TextUtils.TruncateAt.END
        label.gravity = Gravity.CENTER_HORIZONTAL
        label.letterSpacing = 0.02f
        label.setPadding(dp(4), dp(9), dp(4), 0)
        addView(label, LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT))

        onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus -> showFocus(hasFocus) }
    }

    private fun showFocus(focused: Boolean) {
        val scale = if (focused) 1.08f else 1f
        animate().scaleX(scale).scaleY(scale).setDuration(160).start()
        translationZ = if (focused) dp(8).toFloat() else 0f
        focusRing.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
        card.elevation = (if (focused) dp(12) else dp(2)).toFloat()
        card.tintColor = if (focused) 0x2EFFFFFF else 0x14FFFFFF
        label.setTextColor(if (focused) Theme.CREAM else Theme.CREAM_60)
        label.ellipsize = if (focused) TextUtils.TruncateAt.MARQUEE else TextUtils.TruncateAt.END
        label.marqueeRepeatLimit = -1
        label.isSelected = focused
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w / 2f
        pivotY = card.height / 2f
    }

    private fun copyOf(drawable: Drawable): Drawable =
        drawable.constantState?.newDrawable(resources)?.mutate() ?: drawable

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}

/** Палітра в теплому мінімалістичному стилі. */
object Theme {
    val CREAM: Int = 0xFFF5F1EA.toInt()
    val CREAM_80: Int = 0xCCF5F1EA.toInt()
    val CREAM_60: Int = 0x99F5F1EA.toInt()
    val CREAM_45: Int = 0x73F5F1EA.toInt()
    val CORAL: Int = 0xFFD97757.toInt()
    val INK: Int = 0xFF1F1E1C.toInt()
}
