package com.rm.infill.platform

import com.rm.infill.map.GraphicsLevel

/**
 * What the game needs from the platform: somewhere to keep saves and
 * settings, a good graphics level for this device, and word when the app is
 * put away so it can save.
 */
interface Platform {
    /** Names of the saves there are. */
    fun saves(): List<String>
    fun readSave(name: String): ByteArray?
    fun writeSave(name: String, bytes: ByteArray)
    fun deleteSave(name: String)

    fun setting(key: String): String?
    fun setSetting(key: String, value: String?)

    /** A graphics level that suits this device, used until the player picks one. */
    val suggestedGraphics: GraphicsLevel

    /** How much a mouse wheel scrolls for one notch: a notch on Android, pixels in a browser. */
    val scrollPerNotch: Float get() = 1f

    /** Played with a mouse and keyboard, for the help's wording: a click for a tap. */
    val onDesktop: Boolean get() = false

    /** Whether the keys for looking at seasons, hours, weather and fires work: only in a debug build. */
    val devKeys: Boolean get() = false

    /** Hands [bytes] to whatever the player picks to send or keep them with, as a file called [fileName]. */
    fun shareFile(fileName: String, mime: String, bytes: ByteArray)

    /** Asks the player for a file to open; [onOpened] gets its name and what's in it, if they pick one. */
    fun openFile(onOpened: (name: String, bytes: ByteArray) -> Unit)

    /** Shares a picture [width] by [height] pixels, [argb] row by row, as a PNG called [fileName]. */
    fun sharePicture(fileName: String, width: Int, height: Int, argb: IntArray)

    /** Called when the app goes into the background or the page is hidden. */
    fun onHidden(action: () -> Unit)

    /** Called when the app or the page comes back. */
    fun onShown(action: () -> Unit)
}

/** The platform the app is running on, set once when it starts. */
lateinit var platform: Platform

/** The file name a save is kept under: letters, digits and dashes only. */
fun saveFileName(name: String): String {
    val slug = name.trim().lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-')
    return slug.ifEmpty { "town" }
}

const val AUTOSAVE = "autosave"

/** Where the cameras are cut out of the screen, in window pixels; none in a browser. */
@androidx.compose.runtime.Composable
expect fun cameraCutouts(): List<androidx.compose.ui.geometry.Rect>

/** While [enabled], the system back button (Android's) calls [onBack] instead of leaving the app. */
@androidx.compose.runtime.Composable
expect fun SystemBackButton(enabled: Boolean, onBack: () -> Unit)

/**
 * While [enabled], the back button and Esc call [onBack]. The one added last
 * goes first, so Esc closes the window on top, as back does.
 */
@androidx.compose.runtime.Composable
fun BackButton(enabled: Boolean, onBack: () -> Unit) {
    SystemBackButton(enabled, onBack)
    val current = androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.compose.runtime.DisposableEffect(enabled) {
        val handler = { current.value() }
        if (enabled) Escape.handlers += handler
        onDispose { Escape.handlers -= handler }
    }
}

/** What Esc does, newest first. */
object Escape {
    internal val handlers = mutableListOf<() -> Unit>()

    /** Does what Esc does now, and says whether there was anything. */
    fun press(): Boolean = handlers.lastOrNull()?.let { it(); true } ?: false
}
