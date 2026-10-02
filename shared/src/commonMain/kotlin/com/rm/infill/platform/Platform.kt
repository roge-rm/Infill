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

    /** Called when the app goes into the background or the page is hidden. */
    fun onHidden(action: () -> Unit)
}

/** The platform the app is running on, set once when it starts. */
lateinit var platform: Platform

/** The file name a save is kept under: letters, digits and dashes only. */
fun saveFileName(name: String): String {
    val slug = name.trim().lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-')
    return slug.ifEmpty { "town" }
}

const val AUTOSAVE = "autosave"

/** While [enabled], the system back button (Android's) calls [onBack] instead of leaving the app. */
@androidx.compose.runtime.Composable
expect fun BackButton(enabled: Boolean, onBack: () -> Unit)
