package com.rm.infill

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rm.infill.platform.AndroidPad
import com.rm.infill.platform.AndroidPlatform
import com.rm.infill.platform.platform
import com.rm.infill.ui.Pad

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draws behind the bars and into the camera cutout. The map fills the
        // whole screen and the bars over it keep clear of the cutout.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        goFullScreen()
        platform = androidPlatform
        Pad.pressFocused = ::pressFocused
        setContent { App() }
    }

    private val androidPlatform by lazy { AndroidPlatform(applicationContext) }

    /** A controller's buttons go to the game's controller handling, the rest to the screen as usual. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = AndroidPad.key(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = AndroidPad.motion(event) || super.dispatchGenericMotionEvent(event)

    /** Presses what has the focus, as Enter would, for a controller's A. */
    private fun pressFocused() {
        val now = SystemClock.uptimeMillis()
        super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
        super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
    }

    /** Back again: the sound picks up. */
    override fun onStart() {
        super.onStart()
        androidPlatform.show()
    }

    /** Put away: the game saves itself and goes quiet. */
    override fun onStop() {
        super.onStop()
        androidPlatform.hide()
        Pad.releaseAll()
    }

    /** Hides the status and navigation bars. They come back on a swipe and hide again after. */
    private fun goFullScreen() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
