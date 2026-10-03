package com.rm.infill.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

/**
 * A game controller's buttons, in the standard layout: A at the bottom of the
 * four, B on the right. The left stick's four ways are buttons too, for
 * moving about windows; they can't be given to an action.
 */
enum class PadButton(val bindable: Boolean = true) {
    A, B, X, Y, LB, RB, LT, RT, Select, Start, L3, R3, Up, Down, Left, Right,
    StickUp(false), StickDown(false), StickLeft(false), StickRight(false),
}

/** The buttons out of the box. Settings can change these. */
val DefaultPad: Map<PadButton, KeyAction> = mapOf(
    PadButton.Up to KeyAction.CursorUp,
    PadButton.Down to KeyAction.CursorDown,
    PadButton.Left to KeyAction.CursorLeft,
    PadButton.Right to KeyAction.CursorRight,
    PadButton.A to KeyAction.Use,
    PadButton.B to KeyAction.Back,
    PadButton.X to KeyAction.ToolInspect,
    PadButton.Y to KeyAction.Tools,
    PadButton.LB to KeyAction.PrevChoice,
    PadButton.RB to KeyAction.NextChoice,
    PadButton.L3 to KeyAction.PrevTab,
    PadButton.R3 to KeyAction.NextTab,
    PadButton.LT to KeyAction.ZoomOut,
    PadButton.RT to KeyAction.ZoomIn,
    PadButton.Start to KeyAction.Menu,
    PadButton.Select to KeyAction.NextOverlay,
)

/** A button's name as printed on most controllers. */
fun padName(b: PadButton): String = when (b) {
    PadButton.Up -> "D-pad ↑"
    PadButton.Down -> "D-pad ↓"
    PadButton.Left -> "D-pad ←"
    PadButton.Right -> "D-pad →"
    else -> b.name
}

/**
 * The controllers, as one. The platform reports buttons and sticks here, and
 * whoever is listening takes them, newest first, as Esc goes to the window on
 * top: the settings while they wait for a button, then the map, then moving
 * about the windows and buttons.
 */
object Pad {
    internal val handlers = mutableListOf<(PadButton, Boolean) -> Boolean>()

    /** Called on every button, before anyone takes it, so the game knows a controller is in use. */
    var onUse: () -> Unit = {}

    /** Presses whatever has the focus, as Enter does. Set by the platform. */
    var pressFocused: () -> Unit = {}

    /** Whether a controller has been used since the game started, so the settings show its buttons. */
    var seen by mutableStateOf(false)
        private set

    /** The sticks, each from -1 to 1 a way, down and right positive, with the middle cut out. */
    var left by mutableStateOf(Offset.Zero)
        private set
    var right by mutableStateOf(Offset.Zero)
        private set

    /** Whether either stick is off the middle, so the map knows to keep moving. */
    val moving: Boolean get() = left != Offset.Zero || right != Offset.Zero

    private val down = mutableSetOf<PadButton>()

    /** A button went down or came up. A button already down doesn't go down again. */
    fun button(b: PadButton, pressed: Boolean) {
        if (pressed == (b in down)) return
        if (pressed) down += b else down -= b
        seen = true
        onUse()
        for (h in handlers.asReversed().toList()) if (h(b, pressed)) return
    }

    /** Where the sticks are now, straight from the controller. */
    fun sticks(lx: Float, ly: Float, rx: Float, ry: Float) {
        val l = deadZone(lx, ly)
        val r = deadZone(rx, ry)
        if (l != left || r != right) {
            if (!seen && (l != Offset.Zero || r != Offset.Zero)) seen = true
            if (l != Offset.Zero && left == Offset.Zero || r != Offset.Zero && right == Offset.Zero) onUse()
        }
        left = l
        right = r
        // The left stick pushed well over counts as a way, for moving about windows.
        button(PadButton.StickUp, ly < -FLICK || ly < -LET_GO && PadButton.StickUp in down)
        button(PadButton.StickDown, ly > FLICK || ly > LET_GO && PadButton.StickDown in down)
        button(PadButton.StickLeft, lx < -FLICK || lx < -LET_GO && PadButton.StickLeft in down)
        button(PadButton.StickRight, lx > FLICK || lx > LET_GO && PadButton.StickRight in down)
    }

    /** A trigger, from 0 to 1, as a button: down past half way, up again under a third. */
    fun trigger(b: PadButton, amount: Float) {
        button(b, amount > FLICK || amount > LET_GO && b in down)
    }

    /** Every button up and the sticks back in the middle, when the controller goes away. */
    fun releaseAll() {
        for (b in down.toList()) button(b, false)
        left = Offset.Zero
        right = Offset.Zero
    }

    /** Takes the stick's middle out and scales the rest back to 0 to 1, so it starts gently. */
    private fun deadZone(x: Float, y: Float): Offset {
        val r = sqrt(x * x + y * y)
        if (r < DEAD) return Offset.Zero
        val scaled = ((r - DEAD) / (1f - DEAD)).coerceAtMost(1f)
        return Offset(x / r * scaled, y / r * scaled)
    }

    private const val DEAD = 0.2f
    private const val FLICK = 0.5f
    private const val LET_GO = 0.3f
}

/**
 * While [enabled], [onButton] hears the controller's buttons before anything
 * added earlier, and says whether it took each one.
 */
@Composable
fun PadListener(enabled: Boolean = true, onButton: (PadButton, Boolean) -> Boolean) {
    val current = rememberUpdatedState(onButton)
    DisposableEffect(enabled) {
        val handler: (PadButton, Boolean) -> Boolean = { b, pressed -> current.value(b, pressed) }
        if (enabled) Pad.handlers += handler
        onDispose { Pad.handlers -= handler }
    }
}
