package com.rm.infill

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import com.rm.infill.platform.WebPad
import com.rm.infill.platform.WebPlatform
import com.rm.infill.platform.platform

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    platform = WebPlatform
    WebPad.start()
    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) {
        App()
    }
}
