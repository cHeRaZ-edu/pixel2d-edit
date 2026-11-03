package com.cheraz.pixel2dedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect

/** Hoja de tiles empaquetada (sin separación). El ID es fila * columnas + columna. */
class Tileset(val bitmap: Bitmap, val tileSize: Int) {
    val cols = bitmap.width / tileSize
    val rows = bitmap.height / tileSize
    val count = cols * rows

    /** Tiles 100% transparentes: un sello no los escribe, así no borra lo que hay debajo. */
    private val empty = BooleanArray(count).also { e ->
        val px = IntArray(tileSize * tileSize)
        for (id in 0 until count) {
            bitmap.getPixels(px, 0, tileSize, (id % cols) * tileSize, (id / cols) * tileSize, tileSize, tileSize)
            e[id] = px.all { (it ushr 24) == 0 }
        }
    }

    fun isEmpty(id: Int) = id !in 0 until count || empty[id]

    fun src(id: Int, out: Rect): Rect {
        val cx = (id % cols) * tileSize
        val cy = (id / cols) * tileSize
        out.set(cx, cy, cx + tileSize, cy + tileSize)
        return out
    }

    companion object {
        fun fromAsset(ctx: Context, name: String, tileSize: Int = 16): Tileset {
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = ctx.assets.open("tilesets/$name.png").use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: error("No se pudo leer tilesets/$name.png")
            return Tileset(bmp, tileSize)
        }
    }
}

/** Un pack dentro de la biblioteca. Sus tiles usan IDs globales offset..offset+count-1. */
class Pack(val key: String, val label: String, val tileset: Tileset, val offset: Int)

/**
 * Todos los packs juntos. ID global = índice del pack * STRIDE + ID local, así un mapa puede
 * mezclar packs y agregar uno nuevo no mueve los IDs de los que ya existen (Pueblo = 0.., como antes).
 */
class TileLibrary(val packs: List<Pack>) {
    val tileSize = packs.first().tileset.tileSize

    fun pack(id: Int): Pack? {
        if (id < 0) return null
        val p = packs.getOrNull(id / STRIDE) ?: return null
        return if (id - p.offset < p.tileset.count) p else null
    }

    fun isEmpty(id: Int): Boolean {
        val p = pack(id) ?: return true
        return p.tileset.isEmpty(id - p.offset)
    }

    /** Tile (dx, dy) de un sello cuya esquina es [base], dentro del mismo pack. */
    fun stampTile(base: Int, dx: Int, dy: Int): Int =
        base + dy * (pack(base)?.tileset?.cols ?: 1) + dx

    companion object {
        const val STRIDE = 1000

        fun load(ctx: Context) = TileLibrary(
            listOf(
                "tinytown" to "Pueblo",
                "tinydungeon" to "Mazmorra",
                "tinyfarm" to "Granja"
            ).mapIndexed { i, (key, label) ->
                Pack(key, label, Tileset.fromAsset(ctx, key), i * STRIDE)
            }
        )
    }
}
