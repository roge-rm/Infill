package com.rm.infill.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.rm.infill.App
import com.rm.infill.audio.AudioEngine
import com.rm.infill.platform.DesktopPlatform
import com.rm.infill.platform.platform
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

fun main() {
    val desktop = DesktopPlatform()
    platform = desktop
    application {
        Window(
            title = "Infill",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
            onCloseRequest = {
                desktop.hide()
                AudioEngine.stop()
                exitApplication()
            },
        ) {
            DisposableEffect(window) {
                val listener = object : WindowAdapter() {
                    override fun windowIconified(e: WindowEvent) = desktop.hide()
                    override fun windowDeiconified(e: WindowEvent) = desktop.show()
                }
                window.addWindowListener(listener)
                onDispose { window.removeWindowListener(listener) }
            }
            App()
        }
    }
}
