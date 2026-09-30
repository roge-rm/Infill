package com.rm.infill.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour the interface uses, by role. Screens read these through
 * `Infill.colors` and never write a colour of their own, so the light and dark
 * themes reach everything.
 */
@Immutable
data class InfillColors(
    /** Behind the map, past its edges. */
    val page: Color,
    /** The bars and panels over the map. */
    val chrome: Color,
    val chromeEdge: Color,
    val button: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val onAccent: Color,
    val isLight: Boolean,
)

val DarkColors = InfillColors(
    page = Color(0xFF121417),
    chrome = Color(0xEB1C1F24),
    chromeEdge = Color(0xFF30353D),
    button = Color(0xFF2A2E35),
    text = Color(0xFFE8E6E1),
    textDim = Color(0xFF9AA0A6),
    accent = Color(0xFFF0B429),
    onAccent = Color(0xFF1C1F24),
    isLight = false,
)

val LightColors = InfillColors(
    page = Color(0xFFD8D2C4),
    chrome = Color(0xEBF4F1EA),
    chromeEdge = Color(0xFFCFC8B8),
    button = Color(0xFFE4DFD4),
    text = Color(0xFF22262B),
    textDim = Color(0xFF636A72),
    accent = Color(0xFFE0A100),
    onAccent = Color(0xFF1C1F24),
    isLight = true,
)

/** Colours that belong to the map rather than the interface, the same in both themes. */
object MapColors {
    val grass = Color(0xFF5A9A3C)
    val water = Color(0xFF3A6FB0)
    val trees = Color(0xFF2F6B2A)
    val dirt = Color(0xFF9A7A4F)
    val gridLine = Color(0x1F000000)
}
