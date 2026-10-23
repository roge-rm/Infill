package com.rm.infill.platform

import com.rm.infill.ui.Pad
import com.rm.infill.ui.PadButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPadTest {
    @Test
    fun mapsButtonsSticksAndTriggers() {
        val heard = mutableListOf<Pair<PadButton, Boolean>>()
        val handler: (PadButton, Boolean) -> Boolean = { b, p -> heard += b to p; true }
        Pad.handlers += handler
        try {
            val buttons = BooleanArray(15)
            val axes = floatArrayOf(0f, 0f, 0f, 0f, -1f, -1f)
            buttons[0] = true   // A
            buttons[4] = true   // left bumper
            buttons[6] = true   // back
            buttons[11] = true  // d-pad up
            buttons[14] = true  // d-pad left
            axes[5] = 1f        // right trigger fully in
            axes[2] = 1f        // right stick right
            DesktopPad.apply(buttons, axes)
            val down = heard.filter { it.second }.map { it.first }.toSet()
            assertEquals(setOf(PadButton.A, PadButton.LB, PadButton.Select, PadButton.Up, PadButton.Left, PadButton.RT), down)
            assertTrue(Pad.right.x > 0.9f)
            assertEquals(0f, Pad.left.x)
            DesktopPad.apply(BooleanArray(15), floatArrayOf(0f, 0f, 0f, 0f, -1f, -1f))
            assertEquals(down, heard.filter { !it.second }.map { it.first }.toSet())
        } finally {
            Pad.handlers -= handler
            Pad.releaseAll()
        }
    }

    @Test
    fun startingWithoutNativesReturnsAtOnceWithNoPad() {
        val before = Pad.pressFocused
        try {
            val t = System.nanoTime()
            // The shared tests have no LWJGL natives, so GLFW can't load.
            assertFalse(DesktopPad.start())
            assertTrue(System.nanoTime() - t < 1_900_000_000L)
        } finally {
            Pad.pressFocused = before
        }
    }
}
