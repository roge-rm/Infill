package com.rm.infill.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect

/** A desktop window has no camera cutouts. */
@Composable
actual fun cameraCutouts(): List<Rect> = emptyList()
