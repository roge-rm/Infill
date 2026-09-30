package com.rm.infill.ui

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
    PanUp(held = true),
    PanDown(held = true),
    PanLeft(held = true),
    PanRight(held = true),
    ZoomIn(held = true),
    ZoomOut(held = true),
    ToolInspect,
    ToolBulldoze,
    ToolRoad,
    ToolZone,
    ToolPower,
    ToolServices,
    Budget,
    Graphs,
    NextOverlay,
    Speed1,
    Speed2,
    Speed3,
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
)

/** The keys out of the box. Settings will be able to change these. */
val DefaultKeys: Map<Key, KeyAction> = mapOf(
    Key.DirectionUp to KeyAction.PanUp,
    Key.W to KeyAction.PanUp,
    Key.DirectionDown to KeyAction.PanDown,
    Key.S to KeyAction.PanDown,
    Key.DirectionLeft to KeyAction.PanLeft,
    Key.A to KeyAction.PanLeft,
    Key.DirectionRight to KeyAction.PanRight,
    Key.D to KeyAction.PanRight,
    Key.Equals to KeyAction.ZoomIn,
    Key.Plus to KeyAction.ZoomIn,
    Key.NumPadAdd to KeyAction.ZoomIn,
    Key.Minus to KeyAction.ZoomOut,
    Key.NumPadSubtract to KeyAction.ZoomOut,
    Key.One to KeyAction.ToolInspect,
    Key.Two to KeyAction.ToolBulldoze,
    Key.Three to KeyAction.ToolRoad,
    Key.Four to KeyAction.ToolZone,
    Key.Five to KeyAction.ToolPower,
    Key.Six to KeyAction.ToolServices,
    Key.B to KeyAction.Budget,
    Key.G to KeyAction.Graphs,
    Key.V to KeyAction.NextOverlay,
    Key.Spacebar to KeyAction.Pause,
    Key.Escape to KeyAction.Back,
    Key.LeftBracket to KeyAction.DevSeasonBack,
    Key.RightBracket to KeyAction.DevSeasonNext,
    Key.Comma to KeyAction.DevHourBack,
    Key.Period to KeyAction.DevHourNext,
    Key.K to KeyAction.DevGraphics,
    Key.Q to KeyAction.DevWeather,
    Key.X to KeyAction.DevFire,
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
    /** Keys that are down and what they did, so a key's release matches its press even if Shift came up first. */
    private val down = mutableMapOf<Key, KeyAction>()
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

    /** When the window loses focus the key-up never comes, so forget everything. */
    fun releaseAll() {
        down.clear()
        if (held.isNotEmpty()) {
            held.clear()
            heldVersion++
        }
    }
}

/** A key's name as printed on it. */
fun keyName(key: Key): String = KEY_NAMES[key] ?: "#${key.keyCode}"

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
    put(Key.Spacebar, "Space"); put(Key.Escape, "Esc"); put(Key.Enter, "Enter"); put(Key.Tab, "Tab")
    put(Key.Backspace, "Backspace"); put(Key.Delete, "Delete")
    put(Key.Minus, "-"); put(Key.Equals, "="); put(Key.Plus, "+"); put(Key.Comma, ","); put(Key.Period, ".")
    put(Key.LeftBracket, "["); put(Key.RightBracket, "]"); put(Key.Slash, "/"); put(Key.Semicolon, ";")
    put(Key.NumPadAdd, "Num +"); put(Key.NumPadSubtract, "Num -")
}
