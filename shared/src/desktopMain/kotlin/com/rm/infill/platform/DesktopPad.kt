package com.rm.infill.platform

import com.rm.infill.ui.Pad
import com.rm.infill.ui.PadButton
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWGamepadState

/**
 * Controllers on the desktop. GLFW is started on its own thread, without a
 * window, and the first controller it knows as a gamepad (a Steam Deck's own
 * controls show up as one through Steam Input) is read about 60 times a
 * second and passed to [Pad] on the window's thread. If GLFW won't start,
 * there's just no controller.
 */
object DesktopPad {
    private var thread: Thread? = null

    private val started = CountDownLatch(1)
    @Volatile private var working = false
    private var focused = false

    /**
     * Starts reading controllers, and waits up to two seconds for GLFW to start
     * or fail, so its X11 set-up doesn't overlap AWT's. Says whether GLFW started.
     */
    fun start(): Boolean {
        if (thread == null) {
            Pad.pressFocused = ::pressFocused
            thread = Thread(::run, "gamepad").apply { isDaemon = true; start() }
        }
        started.await(2, TimeUnit.SECONDS)
        return working
    }

    private fun run() {
        try {
            init()
        } finally {
            started.countDown()
        }
        if (working) poll()
    }

    private fun init() {
        try {
            GLFW.glfwSetErrorCallback { code, text -> System.err.println("Infill: GLFW error $code: ${GLFWErrorCallback.getDescription(text)}") }
            if (!GLFW.glfwInit()) {
                System.err.println("Infill: no controller support, GLFW won't start")
                return
            }
            working = true
        } catch (e: Throwable) {
            System.err.println("Infill: no controller support, GLFW won't load: $e")
        }
    }

    private fun poll() {
        val state = GLFWGamepadState.create()
        val buttons = BooleanArray(BUTTON_COUNT)
        val axes = FloatArray(AXIS_COUNT)
        var present = false
        while (true) {
            try {
                // Lets GLFW notice controllers plugged in or taken out.
                GLFW.glfwPollEvents()
                val id = (GLFW.GLFW_JOYSTICK_1..GLFW.GLFW_JOYSTICK_LAST).firstOrNull { GLFW.glfwJoystickIsGamepad(it) }
                if (id != null && GLFW.glfwGetGamepadState(id, state)) {
                    for (i in 0 until BUTTON_COUNT) buttons[i] = state.buttons(i).toInt() == GLFW.GLFW_PRESS
                    for (i in 0 until AXIS_COUNT) axes[i] = state.axes(i)
                    present = true
                    val b = buttons.copyOf()
                    val a = axes.copyOf()
                    SwingUtilities.invokeLater { if (inFocus()) apply(b, a) }
                } else if (present) {
                    present = false
                    SwingUtilities.invokeLater { Pad.releaseAll() }
                }
            } catch (e: Throwable) {
                System.err.println("Infill: controller read failed: $e")
                return
            }
            Thread.sleep(16)
        }
    }

    /** On the window's thread: whether an Infill window is active. Releases everything once when it stops being. */
    private fun inFocus(): Boolean {
        val now = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow != null
        if (!now && focused) Pad.releaseAll()
        focused = now
        return now
    }

    /**
     * Passes one reading on. [buttons] and [axes] are in GLFW's gamepad order:
     * A, B, X, Y, the bumpers, back, start, guide, the stick clicks, then the
     * d-pad; and the sticks' x and y, then the triggers, which rest at -1.
     */
    internal fun apply(buttons: BooleanArray, axes: FloatArray) {
        for ((i, b) in BUTTONS.withIndex()) {
            if (b != null) Pad.button(b, buttons.getOrElse(i) { false })
        }
        Pad.trigger(PadButton.LT, (axes.getOrElse(4) { -1f } + 1f) / 2f)
        Pad.trigger(PadButton.RT, (axes.getOrElse(5) { -1f } + 1f) / 2f)
        Pad.sticks(axes.getOrElse(0) { 0f }, axes.getOrElse(1) { 0f }, axes.getOrElse(2) { 0f }, axes.getOrElse(3) { 0f })
    }

    private const val BUTTON_COUNT = 15
    private const val AXIS_COUNT = 6

    /** By GLFW's button number; the guide button isn't used. */
    private val BUTTONS = listOf(
        PadButton.A, PadButton.B, PadButton.X, PadButton.Y, PadButton.LB, PadButton.RB,
        PadButton.Select, PadButton.Start, null, PadButton.L3, PadButton.R3,
        PadButton.Up, PadButton.Right, PadButton.Down, PadButton.Left,
    )

    /** Sends Enter to whatever has the keyboard, as a key would. */
    private fun pressFocused() {
        val target = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return
        val now = System.currentTimeMillis()
        target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_ENTER, '\n'))
        target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, now, 0, KeyEvent.VK_ENTER, '\n'))
    }
}
