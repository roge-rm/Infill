package com.rm.infill.platform

import androidx.compose.runtime.Composable

/** The browser's back button leaves the page, as it should; Esc does the rest. */
@Composable
actual fun BackButton(enabled: Boolean, onBack: () -> Unit) {}
