package com.cheraz.pixel2dedit

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {

    private lateinit var tileset: Tileset
    private lateinit var mapView: MapView
    private lateinit var palette: PaletteView
    private lateinit var stick: PlayStick
    private lateinit var editPanel: LinearLayout
    private lateinit var playBtn: TextView
    private lateinit var undoBtn: TextView
    private lateinit var redoBtn: TextView
    private lateinit var modeBtn: TextView
    private val editOnly = mutableListOf<View>()
    private val layerChips = mutableMapOf<Layer, TextView>()
    private val toolChips = mutableMapOf<Tool, TextView>()

    private val dp by lazy { resources.displayMetrics.density }
    private val autosave by lazy { File(filesDir, "autosave.json") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tileset = Tileset.fromAsset(this, "tinytown")
        mapView = MapView(this, tileset, loadAutosave() ?: TileMap.blank(40, 30))
        palette = PaletteView(this, tileset)
        stick = PlayStick(this) { x, y -> mapView.inputX = x; mapView.inputY = y }

        val root = FrameLayout(this)
        root.addView(mapView, FrameLayout.LayoutParams(MATCH, MATCH))
        stick.visibility = View.GONE
        root.addView(stick, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(buildTopBar(), FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        editPanel = buildEditPanel()
        root.addView(editPanel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        setContentView(root)

        palette.onSelect = { id, w, h ->
            mapView.selectedTile = id
            mapView.stampW = w
            mapView.stampH = h
            // Elegir un tile implica querer pintarlo.
            if (mapView.tool != Tool.PEN && mapView.tool != Tool.FILL) selectTool(Tool.PEN)
            setPanMode(false)
        }
        mapView.onPicked = { id ->
            palette.setSelection(id, 1, 1)
            mapView.stampW = 1; mapView.stampH = 1
            selectTool(Tool.PEN)
        }
        mapView.onChanged = { refreshUndo() }
        selectLayer(Layer.GROUND)
        selectTool(Tool.PEN)
        setPanMode(true)
        refreshUndo()
    }

    override fun onPause() {
        super.onPause()
        if (mapView.playing) togglePlay()
        try {
            autosave.writeText(mapView.map.toJson().toString())
        } catch (e: Exception) {
            Log.e("pixel2d", "autosave falló", e)
        }
    }

    private fun loadAutosave(): TileMap? = try {
        if (autosave.exists()) TileMap.fromJson(JSONObject(autosave.readText())) else null
    } catch (e: Exception) {
        Log.e("pixel2d", "autosave ilegible, mapa nuevo", e)
        null
    }

    // ---------- UI ----------

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(6), px(6), px(6), px(6))
        }
        playBtn = chip("▶ Jugar") { togglePlay() }
        bar.addView(playBtn)
        modeBtn = chip("") { setPanMode(!mapView.panMode) }.also { editOnly += it; bar.addView(it) }
        undoBtn = chip("↶") { mapView.undo() }.also { editOnly += it; bar.addView(it) }
        redoBtn = chip("↷") { mapView.redo() }.also { editOnly += it; bar.addView(it) }
        chip("Rejilla") {
            mapView.showGrid = !mapView.showGrid
            mapView.invalidate()
        }.also { editOnly += it; bar.addView(it) }
        chip("Centrar") { mapView.fitView() }.also { editOnly += it; bar.addView(it) }
        chip("Nuevo") { askNewMap() }.also { editOnly += it; bar.addView(it) }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(bar)
        }
    }

    private fun buildEditPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(235, 30, 28, 38))
            setPadding(px(6), px(6), px(6), px(6))
        }
        val layers = LinearLayout(this)
        for (l in Layer.values()) {
            val c = chip(l.label) { selectLayer(l) }
            layerChips[l] = c
            layers.addView(c)
        }
        panel.addView(scrollRow(layers))
        val tools = LinearLayout(this)
        for (t in Tool.values()) {
            val c = chip(t.label) { selectTool(t); setPanMode(false) }
            toolChips[t] = c
            tools.addView(c)
        }
        lateinit var autoChip: TextView
        autoChip = chip("Copa→Encima") {
            mapView.autoAbove = !mapView.autoAbove
            autoChip.background = chipBg(mapView.autoAbove)
        }
        autoChip.background = chipBg(mapView.autoAbove)
        tools.addView(autoChip)
        panel.addView(scrollRow(tools))
        val scroll = ScrollView(this).apply { addView(palette) }
        panel.addView(scroll, LinearLayout.LayoutParams(MATCH, px(170)).apply { topMargin = px(4) })
        return panel
    }

    private fun scrollRow(row: LinearLayout) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(row)
    }

    private fun chip(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
        minWidth = px(44)
        minHeight = px(38)
        setPadding(px(12), px(6), px(12), px(6))
        background = chipBg(false)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = px(6); bottomMargin = px(4) }
    }

    private fun chipBg(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = 10f * dp
        setColor(if (selected) Color.rgb(214, 150, 40) else Color.argb(220, 62, 58, 78))
    }

    private fun selectLayer(l: Layer) {
        mapView.layer = l
        layerChips.forEach { (k, v) -> v.background = chipBg(k == l) }
        palette.dimmed = l == Layer.COLLISION
        // En colisión no hay tile que tomar: el gotero no aplica.
        toolChips[Tool.PICK]?.alpha = if (l == Layer.COLLISION) 0.4f else 1f
        if (l == Layer.COLLISION && mapView.tool == Tool.PICK) selectTool(Tool.PEN)
        mapView.invalidate()
    }

    private fun setPanMode(on: Boolean) {
        mapView.panMode = on
        modeBtn.text = if (on) "✋ Mover" else "✎ Pintar"
        modeBtn.background = chipBg(!on)
    }

    private fun selectTool(t: Tool) {
        if (t == Tool.PICK && mapView.layer == Layer.COLLISION) return
        mapView.tool = t
        toolChips.forEach { (k, v) -> v.background = chipBg(k == t) }
    }

    private fun refreshUndo() {
        undoBtn.alpha = if (mapView.history.canUndo) 1f else 0.4f
        redoBtn.alpha = if (mapView.history.canRedo) 1f else 0.4f
    }

    private fun togglePlay() {
        if (mapView.playing) {
            mapView.stopPlay()
            stick.reset()
            stick.visibility = View.GONE
            editPanel.visibility = View.VISIBLE
            editOnly.forEach { it.visibility = View.VISIBLE }
            playBtn.text = "▶ Jugar"
        } else {
            mapView.startPlay()
            stick.visibility = View.VISIBLE
            editPanel.visibility = View.GONE
            editOnly.forEach { it.visibility = View.GONE }
            playBtn.text = "✎ Editar"
        }
    }

    private fun askNewMap() {
        val sizes = listOf(20 to 15, 40 to 30, 64 to 48)
        val labels = arrayOf("Chico 20×15", "Mediano 40×30", "Grande 64×48")
        AlertDialog.Builder(this)
            .setTitle("Mapa nuevo (reemplaza el actual)")
            .setItems(labels) { _, which ->
                val (w, h) = sizes[which]
                mapView.map = TileMap.blank(w, h)
                mapView.invalidate()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun px(v: Int) = (v * dp).toInt()

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
