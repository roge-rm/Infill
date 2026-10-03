package com.rm.infill.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun SystemBackButton(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
