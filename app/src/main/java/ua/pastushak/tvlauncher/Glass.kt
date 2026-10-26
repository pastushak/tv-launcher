package ua.pastushak.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout

/**
 * Спільне розмите зображення фону для всіх «скляних» панелей.
 * Розмиття робиться один раз при зміні фону, а кожна панель просто малює
 * свій шматок цього зображення — тому ефект працює навіть на старому Android без RenderEffect.
 */
object GlassBackground {
    var blurred: Bitmap? = null
        private set
    var screenWidth = 1
        private set
    var screenHeight = 1
        private set

    /** Наскільки затемнено фон (0..1), коли сторінку прокручено вниз. */
    var dim = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidateAll()
            }
        }

    private val views = LinkedHashSet<GlassLayout>()

    fun set(bitmap: Bitmap, width: Int, height: Int) {
        blurred = bitmap
        screenWidth = width
        screenHeight = height
        invalidateAll()
    }

    internal fun attach(view: GlassLayout) {
        views.add(view)
    }

    internal fun detach(view: GlassLayout) {
        views.remove(view)
    }

    private fun invalidateAll() {
        for (v in views.toList()) v.invalidate()
    }
}

/** Контейнер із матовим склом: розмитий фон + легкий білий відтінок + тонка рамка. */
class GlassLayout(context: Context) : FrameLayout(context) {

    var cornerRadius = 0f
        set(value) {
            field = value
            invalidateOutline()
            invalidate()
        }
    var tintColor: Int = 0x1FFFFFFF
        set(value) {
            field = value
            invalidate()
        }
    var borderColor: Int = 0x33FFFFFF
        set(value) {
            field = value
            invalidate()
        }
    var borderWidth = 1f
        set(value) {
            field = value
            invalidate()
        }

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val shaderMatrix = Matrix()
    private val location = IntArray(2)
    private var shader: BitmapShader? = null
    private var shaderSource: Bitmap? = null

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { invalidate() }

    init {
        setWillNotDraw(false)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        clipToOutline = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
        GlassBackground.attach(this)
    }

    override fun onDetachedFromWindow() {
        val observer = viewTreeObserver
        if (observer.isAlive) observer.removeOnScrollChangedListener(scrollListener)
        GlassBackground.detach(this)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)

        val bitmap = GlassBackground.blurred
        if (bitmap != null) {
            if (shaderSource !== bitmap) {
                shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                shaderSource = bitmap
            }
            val s = shader
            if (s != null) {
                getLocationInWindow(location)
                shaderMatrix.setScale(
                    GlassBackground.screenWidth / bitmap.width.toFloat(),
                    GlassBackground.screenHeight / bitmap.height.toFloat()
                )
                shaderMatrix.postTranslate(-location[0].toFloat(), -location[1].toFloat())
                s.setLocalMatrix(shaderMatrix)
                glassPaint.shader = s
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, glassPaint)
            }
        }

        val dim = GlassBackground.dim
        if (dim > 0f) {
            fillPaint.color = Color.argb((dim * 120).toInt(), 0x14, 0x14, 0x13)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
        }

        fillPaint.color = tintColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)

        if (borderWidth > 0f) {
            val half = borderWidth / 2f
            rect.inset(half, half)
            borderPaint.strokeWidth = borderWidth
            borderPaint.color = borderColor
            val r = (cornerRadius - half).coerceAtLeast(0f)
            canvas.drawRoundRect(rect, r, r, borderPaint)
        }
        super.onDraw(canvas)
    }
}
