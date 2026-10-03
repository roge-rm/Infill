package com.rm.infill.ui

import androidx.compose.runtime.Composable

/**
 * The page answers navigator.languages with the chosen language while one is
 * set (see index.html), and that's where the strings are looked up.
 */
@Composable
actual fun InPlatformLanguage(language: String, content: @Composable () -> Unit) {
    chooseLanguage(language)
    content()
}

private fun chooseLanguage(language: String): Unit = js("window.__infillLanguage = language || null")
