package com.cheraz.pixel2dedit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Tileset completo en cuadrícula, dentro de un ScrollView.
 * Toque = 1 tile. Mantener presionado y arrastrar = rectángulo de varios tiles (árbol entero, casa...).
 */
class PaletteView(context: Context, var tileset: Tileset) : View(context) {

    var selected = 0
        private set
    var selW = 1
        private set
    var selH = 1
        private set
    var dimmed = false
        set(v) { field = v; invalidate() }
    var onSelect: ((id: Int, w: Int, h: Int) -> Unit)? = null

    fun setSelection(id: Int, w: Int, h: Int) {
        selected = id; selW = w; selH = h
        invalidate()
    }

    private val dp = resources.displayMetrics.density
    private val tilePaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val checkA = Paint().apply { color = Color.rgb(58, 54, 70) }
    private val checkB = Paint().apply { color = Color.rgb(46, 43, 56) }
    private val selPaint = Paint().apply { color = Color.rgb(255, 210, 60); style = Paint.Style.STROKE; strokeWidth = 3f * resources.displayMetrics.density }
    private val dimPaint = Paint().apply { color = Color.argb(170, 20, 18, 26) }
    private val src = Rect()
    private val dst = Rect()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var selecting = false
    private var anchorC = 0
    private var anchorR = 0

    private val longPress = Runnable {
        if (dimmed) return@Runnable
        selecting = true
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val (c, r) = cellAt(downX, downY)
        anchorC = c; anchorR = r
        dragTo(c, r)
    }

    private fun cellAt(x: Float, y: Float): Pair<Int, Int> {
        val s = cell()
        return Pair((x / s).toInt().coerceIn(0, tileset.cols - 1), (y / s).toInt().coerceIn(0, tileset.rows - 1))
    }

    /** Rectángulo entre el ancla y el dedo, máx. 6×6. */
    private fun dragTo(c: Int, r: Int) {
        val c0 = minOf(anchorC, c); val r0 = minOf(anchorR, r)
        val w = (kotlin.math.abs(c - anchorC) + 1).coerceAtMost(6)
        val h = (kotlin.math.abs(r - anchorR) + 1).coerceAtMost(6)
        setSelection(r0 * tileset.cols + c0, w, h)
    }

    private fun cell(): Int = if (width > 0) width / tileset.cols else (40 * dp).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val c = w / tileset.cols
        setMeasuredDimension(w, c * tileset.rows)
    }

    override fun onDraw(c: Canvas) {
        val s = cell()
        for (id in 0 until tileset.count) {
            val x = (id % tileset.cols) * s
            val y = (id / tileset.cols) * s
            dst.set(x, y, x + s, y + s)
            // Fondo a cuadros para ver los tiles con transparencia
            val h = s / 2
            c.drawRect(x.toFloat(), y.toFloat(), (x + h).toFloat(), (y + h).toFloat(), checkA)
            c.drawRect((x + h).toFloat(), y.toFloat(), (x + s).toFloat(), (y + h).toFloat(), checkB)
            c.drawRect(x.toFloat(), (y + h).toFloat(), (x + h).toFloat(), (y + s).toFloat(), checkB)
            c.drawRect((x + h).toFloat(), (y + h).toFloat(), (x + s).toFloat(), (y + s).toFloat(), checkA)
            c.drawBitmap(tileset.bitmap, tileset.src(id, src), dst, tilePaint)
        }
        val sx = (selected % tileset.cols) * s
        val sy = (selected / tileset.cols) * s
        c.drawRect(sx + 2f, sy + 2f, sx + s * selW - 2f, sy + s * selH - 2f, selPaint)
        if (dimmed) c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                selecting = false
                postDelayed(longPress, 300)
            }
            MotionEvent.ACTION_MOVE -> {
                if (selecting) {
                    val (c, r) = cellAt(e.x, e.y)
                    dragTo(c, r)
                } else if (abs(e.x - downX) > slop || abs(e.y - downY) > slop) {
                    removeCallbacks(longPress) // es scroll, no selección
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (selecting) {
                    selecting = false
                    onSelect?.invoke(selected, selW, selH)
                } else if (abs(e.x - downX) < slop && abs(e.y - downY) < slop && !dimmed) {
                    val (c, r) = cellAt(e.x, e.y)
                    setSelection(r * tileset.cols + c, 1, 1)
                    onSelect?.invoke(selected, 1, 1)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                selecting = false
            }
        }
        return true
    }
}
