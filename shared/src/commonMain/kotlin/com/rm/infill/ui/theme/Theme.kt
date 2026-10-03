package com.rm.infill.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ripple
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalInfillColors = staticCompositionLocalOf { DarkColors }

object Infill {
    val colors: InfillColors
        @Composable @ReadOnlyComposable get() = LocalInfillColors.current
}

/** Follows the device's light or dark setting. Material's scheme is built from the palette. */
@Composable
fun InfillTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.page, onBackground = c.text,
            surface = c.chrome, onSurface = c.text, onSurfaceVariant = c.textDim,
            outline = c.chromeEdge,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.page, onBackground = c.text,
            surface = c.chrome, onSurface = c.text, onSurfaceVariant = c.textDim,
            outline = c.chromeEdge,
        )
    }
    CompositionLocalProvider(LocalInfillColors provides c) {
        MaterialTheme(colorScheme = scheme) {
            // Everything pressable keeps its ripple, and shows a ring when the keys have it.
            CompositionLocalProvider(LocalIndication provides FocusRing(c.accent), content = content)
        }
    }
}

/** The ripple on a press, and a ring in [colour] round whatever the keyboard has focused. */
private class FocusRing(private val colour: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FocusRingNode(interactionSource, colour)
    override fun equals(other: Any?) = other is FocusRing && other.colour == colour
    override fun hashCode() = colour.hashCode()
}

private class FocusRingNode(private val source: InteractionSource, private val colour: Color) : DelegatingNode(), DrawModifierNode {
    private var focused = false

    init {
        delegate(ripple().create(source))
    }

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect {
                when (it) {
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    else -> return@collect
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) return
        val w = RING.toPx()
        drawRoundRect(colour, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(CORNER.toPx()), style = Stroke(w))
    }

    private companion object {
        val RING = 3.dp
        val CORNER = 8.dp
    }
}
