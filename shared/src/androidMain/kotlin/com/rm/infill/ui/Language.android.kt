package com.rm.infill.ui

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Android looks strings up in the configuration's locale and the default one, so both are set. */
@Composable
actual fun InPlatformLanguage(language: String, content: @Composable () -> Unit) {
    val system = remember { Resources.getSystem().configuration.locales[0] }
    val locale = if (language.isEmpty()) system else Locale.forLanguageTag(language)
    val context = LocalContext.current
    val base = LocalConfiguration.current
    val configuration = remember(base, locale) { Configuration(base).apply { setLocale(locale) } }
    Locale.setDefault(locale)
    @Suppress("DEPRECATION")
    context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}
