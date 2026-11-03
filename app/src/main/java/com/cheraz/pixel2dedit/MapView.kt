package com.cheraz.pixel2dedit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Tool(val label: String) {
    PEN("Lápiz"),
    ERASE("Borrar"),
    FILL("Relleno"),
    PICK("Gotero"),
    SPAWN("Spawn")
}

/**
 * Lienzo del mapa. En modo edición: 1 dedo pinta, 2 dedos mueven y hacen zoom.
 * En modo jugar: un cuadrado se mueve con el joystick y choca con la capa de colisión.
 */
class MapView(context: Context, var tileset: Tileset, initial: TileMap) : View(context) {

    var map: TileMap = initial
        set(v) { field = v; history.clear(); fitView(); notifyChanged() }

    val history = History()
    var layer = Layer.GROUND
    var tool = Tool.PEN
    /** Esquina superior izquierda del sello elegido en la paleta, y su tamaño en tiles. */
    var selectedTile = 0
    var stampW = 1
    var stampH = 1
    /** En Deco, las filas de arriba del sello (copa, techo) van a Encima. */
    var autoAbove = true
    var showGrid = true
    /** Modo Mover: un dedo arrastra el mapa, nunca pinta. */
    var panMode = true

    /** Avisa a la UI (botones de undo/redo, paleta). */
    var onChanged: (() -> Unit)? = null
    var onPicked: ((Int) -> Unit)? = null

    // Cámara: punto del mundo (px de tile) en el centro de la pantalla.
    private var camX = 0f
    private var camY = 0f
    private var zoom = 0f // px de pantalla por px del mundo

    private val dp = resources.displayMetrics.density
    private val ts get() = tileset.tileSize

