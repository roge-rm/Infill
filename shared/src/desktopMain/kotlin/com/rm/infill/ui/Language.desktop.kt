package com.rm.infill.ui

import androidx.compose.runtime.Composable
import java.util.Locale

private val systemLocale = Locale.getDefault()

@Composable
actual fun InPlatformLanguage(language: String, content: @Composable () -> Unit) {
    val locale = if (language.isEmpty()) systemLocale else Locale.forLanguageTag(language)
    Locale.setDefault(locale)
    content()
}
