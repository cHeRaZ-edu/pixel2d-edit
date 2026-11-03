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
