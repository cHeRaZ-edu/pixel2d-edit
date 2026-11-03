package com.cheraz.pixel2dedit

import org.json.JSONArray
import org.json.JSONObject

/** Capas que se pintan con tiles. La colisión es aparte (booleana por celda). */
enum class Layer(val label: String) {
    GROUND("Suelo"),
    DECO("Deco"),
    ABOVE("Encima"),
    COLLISION("Colisión")
}

/**
 * Mapa cenital: tres capas de tiles (-1 = vacío) más una capa de colisión.
 * Todo se referencia por ID de tile, así que cambiar el tileset reviste el nivel entero.
 */
class TileMap(val w: Int, val h: Int, val tileset: String = "tinytown") {
    val ground = IntArray(w * h) { -1 }
    val deco = IntArray(w * h) { -1 }
    val above = IntArray(w * h) { -1 }
    val solid = BooleanArray(w * h)
    var spawnX = w / 2
    var spawnY = h / 2

    fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h
    fun idx(x: Int, y: Int) = y * w + x

    fun tiles(layer: Layer): IntArray? = when (layer) {
        Layer.GROUND -> ground
        Layer.DECO -> deco
        Layer.ABOVE -> above
        Layer.COLLISION -> null
    }

    /** Valor de una celda: ID de tile, o 0/1 para colisión. */
    fun get(layer: Layer, i: Int): Int =
        tiles(layer)?.get(i) ?: if (solid[i]) 1 else 0

    fun set(layer: Layer, i: Int, v: Int) {
        val t = tiles(layer)
        if (t != null) t[i] = v else solid[i] = v != 0
    }

    fun isSolid(x: Int, y: Int) = !inside(x, y) || solid[idx(x, y)]

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 1)
        put("w", w)
        put("h", h)
        put("tileset", tileset)
        put("ground", JSONArray(ground.toList()))
        put("deco", JSONArray(deco.toList()))
        put("above", JSONArray(above.toList()))
        put("solid", JSONArray(solid.map { if (it) 1 else 0 }))
        put("spawn", JSONArray(listOf(spawnX, spawnY)))
    }

    companion object {
        /** Mapa nuevo con todo el suelo de pasto. */
        fun blank(w: Int, h: Int, groundTile: Int = 0): TileMap =
            TileMap(w, h).also { it.ground.fill(groundTile) }

        fun fromJson(o: JSONObject): TileMap {
            val m = TileMap(o.getInt("w"), o.getInt("h"), o.optString("tileset", "tinytown"))
            fun read(name: String, dst: IntArray) {
                val a = o.optJSONArray(name) ?: return
                for (i in 0 until minOf(a.length(), dst.size)) dst[i] = a.getInt(i)
            }
            read("ground", m.ground)
            read("deco", m.deco)
            read("above", m.above)
            o.optJSONArray("solid")?.let { a ->
                for (i in 0 until minOf(a.length(), m.solid.size)) m.solid[i] = a.getInt(i) != 0
            }
            o.optJSONArray("spawn")?.let { a ->
                if (a.length() >= 2) { m.spawnX = a.getInt(0); m.spawnY = a.getInt(1) }
            }
            return m
        }
    }
}

/** Un cambio de celda; un trazo es una lista de estos. */
data class CellChange(val layer: Layer, val i: Int, val old: Int, val new: Int)

/** Undo/redo por trazo completo, no por celda. */
class History(private val limit: Int = 60) {
    private val undo = ArrayDeque<List<CellChange>>()
    private val redo = ArrayDeque<List<CellChange>>()

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun push(stroke: List<CellChange>) {
        if (stroke.isEmpty()) return
        undo.addLast(stroke)
        if (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(map: TileMap): Boolean {
        val s = undo.removeLastOrNull() ?: return false
        for (c in s.asReversed()) map.set(c.layer, c.i, c.old)
        redo.addLast(s)
        return true
    }

    fun redo(map: TileMap): Boolean {
        val s = redo.removeLastOrNull() ?: return false
        for (c in s) map.set(c.layer, c.i, c.new)
        undo.addLast(s)
        return true
    }

    fun clear() { undo.clear(); redo.clear() }
}
