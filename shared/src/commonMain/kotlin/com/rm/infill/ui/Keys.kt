package com.rm.infill.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Everything a key can do. [held] ones keep going while the key is down
 * (panning, zooming), the rest happen once per press.
 */
enum class KeyAction(val held: Boolean = false) {
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
    Pause,
    Back,

    // For trying the looks while they're being made.
    DevSeasonBack,
    DevSeasonNext,
    DevHourBack,
    DevHourNext,
    DevGraphics,
}

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
    Key.Spacebar to KeyAction.Pause,
    Key.Escape to KeyAction.Back,
    Key.LeftBracket to KeyAction.DevSeasonBack,
    Key.RightBracket to KeyAction.DevSeasonNext,
    Key.Comma to KeyAction.DevHourBack,
    Key.Period to KeyAction.DevHourNext,
    Key.G to KeyAction.DevGraphics,
)

/**
 * Turns key events into actions. A press fires once however long the key is
 * held (the platform's key repeat is ignored), and held actions are listed in
 * [held] until their key comes up.
 */
class KeyInput(private val bindings: Map<Key, KeyAction> = DefaultKeys) {
    private val down = mutableSetOf<KeyAction>()
    val held = mutableSetOf<KeyAction>()

    /** Goes up by one whenever [held] changes, so the UI can follow it. */
    var heldVersion by mutableIntStateOf(0)
        private set

    fun onKey(event: KeyEvent, onPress: (KeyAction) -> Unit): Boolean {
        val action = bindings[event.key] ?: return false
        when (event.type) {
            KeyEventType.KeyDown -> if (down.add(action)) {
                if (action.held && held.add(action)) heldVersion++
                onPress(action)
            }
            KeyEventType.KeyUp -> {
                down.remove(action)
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
