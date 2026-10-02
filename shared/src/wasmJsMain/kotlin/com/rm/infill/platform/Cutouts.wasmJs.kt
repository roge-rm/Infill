package com.rm.infill.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect

/** A browser window has no cameras cut out of it. */
@Composable
actual fun cameraCutouts(): List<Rect> = emptyList()
