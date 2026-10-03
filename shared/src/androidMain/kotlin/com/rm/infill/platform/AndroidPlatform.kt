package com.rm.infill.platform

import android.app.ActivityManager
import android.content.Context
import com.rm.infill.map.GraphicsLevel
import java.io.File

/** Saves go in the app's files folder, settings in its shared preferences. */
class AndroidPlatform(context: Context) : Platform {
    private val dir = File(context.filesDir, "saves").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val hidden = mutableListOf<() -> Unit>()
    private val shown = mutableListOf<() -> Unit>()

    override val devKeys = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun saves(): List<String> =
        dir.listFiles().orEmpty().filter { it.name.endsWith(EXT) }.map { it.name.removeSuffix(EXT) }

    override fun readSave(name: String): ByteArray? = File(dir, name + EXT).takeIf { it.exists() }?.readBytes()

    /** Written to a temporary file and moved into place, so a crash mid-write can't spoil the old save. */
    override fun writeSave(name: String, bytes: ByteArray) {
        val tmp = File(dir, "$name$EXT.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(File(dir, name + EXT))
    }

    override fun deleteSave(name: String) {
        File(dir, name + EXT).delete()
    }

    override fun setting(key: String): String? = prefs.getString(key, null)

    override fun setSetting(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    /** Low on a phone Android calls low on memory, medium on one with a small app heap, high otherwise. */
    override val suggestedGraphics: GraphicsLevel = run {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        when {
            am.isLowRamDevice -> GraphicsLevel.Low
            am.memoryClass < 192 -> GraphicsLevel.Medium
            else -> GraphicsLevel.High
        }
    }

    override fun onHidden(action: () -> Unit) {
        hidden += action
    }

    /** MainActivity calls this from onStop. */
    fun hide() = hidden.forEach { it() }

    override fun onShown(action: () -> Unit) {
        shown += action
    }

    /** MainActivity calls this from onStart. */
    fun show() = shown.forEach { it() }

    private companion object {
        const val EXT = ".infill"
    }
}
