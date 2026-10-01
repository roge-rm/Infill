package com.rm.infill.platform

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import com.rm.infill.map.GraphicsLevel
import com.rm.infill.ui.DefaultKeys
import com.rm.infill.ui.KeyAction

enum class ThemeChoice { Auto, Light, Dark }

/** The player's settings for this device, kept as they change. */
@Stable
class Settings(private val store: Platform) {
    private var graphicsState by mutableStateOf(
        store.setting(GRAPHICS)?.let { v -> GraphicsLevel.entries.firstOrNull { it.name == v } } ?: store.suggestedGraphics,
    )
    var graphics: GraphicsLevel
        get() = graphicsState
        set(v) {
            graphicsState = v
            store.setSetting(GRAPHICS, v.name)
        }

    private var themeState by mutableStateOf(
        store.setting(THEME)?.let { v -> ThemeChoice.entries.firstOrNull { it.name == v } } ?: ThemeChoice.Auto,
    )
    var theme: ThemeChoice
        get() = themeState
        set(v) {
            themeState = v
            store.setSetting(THEME, v.name)
        }

    private var scaleState by mutableFloatStateOf(store.setting(SCALE)?.toFloatOrNull()?.takeIf { it in SCALES } ?: 1f)

    /** How much bigger than normal the controls and text are. */
    var uiScale: Float
        get() = scaleState
        set(v) {
            scaleState = v
            store.setSetting(SCALE, v.toString())
        }

    private var keysState by mutableStateOf(loadKeys())

    /** Which key does what. Kept as key codes, which differ between platforms, so they're kept per device. */
    val keys: Map<Key, KeyAction> get() = keysState

    /** Makes [key] the key for [action], in place of the ones it had, and takes it from whatever had it. */
    fun bind(action: KeyAction, key: Key) {
        keysState = keysState.filter { (k, a) -> k != key && a != action } + (key to action)
        saveKeys()
    }

    /** Takes [key] off whatever it does. */
    fun unbind(key: Key) {
        keysState = keysState - key
        saveKeys()
    }

    fun resetKeys() {
        keysState = DefaultKeys
        store.setSetting(KEYS, null)
        store.setSetting(KNOWN, null)
    }

    private fun loadKeys(): Map<Key, KeyAction> {
        val saved = store.setting(KEYS) ?: return DefaultKeys
        val out = LinkedHashMap<Key, KeyAction>()
        for (pair in saved.split(';')) {
            val (code, name) = pair.split('=').takeIf { it.size == 2 } ?: continue
            val action = KeyAction.entries.firstOrNull { it.name == name } ?: continue
            out[Key(code.toLongOrNull() ?: continue)] = action
        }
        // Dev keys aren't saved; they always come from the defaults. Nor are
        // actions added since the keys were saved: they get their default key
        // if nothing else has taken it.
        // Keys saved before this was kept came from 0.1, which had only the actions it had then.
        val known = store.setting(KNOWN)?.split(',')?.toSet() ?: FIRST_ACTIONS
        for ((k, a) in DefaultKeys) {
            val added = a.name !in known && a !in out.values
            if ((a.dev || added) && k !in out) out[k] = a
        }
        return out
    }

    private fun saveKeys() {
        store.setSetting(KEYS, keysState.filterValues { !it.dev }.entries.joinToString(";") { "${it.key.keyCode}=${it.value.name}" })
        store.setSetting(KNOWN, KeyAction.entries.joinToString(",") { it.name })
    }

    companion object {
        /** The actions there were in 0.1, when keys were first saved and before the list of known ones was kept. */
        private val FIRST_ACTIONS = setOf(
            "PanUp", "PanDown", "PanLeft", "PanRight", "ZoomIn", "ZoomOut",
            "ToolInspect", "ToolBulldoze", "ToolRoad", "ToolZone", "ToolPower", "ToolServices",
            "Budget", "Graphs", "NextOverlay", "Speed1", "Speed2", "Speed3", "Pause", "Back", "Undo", "Redo",
        )

        val SCALES = listOf(1f, 1.1f, 1.2f, 1.3f)
        private const val GRAPHICS = "graphics"
        private const val THEME = "theme"
        private const val SCALE = "ui_scale"
        private const val KEYS = "keys"

        /** The actions there were when the keys were saved, so ones added since can have their defaults. */
        private const val KNOWN = "keys_known"
    }
}
