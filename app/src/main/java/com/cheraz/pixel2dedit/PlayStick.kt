package com.cheraz.pixel2dedit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** Joystick flotante: aparece donde pones el dedo. Salida -1..1 con Y hacia abajo (como la pantalla). */
class PlayStick(context: Context, private val onMove: (Float, Float) -> Unit) : View(context) {

    private val radius = 60f * resources.displayMetrics.density
    private var id = -1
    private var cx = 0f
    private var cy = 0f
    private var kx = 0f
    private var ky = 0f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = Color.argb(140, 255, 255, 255)
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255) }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        textSize = 14f * resources.displayMetrics.density
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> if (id == -1) {
                val i = e.actionIndex
                id = e.getPointerId(i)
                cx = e.getX(i); cy = e.getY(i); kx = cx; ky = cy
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(id)
                if (i >= 0) {
                    var dx = e.getX(i) - cx
                    var dy = e.getY(i) - cy
                    val d = hypot(dx, dy)
                    if (d > radius) { dx *= radius / d; dy *= radius / d }
                    kx = cx + dx; ky = cy + dy
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                if (e.getPointerId(e.actionIndex) == id) id = -1
            MotionEvent.ACTION_CANCEL -> id = -1
        }
        emit()
        invalidate()
        return true
    }

    private fun emit() {
        if (id == -1) { onMove(0f, 0f); return }
        var x = (kx - cx) / radius
        var y = (ky - cy) / radius
        if (hypot(x, y) < 0.15f) { x = 0f; y = 0f } // zona muerta
        onMove(x, y)
    }

    fun reset() { id = -1; emit(); invalidate() }

    override fun onDraw(c: Canvas) {
        if (id == -1) {
            c.drawText("Toca y arrastra para moverte", width / 2f, height - 40f * resources.displayMetrics.density, hintPaint)
            return
        }
        c.drawCircle(cx, cy, radius, basePaint)
        c.drawCircle(kx, ky, radius * 0.4f, knobPaint)
    }
}
