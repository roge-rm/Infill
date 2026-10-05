package com.rm.infill.platform

import android.app.ActivityManager
import android.content.Context
import com.rm.infill.map.GraphicsLevel
import java.io.File

/** Saves go in the app's files folder, settings in its shared preferences. */
class AndroidPlatform(private val context: Context) : Platform {
    private val dir = File(context.filesDir, "saves").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val hidden = mutableListOf<() -> Unit>()
    private val shown = mutableListOf<() -> Unit>()

    override val devKeys = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Saves dropped into the app's own folder on the device's storage
     * (Android/data/com.rm.infill/files/import), say from another phone or a
     * computer, are taken in when the saves are next listed. One with the name
     * of a save already here comes in under a new name.
     */
    private val importDir: File? = context.getExternalFilesDir("import")

    private fun takeImports() {
        val files = importDir?.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(EXT) }
        for (f in files) {
            val base = f.name.removeSuffix(EXT)
            var name = base
            var n = 2
            while (File(dir, name + EXT).exists()) name = "$base-${n++}"
            runCatching {
                f.copyTo(File(dir, name + EXT))
                f.delete()
            }
        }
    }

    override fun saves(): List<String> {
        takeImports()
        return dir.listFiles().orEmpty().filter { it.name.endsWith(EXT) }.map { it.name.removeSuffix(EXT) }
    }

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

    /** Written to the cache and offered through the share sheet. */
    override fun shareFile(fileName: String, mime: String, bytes: ByteArray) {
        val shared = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(shared, fileName)
        file.writeBytes(bytes)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(android.content.Intent.EXTRA_STREAM, uri)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(android.content.Intent.createChooser(send, fileName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun sharePicture(fileName: String, width: Int, height: Int, argb: IntArray) {
        val bitmap = android.graphics.Bitmap.createBitmap(argb, width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        shareFile(fileName, "image/png", out.toByteArray())
    }

    /** MainActivity sets this to show the system's file picker. */
    var pickFile: (() -> Unit)? = null
    private var onOpened: ((String, ByteArray) -> Unit)? = null

    override fun openFile(onOpened: (name: String, bytes: ByteArray) -> Unit) {
        this.onOpened = onOpened
        pickFile?.invoke()
    }

    /** MainActivity calls this with the file picked, if any. */
    fun picked(uri: android.net.Uri?) {
        val call = onOpened ?: return
        onOpened = null
        if (uri == null) return
        val resolver = context.contentResolver
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return
        var name = "town"
        runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0).removeSuffix(EXT)
            }
        }
        call(name, bytes)
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
