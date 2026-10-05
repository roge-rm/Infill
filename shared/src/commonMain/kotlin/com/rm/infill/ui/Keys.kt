package com.rm.infill.ui

import com.rm.infill.res.Res
import com.rm.infill.res.key_backspace
import com.rm.infill.res.key_delete
import com.rm.infill.res.key_enter
import com.rm.infill.res.key_esc
import com.rm.infill.res.key_num_enter
import com.rm.infill.res.key_num_minus
import com.rm.infill.res.key_num_plus
import com.rm.infill.res.key_space
import com.rm.infill.res.key_tab
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Everything a key can do. [held] ones keep going while the key is down
 * (panning, zooming), the rest happen once per press.
 */
enum class KeyAction(val held: Boolean = false, val dev: Boolean = false) {
    // The cursor on the map, for playing with keys alone: it moves a tile a press, and on while held.
    CursorUp(held = true),
    CursorDown(held = true),
    CursorLeft(held = true),
    CursorRight(held = true),
    /** Uses the tool at the cursor: starts and finishes a drag, places a building, or inspects. */
    Use,
    PanUp(held = true),
    PanDown(held = true),
    PanLeft(held = true),
    PanRight(held = true),
    ZoomIn(held = true),
    ZoomOut(held = true),
    ToolInspect,
    ToolBulldoze,
    ToolRoad,
    ToolRail,
    ToolZone,
    ToolPower,
    ToolWater,
    ToolServices,
    ToolLeisure,
    Ordinances,
    Chronicle,
    Opinion,
    ToolTransit,
    ToolTraffic,
    ToolDistricts,
    ToolPhone,
    ToolPorts,
    ToolAir,
    /** The tray's choices and tabs, a step at a time. */
    PrevChoice,
    NextChoice,
    PrevTab,
    NextTab,
    /** Takes the focus from the map to the tool buttons, for a controller. */
    Tools,
    Menu,
    Budget,
    Graphs,
    People,
    Demand,
    NextOverlay,
    Speed1,
    Speed2,
    Speed3,
    Speed4,
    Pause,
    Back,
    Undo,
    Redo,

    // For trying the looks while they're being made.
    DevSeasonBack(dev = true),
    DevSeasonNext(dev = true),
    DevHourBack(dev = true),
    DevHourNext(dev = true),
    DevGraphics(dev = true),
    DevWeather(dev = true),
    DevFire(dev = true),
}

/** A key and the modifiers held with it. Cmd counts as Ctrl, for Macs in the browser. */
data class KeyChord(val key: Key, val ctrl: Boolean = false, val shift: Boolean = false)

private fun Key.ctrl(shift: Boolean = false) = KeyChord(this, ctrl = true, shift = shift)

/** Chords with modifiers, out of the box. These win over the same key on its own. */
val DefaultChords: Map<KeyChord, KeyAction> = mapOf(
    Key.Z.ctrl() to KeyAction.Undo,
    Key.Y.ctrl() to KeyAction.Redo,
    Key.Z.ctrl(shift = true) to KeyAction.Redo,
    KeyChord(Key.One, shift = true) to KeyAction.Speed1,
    KeyChord(Key.Two, shift = true) to KeyAction.Speed2,
    KeyChord(Key.Three, shift = true) to KeyAction.Speed3,
    KeyChord(Key.Four, shift = true) to KeyAction.Speed4,
)

/** The keys out of the box. Settings will be able to change these. */
val DefaultKeys: Map<Key, KeyAction> = mapOf(
    Key.DirectionUp to KeyAction.CursorUp,
    Key.DirectionDown to KeyAction.CursorDown,
    Key.DirectionLeft to KeyAction.CursorLeft,
    Key.DirectionRight to KeyAction.CursorRight,
    Key.Enter to KeyAction.Use,
    Key.NumPadEnter to KeyAction.Use,
    Key.W to KeyAction.PanUp,
    Key.S to KeyAction.PanDown,
    Key.A to KeyAction.PanLeft,
    Key.D to KeyAction.PanRight,
    Key.Equals to KeyAction.ZoomIn,
    Key.Plus to KeyAction.ZoomIn,
    Key.NumPadAdd to KeyAction.ZoomIn,
    Key.Minus to KeyAction.ZoomOut,
    Key.NumPadSubtract to KeyAction.ZoomOut,
    Key.One to KeyAction.ToolInspect,
    Key.Two to KeyAction.ToolBulldoze,
    Key.Three to KeyAction.ToolRoad,
    Key.Four to KeyAction.ToolRail,
    Key.Five to KeyAction.ToolZone,
    Key.Six to KeyAction.ToolPower,
    Key.Seven to KeyAction.ToolWater,
    Key.Eight to KeyAction.ToolServices,
    Key.L to KeyAction.ToolLeisure,
    Key.U to KeyAction.Ordinances,
    Key.H to KeyAction.Chronicle,
    Key.R to KeyAction.Opinion,
    Key.Nine to KeyAction.ToolTransit,
    Key.Zero to KeyAction.ToolTraffic,
    Key.Backslash to KeyAction.ToolDistricts,
    Key.T to KeyAction.ToolPhone,
    Key.O to KeyAction.ToolPorts,
    Key.I to KeyAction.ToolAir,
    Key.LeftBracket to KeyAction.PrevChoice,
    Key.RightBracket to KeyAction.NextChoice,
    Key.Comma to KeyAction.PrevTab,
    Key.Period to KeyAction.NextTab,
    Key.B to KeyAction.Budget,
    Key.G to KeyAction.Graphs,
    Key.P to KeyAction.People,
    Key.N to KeyAction.Demand,
    Key.V to KeyAction.NextOverlay,
    Key.Spacebar to KeyAction.Pause,
    Key.Escape to KeyAction.Back,
    Key.F5 to KeyAction.DevSeasonBack,
    Key.F6 to KeyAction.DevSeasonNext,
    Key.F7 to KeyAction.DevHourBack,
    Key.F8 to KeyAction.DevHourNext,
    Key.F9 to KeyAction.DevGraphics,
    Key.F10 to KeyAction.DevWeather,
    Key.F12 to KeyAction.DevFire,
)

