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
    }

    private fun loadKeys(): Map<Key, KeyAction> {
        val saved = store.setting(KEYS) ?: return DefaultKeys
        val out = LinkedHashMap<Key, KeyAction>()
        for (pair in saved.split(';')) {
            val (code, name) = pair.split('=').takeIf { it.size == 2 } ?: continue
            val action = KeyAction.entries.firstOrNull { it.name == name } ?: continue
            out[Key(code.toLongOrNull() ?: continue)] = action
        }
        // Dev keys aren't saved; they always come from the defaults.
        for ((k, a) in DefaultKeys) if (a.dev && k !in out) out[k] = a
        return out
    }

    private fun saveKeys() {
        store.setSetting(KEYS, keysState.filterValues { !it.dev }.entries.joinToString(";") { "${it.key.keyCode}=${it.value.name}" })
    }

    companion object {
        val SCALES = listOf(1f, 1.1f, 1.2f, 1.3f)
        private const val GRAPHICS = "graphics"
        private const val THEME = "theme"
        private const val SCALE = "ui_scale"
        private const val KEYS = "keys"
    }
}
