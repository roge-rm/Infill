package com.rm.infill.platform

import com.rm.infill.map.GraphicsLevel
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Saves and settings live in the browser's local storage; saves as text, since that's all it keeps. */
@OptIn(ExperimentalEncodingApi::class)
object WebPlatform : Platform {
    private const val SAVE = "infill.save."
    private const val SETTING = "infill.setting."

    override fun saves(): List<String> =
        (0 until localStorage.length).mapNotNull { localStorage.key(it) }.filter { it.startsWith(SAVE) }.map { it.removePrefix(SAVE) }

    override fun readSave(name: String): ByteArray? = localStorage.getItem(SAVE + name)?.let { Base64.decode(it) }

    override fun writeSave(name: String, bytes: ByteArray) = localStorage.setItem(SAVE + name, Base64.encode(bytes))

    override fun deleteSave(name: String) = localStorage.removeItem(SAVE + name)

    override fun setting(key: String): String? = localStorage.getItem(SETTING + key)

    override fun setSetting(key: String, value: String?) {
        if (value == null) localStorage.removeItem(SETTING + key) else localStorage.setItem(SETTING + key, value)
    }

    override val suggestedGraphics = GraphicsLevel.High

    /** Browsers give the wheel in pixels, about a hundred a notch. */
    override val scrollPerNotch = 100f

    override val onDesktop = true

    /** Downloaded, which is as near as a browser comes to sharing a file. */
    override fun shareFile(fileName: String, mime: String, bytes: ByteArray) {
        val data = newBytes(bytes.size)
        for (i in bytes.indices) setByte(data, i, bytes[i].toInt())
        download(fileName, mime, data)
    }

    override fun openFile(onOpened: (name: String, bytes: ByteArray) -> Unit) {
        pickFile { name, data ->
            val size = lengthOf(data)
            onOpened(name.removeSuffix(".infill"), ByteArray(size) { byteAt(data, it).toByte() })
        }
    }

    /** Drawn onto a canvas and downloaded as a PNG. */
    override fun sharePicture(fileName: String, width: Int, height: Int, argb: IntArray) {
        val rgba = newBytes(width * height * 4)
        for (i in argb.indices) {
            val p = argb[i]
            setByte(rgba, i * 4, (p shr 16) and 0xff)
            setByte(rgba, i * 4 + 1, (p shr 8) and 0xff)
            setByte(rgba, i * 4 + 2, p and 0xff)
            setByte(rgba, i * 4 + 3, (p ushr 24) and 0xff)
        }
        downloadPicture(fileName, width, height, rgba)
    }

    override fun onHidden(action: () -> Unit) {
        document.addEventListener("visibilitychange", { if (pageHidden()) action() })
    }

    override fun onShown(action: () -> Unit) {
        document.addEventListener("visibilitychange", { if (!pageHidden()) action() })
    }
}

private fun pageHidden(): Boolean = js("document.hidden")

private fun newBytes(size: Int): JsAny = js("new Uint8ClampedArray(size)")

private fun setByte(array: JsAny, index: Int, value: Int): Unit = js("array[index] = value")

private fun lengthOf(array: JsAny): Int = js("array.length")

private fun byteAt(array: JsAny, index: Int): Int = js("array[index]")

private fun download(name: String, mime: String, data: JsAny): Unit = js("""{
    const url = URL.createObjectURL(new Blob([data], { type: mime }));
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
}""")

private fun downloadPicture(name: String, width: Int, height: Int, rgba: JsAny): Unit = js("""{
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    canvas.getContext('2d').putImageData(new ImageData(rgba, width, height), 0, 0);
    canvas.toBlob(blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = name;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
    }, 'image/png');
}""")

private fun pickFile(onPicked: (String, JsAny) -> Unit): Unit = js("""{
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.infill';
    input.onchange = () => {
        const f = input.files[0];
        if (f) f.arrayBuffer().then(b => onPicked(f.name, new Uint8Array(b)));
    };
    input.click();
}""")
