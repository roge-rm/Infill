package com.rm.infill.platform

import androidx.compose.runtime.Composable

/** Desktop uses the shared Esc handlers. */
@Composable
actual fun SystemBackButton(enabled: Boolean, onBack: () -> Unit) {}