/**
 * Turns key events into actions. A press fires once however long the key is
 * held (the platform's key repeat is ignored), and held actions are listed in
 * [held] until their key comes up.
 */
class KeyInput(
    var bindings: Map<Key, KeyAction> = DefaultKeys,
    private val chords: Map<KeyChord, KeyAction> = DefaultChords,
) {
    /**
     * Keys and controller buttons that are down and what they did, so a key's
     * release matches its press even if Shift came up first.
     */
    private val down = mutableMapOf<Any, KeyAction>()
    val held = mutableSetOf<KeyAction>()

    /** Goes up by one whenever [held] changes, so the UI can follow it. */
    var heldVersion by mutableIntStateOf(0)
        private set

    fun onKey(event: KeyEvent, onPress: (KeyAction) -> Unit): Boolean {
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.key in down) return true
                val ctrl = event.isCtrlPressed || event.isMetaPressed
                val action = chords[KeyChord(event.key, ctrl, event.isShiftPressed)]
                    ?: (if (ctrl || event.isShiftPressed) null else bindings[event.key])
                    ?: return false
                down[event.key] = action
                if (action.held && held.add(action)) heldVersion++
                onPress(action)
            }
            KeyEventType.KeyUp -> {
                val action = down.remove(event.key) ?: return false
                if (held.remove(action)) heldVersion++
            }
        }
        return true
    }

    /** A controller's button, as [onKey] takes a key. Says whether the button does anything. */
    fun onPad(button: PadButton, pressed: Boolean, pad: Map<PadButton, KeyAction>, onPress: (KeyAction) -> Unit): Boolean {
        if (pressed) {
            if (button in down) return true
            val action = pad[button] ?: return false
            down[button] = action
            if (action.held && held.add(action)) heldVersion++
            onPress(action)
        } else {
            val action = down.remove(button) ?: return false
            if (held.remove(action)) heldVersion++
        }
        return true
    }

    /** When the window loses focus the key-up never comes, so forget everything. */
    fun releaseAll() {
        down.clear()
        if (held.isNotEmpty()) {
            held.clear()
            heldVersion++
        }
    }
}

/** A key's name as printed on it, or in words for the ones with words on them. */
@Composable
fun keyName(key: Key): String = KEY_WORDS[key]?.let { stringResource(it) } ?: KEY_NAMES[key] ?: "#${key.keyCode}"

/** Keys with words on them, which differ by language. */
private val KEY_WORDS: Map<Key, StringResource> = mapOf(
    Key.Spacebar to Res.string.key_space, Key.Escape to Res.string.key_esc, Key.Enter to Res.string.key_enter,
    Key.Tab to Res.string.key_tab, Key.Backspace to Res.string.key_backspace, Key.Delete to Res.string.key_delete,
    Key.NumPadAdd to Res.string.key_num_plus, Key.NumPadSubtract to Res.string.key_num_minus, Key.NumPadEnter to Res.string.key_num_enter,
)

private val KEY_NAMES: Map<Key, String> = buildMap {
    val letters = listOf(
        Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
        Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
    )
    letters.forEachIndexed { i, k -> put(k, ('A' + i).toString()) }
    val digits = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    digits.forEachIndexed { i, k -> put(k, i.toString()) }
    val functions = listOf(Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12)
    functions.forEachIndexed { i, k -> put(k, "F${i + 1}") }
    put(Key.DirectionUp, "\u2191"); put(Key.DirectionDown, "\u2193"); put(Key.DirectionLeft, "\u2190"); put(Key.DirectionRight, "\u2192")
    put(Key.Minus, "-"); put(Key.Equals, "="); put(Key.Plus, "+"); put(Key.Comma, ","); put(Key.Period, ".")
    put(Key.LeftBracket, "["); put(Key.RightBracket, "]"); put(Key.Slash, "/"); put(Key.Semicolon, ";"); put(Key.Backslash, "\\")
}
