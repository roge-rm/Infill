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

    override fun onHidden(action: () -> Unit) {
        document.addEventListener("visibilitychange", { if (pageHidden()) action() })
    }

    override fun onShown(action: () -> Unit) {
        document.addEventListener("visibilitychange", { if (!pageHidden()) action() })
    }
}

private fun pageHidden(): Boolean = js("document.hidden")