    // ---- Dibujo ----
    private val tilePaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val gridPaint = Paint().apply { color = Color.argb(45, 255, 255, 255); strokeWidth = 1f }
    private val borderPaint = Paint().apply { color = Color.argb(200, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2f * resources.displayMetrics.density }
    private val solidPaint = Paint().apply { color = Color.argb(90, 230, 40, 60) }
    private val solidStrong = Paint().apply { color = Color.argb(150, 230, 40, 60) }
    private val spawnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 200, 255) }
    private val spawnText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val playerFill = Paint().apply { color = Color.rgb(255, 196, 40) }
    private val playerLine = Paint().apply { color = Color.rgb(60, 30, 20); style = Paint.Style.STROKE }
    private val src = Rect()
    private val dst = Rect()

    // ---- Jugar ----
    var playing = false
        private set
    var inputX = 0f
    var inputY = 0f
    private var px = 0f
    private var py = 0f
    private val half = 5f        // medio lado del cuadrado, px del mundo (10×10 dentro de un tile de 16)
    private val speed = 72f      // px del mundo por segundo (~4.5 tiles/s)
    private var lastFrameNs = 0L
    private var savedCam = FloatArray(3)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (zoom == 0f) fitView()
    }

    /** Centra el mapa con ~12 tiles a lo ancho. */
    fun fitView() {
        if (width == 0) return
        zoom = width / (ts * 12f)
        camX = map.w * ts / 2f
        camY = map.h * ts / 2f
        invalidate()
    }

    private fun offX() = width / 2f - camX * zoom
    private fun offY() = height / 2f - camY * zoom
    private fun edgeX(i: Int) = (offX() + i * ts * zoom).roundToInt()
    private fun edgeY(j: Int) = (offY() + j * ts * zoom).roundToInt()

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(24, 22, 30))
        if (zoom == 0f) return
        val cell = ts * zoom
        val x0 = max(0, floor(-offX() / cell).toInt())
        val y0 = max(0, floor(-offY() / cell).toInt())
        val x1 = min(map.w - 1, floor((width - offX()) / cell).toInt())
        val y1 = min(map.h - 1, floor((height - offY()) / cell).toInt())

        drawTiles(c, map.ground, x0, y0, x1, y1)
        drawTiles(c, map.deco, x0, y0, x1, y1)
        if (playing) drawPlayer(c)
        drawTiles(c, map.above, x0, y0, x1, y1)

        if (!playing) {
            val p = if (layer == Layer.COLLISION) solidStrong else solidPaint
            for (y in y0..y1) for (x in x0..x1) {
                if (map.solid[map.idx(x, y)]) {
                    dst.set(edgeX(x), edgeY(y), edgeX(x + 1), edgeY(y + 1))
                    c.drawRect(dst, p)
                }
            }
            if (showGrid && cell >= 10f) {
                for (x in x0..x1 + 1) {
                    val sx = edgeX(x).toFloat()
                    c.drawLine(sx, edgeY(y0).toFloat(), sx, edgeY(y1 + 1).toFloat(), gridPaint)
                }
                for (y in y0..y1 + 1) {
                    val sy = edgeY(y).toFloat()
                    c.drawLine(edgeX(x0).toFloat(), sy, edgeX(x1 + 1).toFloat(), sy, gridPaint)
                }
            }
            c.drawRect(edgeX(0).toFloat(), edgeY(0).toFloat(), edgeX(map.w).toFloat(), edgeY(map.h).toFloat(), borderPaint)
            // Marcador del spawn
            val sx = (edgeX(map.spawnX) + edgeX(map.spawnX + 1)) / 2f
            val sy = (edgeY(map.spawnY) + edgeY(map.spawnY + 1)) / 2f
            val r = max(cell * 0.3f, 6f * dp)
            c.drawCircle(sx, sy, r, spawnPaint)
            spawnText.textSize = r * 1.2f
            c.drawText("S", sx, sy + r * 0.42f, spawnText)
        }
    }

    private fun drawTiles(c: Canvas, layerTiles: IntArray, x0: Int, y0: Int, x1: Int, y1: Int) {
        val bmp = tileset.bitmap
        for (y in y0..y1) {
            val top = edgeY(y)
            val bottom = edgeY(y + 1)
            for (x in x0..x1) {
                val id = layerTiles[map.idx(x, y)]
                if (id < 0 || id >= tileset.count) continue
                dst.set(edgeX(x), top, edgeX(x + 1), bottom)
                c.drawBitmap(bmp, tileset.src(id, src), dst, tilePaint)
            }
        }
    }

    private fun drawPlayer(c: Canvas) {
        val l = offX() + (px - half) * zoom
        val t = offY() + (py - half) * zoom
        val r = offX() + (px + half) * zoom
        val b = offY() + (py + half) * zoom
        c.drawRect(l, t, r, b, playerFill)
        playerLine.strokeWidth = max(1f, zoom)
        c.drawRect(l, t, r, b, playerLine)
    }

    // ---- Edición: toques ----
    private enum class Gesture { NONE, PAINT, PAN }
    private var gesture = Gesture.NONE
    private val stroke = mutableListOf<CellChange>()
    private var lastCx = -1
    private var lastCy = -1
    // Origen del trazo y sellos ya puestos en él (los sellos quedan alineados a su tamaño).
    private var strokeX0 = 0
    private var strokeY0 = 0
    private val stamped = HashSet<Long>()
    // Línea base del pan/zoom
    private var panSpan = 0f
    private var panZoom = 0f
    private var panWorldX = 0f
    private var panWorldY = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (playing) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (panMode) {
                gesture = Gesture.PAN
                beginPan(e, -1)
            } else {
                gesture = Gesture.PAINT
                stroke.clear()
                stamped.clear()
                val (cx, cy) = cellAt(e.x, e.y)
                lastCx = cx; lastCy = cy
                strokeX0 = cx; strokeY0 = cy
                when (tool) {
                    Tool.PEN, Tool.ERASE -> paintCell(cx, cy)
                    Tool.FILL -> { fill(cx, cy); commitStroke() }
                    Tool.PICK -> pick(cx, cy)
                    Tool.SPAWN -> if (map.inside(cx, cy)) { map.spawnX = cx; map.spawnY = cy }
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Llegó un segundo dedo: lo que pintó el primero era accidental.
                if (gesture == Gesture.PAINT) revertStroke()
                gesture = Gesture.PAN
                beginPan(e, -1)
            }
            MotionEvent.ACTION_MOVE -> when (gesture) {
                Gesture.PAINT -> if (tool == Tool.PEN || tool == Tool.ERASE) {
                    val (cx, cy) = cellAt(e.x, e.y)
                    if (cx != lastCx || cy != lastCy) {
                        line(lastCx, lastCy, cx, cy)
                        lastCx = cx; lastCy = cy
                        invalidate()
                    }
                }
                Gesture.PAN -> { updatePan(e); invalidate() }
                Gesture.NONE -> {}
            }
            MotionEvent.ACTION_POINTER_UP -> if (gesture == Gesture.PAN) beginPan(e, e.actionIndex)
            MotionEvent.ACTION_UP -> {
                if (gesture == Gesture.PAINT) commitStroke()
                gesture = Gesture.NONE
            }
            MotionEvent.ACTION_CANCEL -> {
                if (gesture == Gesture.PAINT) revertStroke()
                gesture = Gesture.NONE
            }
        }
        return true
    }

    private fun focus(e: MotionEvent, skip: Int): FloatArray {
        var sx = 0f; var sy = 0f; var n = 0
        for (i in 0 until e.pointerCount) { if (i == skip) continue; sx += e.getX(i); sy += e.getY(i); n++ }
        if (n == 0) return floatArrayOf(width / 2f, height / 2f, 0f)
        val fx = sx / n; val fy = sy / n
        var span = 0f
        for (i in 0 until e.pointerCount) { if (i == skip) continue; span += hypot(e.getX(i) - fx, e.getY(i) - fy) }
        return floatArrayOf(fx, fy, span / n)
    }

    private fun beginPan(e: MotionEvent, skip: Int) {
        val f = focus(e, skip)
        panSpan = f[2]
        panZoom = zoom
        panWorldX = camX + (f[0] - width / 2f) / zoom
        panWorldY = camY + (f[1] - height / 2f) / zoom
    }

    private fun updatePan(e: MotionEvent) {
        val f = focus(e, -1)
        if (panSpan > 10f && f[2] > 10f) {
            // De ~8dp a ~160dp por tile en pantalla.
            zoom = (panZoom * f[2] / panSpan).coerceIn(0.5f * dp, 10f * dp)
        }
        // Mantiene bajo los dedos el mismo punto del mundo.
        camX = panWorldX - (f[0] - width / 2f) / zoom
        camY = panWorldY - (f[1] - height / 2f) / zoom
    }

    private fun cellAt(sx: Float, sy: Float): Pair<Int, Int> {
        val wx = (sx - offX()) / zoom
        val wy = (sy - offY()) / zoom
        return Pair(floor(wx / ts).toInt(), floor(wy / ts).toInt())
    }

    private fun eraseValue() = if (layer == Layer.COLLISION) 0 else -1

    private fun stampTile(dx: Int, dy: Int) = selectedTile + dy * tileset.cols + dx

    private fun setCell(l: Layer, i: Int, v: Int) {
        val old = map.get(l, i)
        if (old == v) return
        map.set(l, i, v)
        stroke.add(CellChange(l, i, old, v))
    }

    private fun paintCell(cx: Int, cy: Int) {
        if (tool == Tool.ERASE) { eraseCell(cx, cy); return }
        if (layer == Layer.COLLISION) {
            if (map.inside(cx, cy)) setCell(layer, map.idx(cx, cy), 1)
            return
        }
        // Al arrastrar, los sellos caen en una rejilla de su tamaño: no se encima uno con otro.
        val ox = strokeX0 + Math.floorDiv(cx - strokeX0, stampW) * stampW
        val oy = strokeY0 + Math.floorDiv(cy - strokeY0, stampH) * stampH
        if (!stamped.add((ox.toLong() shl 32) or (oy.toLong() and 0xffffffffL))) return
        stamp(ox, oy)
    }

    private fun stamp(ox: Int, oy: Int) {
        for (dy in 0 until stampH) for (dx in 0 until stampW) {
            val x = ox + dx; val y = oy + dy
            if (!map.inside(x, y)) continue
            val id = stampTile(dx, dy)
            if (tileset.isEmpty(id)) continue
            val target = if (autoAbove && layer == Layer.DECO && dy < stampH - 1) Layer.ABOVE else layer
            setCell(target, map.idx(x, y), id)
        }
    }

    private fun eraseCell(cx: Int, cy: Int) {
        if (!map.inside(cx, cy)) return
        val i = map.idx(cx, cy)
        setCell(layer, i, eraseValue())
        // Si la copa se fue sola a Encima, borrar en Deco también la quita.
        if (autoAbove && layer == Layer.DECO) setCell(Layer.ABOVE, i, -1)
    }

    /** Bresenham para que un trazo rápido no deje huecos. */
    private fun line(x0: Int, y0: Int, x1: Int, y1: Int) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            paintCell(x, y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    private fun fill(cx: Int, cy: Int) {
        if (!map.inside(cx, cy)) return
        val target = map.get(layer, map.idx(cx, cy))
        val visited = BooleanArray(map.w * map.h)
        val stack = ArrayDeque<Int>()
        stack.addLast(map.idx(cx, cy))
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (visited[i] || map.get(layer, i) != target) continue
            visited[i] = true
            val x = i % map.w; val y = i / map.w
            // Con un sello de varios tiles, el relleno repite el patrón (ej. bosque 2×2).
            val v = if (layer == Layer.COLLISION) 1
                else stampTile(Math.floorMod(x - cx, stampW), Math.floorMod(y - cy, stampH))
            setCell(layer, i, v)
            if (x > 0) stack.addLast(i - 1)
            if (x < map.w - 1) stack.addLast(i + 1)
            if (y > 0) stack.addLast(i - map.w)
            if (y < map.h - 1) stack.addLast(i + map.w)
        }
    }

    /** Toma el tile visible más arriba en esa celda. */
    private fun pick(cx: Int, cy: Int) {
        if (!map.inside(cx, cy)) return
        val i = map.idx(cx, cy)
        val id = listOf(map.above[i], map.deco[i], map.ground[i]).firstOrNull { it >= 0 } ?: return
        selectedTile = id
        onPicked?.invoke(id)
    }

    private fun commitStroke() {
        history.push(stroke.toList())
        stroke.clear()
        notifyChanged()
    }

    private fun revertStroke() {
        for (c in stroke.asReversed()) map.set(c.layer, c.i, c.old)
        stroke.clear()
        invalidate()
    }

    fun undo() { if (history.undo(map)) { invalidate(); notifyChanged() } }
    fun redo() { if (history.redo(map)) { invalidate(); notifyChanged() } }

    private fun notifyChanged() = onChanged?.invoke()

    // ---- Jugar ----
    fun startPlay() {
        if (playing) return
        savedCam = floatArrayOf(camX, camY, zoom)
        playing = true
        px = (map.spawnX + 0.5f) * ts
        py = (map.spawnY + 0.5f) * ts
        zoom = min(width, height) / (ts * 11f)
        camX = px; camY = py
        inputX = 0f; inputY = 0f
        lastFrameNs = 0L
        postOnAnimation(tick)
        invalidate()
    }

    fun stopPlay() {
        if (!playing) return
        playing = false
        removeCallbacks(tick)
        camX = savedCam[0]; camY = savedCam[1]; zoom = savedCam[2]
        invalidate()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!playing) return
            val now = System.nanoTime()
            val dt = if (lastFrameNs == 0L) 0f else ((now - lastFrameNs) / 1e9f).coerceAtMost(0.05f)
            lastFrameNs = now
            step(dt)
            camX = px; camY = py
            invalidate()
            postOnAnimation(this)
        }
    }

    /** Movimiento por eje: si choca en X todavía puede deslizarse en Y, y viceversa. */
    private fun step(dt: Float) {
        val dx = inputX * speed * dt
        val dy = inputY * speed * dt
        if (dx != 0f && !hits(px + dx, py)) px += dx
        if (dy != 0f && !hits(px, py + dy)) py += dy
    }

    private fun hits(cx: Float, cy: Float): Boolean {
        val eps = 0.01f
        val tx0 = floor((cx - half) / ts).toInt()
        val tx1 = floor((cx + half - eps) / ts).toInt()
        val ty0 = floor((cy - half) / ts).toInt()
        val ty1 = floor((cy + half - eps) / ts).toInt()
        for (ty in ty0..ty1) for (tx in tx0..tx1) if (map.isSolid(tx, ty)) return true
        return false
    }
}
