package com.rm.infill.platform

import com.rm.infill.map.GraphicsLevel
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties
import javax.imageio.ImageIO

/** Desktop data follows XDG_DATA_HOME, independently of the installation folder. */
class DesktopPlatform(val folder: File = File(
    System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
        ?: "${System.getProperty("user.home")}/.local/share", "infill",
)) : Platform {
    private val saveFolder = File(folder, "saves").apply { mkdirs() }
    private val settingsFile = File(folder, "settings.properties")
    private val settings = Properties().apply {
        if (settingsFile.exists()) settingsFile.inputStream().use { load(it) }
    }
    private var hidden: () -> Unit = {}
    private var shown: () -> Unit = {}

    private fun save(name: String) = File(saveFolder, "${saveFileName(name)}.infill")
    override fun saves(): List<String> = saveFolder.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "infill" }.map { it.nameWithoutExtension }.sorted()
    override fun readSave(name: String): ByteArray? = save(name).takeIf { it.isFile }?.readBytes()
    override fun writeSave(name: String, bytes: ByteArray) = atomicWrite(save(name), bytes)
    override fun deleteSave(name: String) { save(name).delete() }
    override fun setting(key: String): String? = settings.getProperty(key)
    override fun setSetting(key: String, value: String?) {
        if (value == null) settings.remove(key) else settings.setProperty(key, value)
        val bytes = java.io.ByteArrayOutputStream().apply { settings.store(this, null) }.toByteArray()
        atomicWrite(settingsFile, bytes)
    }
    override val suggestedGraphics = GraphicsLevel.High
    override val onDesktop = true

    override fun shareFile(fileName: String, mime: String, bytes: ByteArray) {
        chooseFile(FileDialog.SAVE, fileName)?.let { atomicWrite(it, bytes) }
    }
    override fun openFile(onOpened: (String, ByteArray) -> Unit) {
        chooseFile(FileDialog.LOAD, "*.infill")?.let { onOpened(it.nameWithoutExtension, it.readBytes()) }
    }
    override fun sharePicture(fileName: String, width: Int, height: Int, argb: IntArray) {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, width, height, argb, 0, width)
        val bytes = java.io.ByteArrayOutputStream().apply { ImageIO.write(image, "png", this) }.toByteArray()
        shareFile(fileName, "image/png", bytes)
    }
    override fun onHidden(action: () -> Unit) { hidden = action }
    override fun onShown(action: () -> Unit) { shown = action }
    fun hide() = hidden()
    fun show() = shown()

    private fun chooseFile(mode: Int, name: String): File? {
        val dialog = FileDialog(null as Frame?, if (mode == FileDialog.SAVE) "Save file" else "Open town", mode)
        return try {
            dialog.file = name
            dialog.isVisible = true
            dialog.file?.let { File(dialog.directory, it) }
        } finally { dialog.dispose() }
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        file.parentFile.mkdirs()
        val tmp = Files.createTempFile(file.parentFile.toPath(), ".${file.name}", ".tmp")
        try {
            Files.write(tmp, bytes)
            Files.move(tmp, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(tmp) }
    }
}
