package com.rm.infill.platform

import com.rm.infill.ui.Pad
import com.rm.infill.ui.PadButton
import kotlinx.browser.window

/**
 * Controllers in the browser. The page only learns about them by asking, so
 * once one is connected they're read every frame and passed on to [Pad].
 */
object WebPad {
    private var polling = false

    fun start() {
        window.addEventListener("gamepadconnected", { poll() })
        window.addEventListener("gamepaddisconnected", { Pad.releaseAll() })
        Pad.pressFocused = ::pressFocused
        poll()
    }

    private fun poll() {
        if (polling) return
        polling = true
        window.requestAnimationFrame { frame() }
    }

    private fun frame() {
        val state = padState()
        if (state.isEmpty()) {
            // Gone: stop asking until another comes.
            polling = false
            Pad.releaseAll()
            return
        }
        val (buttons, axes) = state.split('|').map { part -> part.split(',').map { it.toFloatOrNull() ?: 0f } }
        for ((i, b) in BUTTONS.withIndex()) {
            val v = buttons.getOrElse(i) { 0f }
            if (b == PadButton.LT || b == PadButton.RT) Pad.trigger(b, v) else Pad.button(b, v > 0.5f)
        }
        Pad.sticks(axes.getOrElse(0) { 0f }, axes.getOrElse(1) { 0f }, axes.getOrElse(2) { 0f }, axes.getOrElse(3) { 0f })
        window.requestAnimationFrame { frame() }
    }

    /** The standard layout's buttons, in the browser's order. */
    private val BUTTONS = listOf(
        PadButton.A, PadButton.B, PadButton.X, PadButton.Y, PadButton.LB, PadButton.RB, PadButton.LT, PadButton.RT,
        PadButton.Select, PadButton.Start, PadButton.L3, PadButton.R3, PadButton.Up, PadButton.Down, PadButton.Left, PadButton.Right,
    )
}

/** The first controller's buttons and axes, as "b,b,…|a,a,…", or nothing when there's none. */
private fun padState(): String = js(
    """(() => {
        const pads = navigator.getGamepads ? navigator.getGamepads() : [];
        for (const g of pads) {
            if (g && g.connected) return g.buttons.map(b => b.value.toFixed(2)).join(',') + '|' + g.axes.map(a => a.toFixed(2)).join(',');
        }
        return '';
    })()""",
)

/**
 * Presses what has the focus, by sending Enter to the canvas the game's keys
 * go to, inside its shadow root. It's sent after this frame, as a real key would be.
 */
private fun pressFocused(): Unit = js(
    """(() => {
        const find = (root) => {
            for (const e of root.querySelectorAll('*')) {
                if (e.tagName === 'CANVAS') return e;
                if (e.shadowRoot) { const c = find(e.shadowRoot); if (c) return c; }
            }
            return null;
        };
        const to = find(document) || document.body;
        const send = (type) => {
            const e = new KeyboardEvent(type, { key: 'Enter', code: 'Enter', bubbles: true, cancelable: true });
            // Made-up key events don't take a key code, and the game reads it.
            Object.defineProperty(e, 'keyCode', { get: () => 13 });
            Object.defineProperty(e, 'which', { get: () => 13 });
            to.dispatchEvent(e);
        };
        setTimeout(() => { send('keydown'); setTimeout(() => send('keyup'), 30); }, 0);
    })()""",
)
