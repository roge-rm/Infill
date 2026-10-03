package com.rm.infill.platform

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import com.rm.infill.map.GraphicsLevel
import com.rm.infill.ui.DefaultKeys
import com.rm.infill.ui.DefaultPad
import com.rm.infill.ui.KeyAction
import com.rm.infill.ui.PadButton

enum class ThemeChoice { Auto, Light, Dark }

/** Which side the tools go down when they run down the side: away from the camera, or always left or right. */
enum class ToolSide { Auto, Left, Right }

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

    private var toolSideState by mutableStateOf(
        store.setting(TOOL_SIDE)?.let { v -> ToolSide.entries.firstOrNull { it.name == v } } ?: ToolSide.Auto,
    )
    var toolSide: ToolSide
        get() = toolSideState
        set(v) {
            toolSideState = v
            store.setSetting(TOOL_SIDE, v.name)
        }

    private var languageState by mutableStateOf(store.setting(LANGUAGE)?.takeIf { it in LANGUAGES } ?: "")

    /** The language the game is in, as a code like "en", or empty for the phone's own. */
    var language: String
        get() = languageState
        set(v) {
            languageState = v
            store.setSetting(LANGUAGE, v.ifEmpty { null })
        }

    private var disastersState by mutableStateOf(store.setting(DISASTERS)?.toIntOrNull()?.coerceIn(0, 2) ?: 2)

    /** How often disasters come: 0 never, 1 fewer, 2 normal. */
    var disasters: Int
        get() = disastersState
        set(v) {
            disastersState = v
            store.setSetting(DISASTERS, v.toString())
        }

    private var volumesState by mutableStateOf(
        IntArray(VOLUMES.size) { k -> store.setting(VOLUMES[k])?.toIntOrNull()?.coerceIn(0, 100) ?: DEFAULT_VOLUMES[k] }.toList(),
    )

    /** How loud everything is, the town, the tools and alerts, and the music, each 0 to 100. */
    var master: Int
        get() = volumesState[0]
        set(v) = setVolume(0, v)
    var townVolume: Int
        get() = volumesState[1]
        set(v) = setVolume(1, v)
    var effectsVolume: Int
        get() = volumesState[2]
        set(v) = setVolume(2, v)
    var musicVolume: Int
        get() = volumesState[3]
        set(v) = setVolume(3, v)

    private fun setVolume(k: Int, v: Int) {
        volumesState = volumesState.toMutableList().also { it[k] = v.coerceIn(0, 100) }
        store.setSetting(VOLUMES[k], v.toString())
    }

    /** What each of the synth's buses gets, 0 to 1: the town, effects, buttons (with the effects), and music. */
    val busGains: FloatArray
        get() {
            val m = master / 100f
            return floatArrayOf(m * townVolume / 100f, m * effectsVolume / 100f, m * effectsVolume / 100f, m * musicVolume / 100f)
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
        padState = DefaultPad
        store.setSetting(PAD, null)
    }

    private var padState by mutableStateOf(loadPad())

    /** Which controller button does what. */
    val pad: Map<PadButton, KeyAction> get() = padState

    /** Makes [button] the button for [action], in place of the one it had, and takes it from whatever had it. */
    fun bindPad(action: KeyAction, button: PadButton) {
        if (!button.bindable) return
        padState = padState.filter { (b, a) -> b != button && a != action } + (button to action)
        store.setSetting(PAD, padState.entries.joinToString(";") { "${it.key.name}=${it.value.name}" })
    }

    private fun loadPad(): Map<PadButton, KeyAction> {
        val saved = store.setting(PAD) ?: return DefaultPad
        val out = LinkedHashMap<PadButton, KeyAction>()
        for (pair in saved.split(';')) {
            val (button, name) = pair.split('=').takeIf { it.size == 2 } ?: continue
            val b = PadButton.entries.firstOrNull { it.name == button && it.bindable } ?: continue
            out[b] = KeyAction.entries.firstOrNull { it.name == name } ?: continue
        }
        return out
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
        // The arrows panned the map until the cursor came; where they still do, they move the cursor now.
        if (KeyAction.CursorUp.name !in known) {
            for ((arrow, pan, cursor) in ARROWS) if (out[arrow] == pan) out[arrow] = cursor
        }
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

        private val ARROWS = listOf(
            Triple(Key.DirectionUp, KeyAction.PanUp, KeyAction.CursorUp),
            Triple(Key.DirectionDown, KeyAction.PanDown, KeyAction.CursorDown),
            Triple(Key.DirectionLeft, KeyAction.PanLeft, KeyAction.CursorLeft),
            Triple(Key.DirectionRight, KeyAction.PanRight, KeyAction.CursorRight),
        )

        val SCALES = listOf(1f, 1.1f, 1.2f, 1.3f)
        private const val GRAPHICS = "graphics"
        private const val THEME = "theme"
        private const val LANGUAGE = "language"

        /**
         * The languages there are strings for, each with its name in itself.
         * A new translation adds a values-xx folder of strings and a line here.
         * The choice shows in Settings once there's more than one, and the
         * manual's Settings section gets a line for it then.
         */
        val LANGUAGES = linkedMapOf("en" to "English")
        private const val SCALE = "ui_scale"
        private const val DISASTERS = "disasters"
        private const val TOOL_SIDE = "tool_side"
        private const val KEYS = "keys"
        private const val PAD = "pad"
        private val VOLUMES = listOf("volume_master", "volume_town", "volume_effects", "volume_music")
        private val DEFAULT_VOLUMES = listOf(80, 80, 70, 60)

        /** The actions there were when the keys were saved, so ones added since can have their defaults. */
        private const val KNOWN = "keys_known"
    }
}
