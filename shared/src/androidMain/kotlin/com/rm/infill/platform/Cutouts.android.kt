package com.rm.infill.platform

import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView

@Composable
actual fun cameraCutouts(): List<Rect> {
    // Read so a rotation, or the insets arriving, works them out again.
    WindowInsets.displayCutout.getTop(LocalDensity.current)
    LocalConfiguration.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
    val cut = LocalView.current.rootWindowInsets?.displayCutout ?: return emptyList()
    return cut.boundingRects.map { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
}
