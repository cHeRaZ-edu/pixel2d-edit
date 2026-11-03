package com.cheraz.pixel2dedit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** Muestra el tileset completo en cuadrícula; un toque elige el tile. Va dentro de un ScrollView. */
class PaletteView(context: Context, var tileset: Tileset) : View(context) {

    var selected = 0
        set(v) { field = v; invalidate() }
    var dimmed = false
        set(v) { field = v; invalidate() }
    var onSelect: ((Int) -> Unit)? = null

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
        c.drawRect(sx + 2f, sy + 2f, sx + s - 2f, sy + s - 2f, selPaint)
        if (dimmed) c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
            MotionEvent.ACTION_UP -> {
                if (abs(e.x - downX) < slop && abs(e.y - downY) < slop && !dimmed) {
                    val s = cell()
                    val col = (e.x / s).toInt()
                    val row = (e.y / s).toInt()
                    val id = row * tileset.cols + col
                    if (col in 0 until tileset.cols && id in 0 until tileset.count) {
                        selected = id
                        onSelect?.invoke(id)
                    }
                }
            }
        }
        return true
    }
}
