package com.rm.infill.platform

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.rm.infill.ui.Pad
import com.rm.infill.ui.PadButton

/**
 * Turns Android's controller events into [Pad]'s. The activity hands its key
 * and motion events here first; what comes from a controller is taken, and
 * keyboards and touches go on as before.
 */
object AndroidPad {
    private var hatX = 0
    private var hatY = 0

    /** Takes a controller's button, and says whether it did. */
    fun key(event: KeyEvent): Boolean {
        val button = BUTTONS[event.keyCode] ?: return false
        // The d-pad's keys come from keyboards and remotes too; only a controller's are taken.
        if (!KeyEvent.isGamepadButton(event.keyCode) && !event.isFromSource(InputDevice.SOURCE_GAMEPAD) && !fromPad(event.device)) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) Pad.button(button, true)
            KeyEvent.ACTION_UP -> Pad.button(button, false)
        }
        return true
    }

    /** Takes the sticks, triggers and a d-pad that reports as a hat, and says whether it did. */
    fun motion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return false
        val device = event.device ?: return false
        fun axis(a: Int): Float {
            val range = device.getMotionRange(a, event.source) ?: return 0f
            val v = event.getAxisValue(a)
            return if (kotlin.math.abs(v) > range.flat) v else 0f
        }
        Pad.sticks(axis(MotionEvent.AXIS_X), axis(MotionEvent.AXIS_Y), axis(MotionEvent.AXIS_Z), axis(MotionEvent.AXIS_RZ))
        Pad.trigger(PadButton.LT, maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE)))
        Pad.trigger(PadButton.RT, maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS)))
        val x = event.getAxisValue(MotionEvent.AXIS_HAT_X).let { if (it < -0.5f) -1 else if (it > 0.5f) 1 else 0 }
        val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y).let { if (it < -0.5f) -1 else if (it > 0.5f) 1 else 0 }
        if (x != hatX) {
            if (hatX != 0) Pad.button(if (hatX < 0) PadButton.Left else PadButton.Right, false)
            if (x != 0) Pad.button(if (x < 0) PadButton.Left else PadButton.Right, true)
            hatX = x
        }
        if (y != hatY) {
            if (hatY != 0) Pad.button(if (hatY < 0) PadButton.Up else PadButton.Down, false)
            if (y != 0) Pad.button(if (y < 0) PadButton.Up else PadButton.Down, true)
            hatY = y
        }
        return true
    }

    private fun fromPad(device: InputDevice?): Boolean {
        val sources = device?.sources ?: return false
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private val BUTTONS = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to PadButton.A,
        KeyEvent.KEYCODE_BUTTON_B to PadButton.B,
        KeyEvent.KEYCODE_BUTTON_X to PadButton.X,
        KeyEvent.KEYCODE_BUTTON_Y to PadButton.Y,
        KeyEvent.KEYCODE_BUTTON_L1 to PadButton.LB,
        KeyEvent.KEYCODE_BUTTON_R1 to PadButton.RB,
        KeyEvent.KEYCODE_BUTTON_L2 to PadButton.LT,
        KeyEvent.KEYCODE_BUTTON_R2 to PadButton.RT,
        KeyEvent.KEYCODE_BUTTON_SELECT to PadButton.Select,
        KeyEvent.KEYCODE_BUTTON_START to PadButton.Start,
        KeyEvent.KEYCODE_BUTTON_THUMBL to PadButton.L3,
        KeyEvent.KEYCODE_BUTTON_THUMBR to PadButton.R3,
        KeyEvent.KEYCODE_DPAD_UP to PadButton.Up,
        KeyEvent.KEYCODE_DPAD_DOWN to PadButton.Down,
        KeyEvent.KEYCODE_DPAD_LEFT to PadButton.Left,
        KeyEvent.KEYCODE_DPAD_RIGHT to PadButton.Right,
    )
}
