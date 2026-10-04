package com.rm.infill.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.res.advice_garbage_far
import com.rm.infill.res.advice_flooding
import com.rm.infill.res.advice_leisure
import com.rm.infill.res.*
import com.rm.infill.sim.Advice
import com.rm.infill.sim.AdviceKind
import com.rm.infill.sim.Zone
import com.rm.infill.ui.theme.Infill
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The status line under the top strip: what's holding the town back, one
 * thing at a time, the worst first, going round them every few seconds.
 * Tapping it looks at the place, and the next tap shows the next thing. Gone
 * when there's nothing to say, and dimmed once it's said the same for a while.
 */
@Composable
fun AdviceLine(advice: List<Advice>, onLook: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    if (advice.isEmpty()) return
    val c = Infill.colors
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(advice.size) {
        while (true) {
            delay(ADVICE_MS)
            shown++
        }
    }
    // Fresh when what it has to say changes; stale, and dimmed, after a while of the same.
    var fresh by remember { mutableStateOf(true) }
    val said = advice.map { it.kind to it.zone }
    LaunchedEffect(said) {
        fresh = true
        delay(STALE_MS)
        fresh = false
    }
    val alpha by animateFloatAsState(if (fresh) 1f else STALE_ALPHA, tween(FADE_MS))
    val a = advice[shown.mod(advice.size)]
    val text = adviceText(a)
    val look = stringResource(Res.string.look_there)
    // Screen readers hear the worst thing when it changes, without the going round.
    val worst = adviceText(advice[0])
    ChromeBox(modifier.alpha(alpha)) {
        Box(Modifier.size(1.dp).semantics { contentDescription = worst; liveRegion = LiveRegionMode.Polite })
        Row(
            Modifier
                .clickable(onClickLabel = look, role = Role.Button) {
                    if (a.x >= 0) onLook(a.x, a.y)
                    shown++
                }
                .clearAndSetSemantics { contentDescription = text }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlyphIcon(adviceGlyph(a.kind), c.warn, Modifier.size(16.dp))
            Text(text, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (advice.size > 1) Text("${shown.mod(advice.size) + 1}/${advice.size}", color = c.textDim, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun adviceText(a: Advice): String {
    val zone = zoneNoun(a.zone)?.let { stringResource(it) } ?: ""
    return when (a.kind) {
        AdviceKind.DEBT -> stringResource(Res.string.advice_debt)
        AdviceKind.POWER_SHORT -> stringResource(Res.string.advice_power_short)
        AdviceKind.WATER_SHORT -> stringResource(Res.string.advice_water_short)
        AdviceKind.NO_WAY_IN -> stringResource(Res.string.advice_no_way_in)
        AdviceKind.ZONE_MORE -> stringResource(Res.string.advice_zone_more, zone)
        AdviceKind.NO_ROAD -> stringResource(Res.string.advice_no_road, zone)
        AdviceKind.NO_POWER -> stringResource(Res.string.advice_no_power, zone)
        AdviceKind.NO_WATER -> stringResource(Res.string.advice_no_water, zone)
        AdviceKind.NO_SEWER -> stringResource(Res.string.advice_no_sewer, zone)
        AdviceKind.NO_STAFF -> stringResource(Res.string.advice_no_staff, zone)
        AdviceKind.UNAPPEALING -> stringResource(Res.string.advice_unappealing, zone)
        AdviceKind.LEISURE -> stringResource(Res.string.advice_leisure, zone)
        AdviceKind.GARBAGE -> stringResource(Res.string.advice_garbage)
        AdviceKind.GARBAGE_FAR -> stringResource(Res.string.advice_garbage_far)
        AdviceKind.FLOODING -> stringResource(Res.string.advice_flooding)
    }
}

private fun zoneNoun(zone: Byte): StringResource? = when (zone) {
    Zone.RESIDENTIAL -> Res.string.advice_homes
    Zone.COMMERCIAL -> Res.string.advice_shops
    Zone.INDUSTRIAL -> Res.string.advice_works
    Zone.OFFICE -> Res.string.advice_offices
    Zone.FARMLAND -> Res.string.advice_farms
    else -> null
}

internal fun adviceGlyph(kind: AdviceKind): Glyph = when (kind) {
    AdviceKind.DEBT -> Glyph.Coins
    AdviceKind.POWER_SHORT, AdviceKind.NO_POWER -> Glyph.Bolt
    AdviceKind.WATER_SHORT, AdviceKind.NO_WATER, AdviceKind.NO_SEWER -> Glyph.Drop
    AdviceKind.ZONE_MORE -> Glyph.Zone
    AdviceKind.NO_ROAD, AdviceKind.NO_WAY_IN -> Glyph.Road
    AdviceKind.NO_STAFF -> Glyph.Cap
    AdviceKind.UNAPPEALING -> Glyph.Warn
    AdviceKind.LEISURE -> Glyph.Tree
    AdviceKind.GARBAGE, AdviceKind.GARBAGE_FAR -> Glyph.Bin
    AdviceKind.FLOODING -> Glyph.Rain
}

/** How long each piece of advice shows before the next. */
private const val ADVICE_MS = 6000L

/** After this long saying the same, the line dims to this, taking this long to fade. */
private const val STALE_MS = 20_000L
private const val STALE_ALPHA = 0.6f
private const val FADE_MS = 600
