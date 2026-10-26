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
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Плитка застосунку: банер або іконка + підпис. Збільшується, коли на ній фокус. */
@SuppressLint("ViewConstructor")
class AppTile(
    context: Context,
    val app: AppEntry,
    val inFavorites: Boolean,
    widthPx: Int,
) : LinearLayout(context) {

    private val card = FrameLayout(context)
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
        val radius = dp(10).toFloat()

        card.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(0x33FFFFFF)
        }
        card.outlineProvider = ViewOutlineProvider.BACKGROUND
        card.clipToOutline = true
        card.elevation = dp(2).toFloat()

        val banner = app.banner
        if (banner != null) {
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(copyOf(banner))
            }
            card.addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
        } else {
            val size = (heightPx * 0.55f).toInt()
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(copyOf(app.icon))
            }
            card.addView(image, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        }

        focusRing.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(dp(3), Color.WHITE)
        }
        focusRing.alpha = 0f
        card.addView(focusRing, FrameLayout.LayoutParams(MATCH, MATCH))

        addView(card, LinearLayout.LayoutParams(widthPx, heightPx))

        label.text = app.label
        label.textSize = 13f
        label.setTextColor(LABEL_IDLE)
        label.setSingleLine(true)
        label.ellipsize = TextUtils.TruncateAt.END
        label.gravity = Gravity.CENTER_HORIZONTAL
        label.setPadding(dp(4), dp(8), dp(4), 0)
        addView(label, LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT))

        onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus -> showFocus(hasFocus) }
    }

    private fun showFocus(focused: Boolean) {
        val scale = if (focused) 1.1f else 1f
        animate().scaleX(scale).scaleY(scale).setDuration(140).start()
        translationZ = if (focused) dp(8).toFloat() else 0f
        focusRing.animate().alpha(if (focused) 1f else 0f).setDuration(140).start()
        card.elevation = (if (focused) dp(10) else dp(2)).toFloat()
        label.setTextColor(if (focused) Color.WHITE else LABEL_IDLE)
        // Під час фокусу довга назва прокручується
        label.ellipsize = if (focused) TextUtils.TruncateAt.MARQUEE else TextUtils.TruncateAt.END
        label.isSelected = focused
        label.marqueeRepeatLimit = -1
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
        val LABEL_IDLE: Int = 0xB3FFFFFF.toInt()
    }
}
