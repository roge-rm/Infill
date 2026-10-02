package com.rm.infill.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.map.TileAtlas
import com.rm.infill.res.Res
import com.rm.infill.res.close
import com.rm.infill.res.tap_again
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.stringResource

/** The tile atlas, for panels that show a building's own picture. Null until it's loaded. */
val LocalAtlas = staticCompositionLocalOf<TileAtlas?> { null }

/** How a figure is doing. */
enum class Tone { Plain, Good, Warn, Bad }

@Composable
fun toneColour(tone: Tone): Color {
    val c = Infill.colors
    return when (tone) {
        Tone.Plain -> c.accent
        Tone.Good -> c.good
        Tone.Warn -> c.warn
        Tone.Bad -> c.bad
    }
}

/** Good at [goodFrom] percent or more, bad under [badUnder], so-so between. */
fun toneOf(percent: Int, goodFrom: Int = 75, badUnder: Int = 40): Tone = when {
    percent >= goodFrom -> Tone.Good
    percent < badUnder -> Tone.Bad
    else -> Tone.Warn
}

/** The text sizes the panels use. */
object Type {
    val title = 16.sp
    val body = 14.sp
    val label = 11.sp
    val caption = 11.sp
}

/** A card's top: its picture, its name, a line under it, and a close button. */
@Composable
fun CardHeader(icon: ChoiceIcon, title: String, subtitle: String?, onClose: (() -> Unit)?) {
    val c = Infill.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceTile(LocalAtlas.current, icon, title, false, 44.dp, null)
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = Type.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = c.textDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        onClose?.let { CloseButton(it) }
    }
}

@Composable
fun CloseButton(onClose: () -> Unit) {
    val c = Infill.colors
    val label = stringResource(Res.string.close)
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(c.button)
            .semantics { contentDescription = label }.clickable(role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(Glyph.Remove, c.textDim, Modifier.size(14.dp)) }
}

/** A word about how it is, in its tone's colour. */
class PillItem(val glyph: Glyph, val text: String, val tone: Tone)

@Composable
fun Pills(pills: List<PillItem>) {
    if (pills.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in pills) Pill(p)
    }
}

@Composable
fun Pill(p: PillItem) {
    // A plain one is neither good nor bad.
    val colour = if (p.tone == Tone.Plain) Infill.colors.textDim else toneColour(p.tone)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(colour.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlyphIcon(p.glyph, colour, Modifier.size(13.dp))
        Text(p.text, color = Infill.colors.text, fontSize = 12.sp, maxLines = 1)
    }
}

/**
 * A figure: its drawing, what it is, and its value, with a meter when it's
 * a share of something ([fraction], 0 to 1), or a bar of [parts] when it's
 * made up of several. [wide] takes a whole row.
 */
class StatItem(
    val glyph: Glyph,
    val label: String,
    val value: String,
    val fraction: Float? = null,
    val tone: Tone = Tone.Plain,
    val parts: List<Pair<Int, Color>>? = null,
    val wide: Boolean = false,
)

/** Figures two to a row, or three on a wide panel; a wide one takes its own row. */
@Composable
fun StatGrid(stats: List<StatItem>, columns: Int = 2) {
    if (stats.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rows = ArrayList<List<StatItem>>()
        var row = ArrayList<StatItem>()
        for (s in stats) {
            if (s.wide) {
                if (row.isNotEmpty()) { rows += row; row = ArrayList() }
                rows += listOf(s)
            } else {
                row += s
                if (row.size == columns) { rows += row; row = ArrayList() }
            }
        }
        if (row.isNotEmpty()) rows += row
        for (r in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (s in r) StatCell(s, Modifier.weight(1f))
                if (!r.first().wide) repeat(columns - r.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun StatCell(s: StatItem, modifier: Modifier = Modifier) {
    val c = Infill.colors
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(c.button), contentAlignment = Alignment.Center) {
            GlyphIcon(s.glyph, if (s.tone == Tone.Plain || s.fraction == null) c.textDim else toneColour(s.tone), Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.label, color = c.textDim, fontSize = Type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.value, color = c.text, fontSize = Type.body, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            s.fraction?.let { Meter(it, toneColour(s.tone), Modifier.fillMaxWidth()) }
            s.parts?.let { StackedBar(it, Modifier.fillMaxWidth()) }
        }
    }
}

/** A thin bar filled to [fraction]. */
@Composable
fun Meter(fraction: Float, colour: Color, modifier: Modifier = Modifier, height: Dp = 5.dp) {
    val back = Infill.colors.button
    Canvas(modifier.height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(back, cornerRadius = r)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(colour, size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
    }
}

/** A bar made of [parts], each as wide as its share. */
@Composable
fun StackedBar(parts: List<Pair<Int, Color>>, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val back = Infill.colors.button
    val total = parts.sumOf { it.first }.toFloat()
    Canvas(modifier.height(height).clip(RoundedCornerShape(height / 2))) {
        drawRect(back)
        if (total <= 0f) return@Canvas
        var x = 0f
        for ((n, colour) in parts) {
            val w = size.width * n / total
            drawRect(colour, Offset(x, 0f), Size(w, size.height))
            x += w
        }
    }
}

/** A key for a stacked bar: each part's colour, name and count. */
@Composable
fun BarKey(parts: List<Triple<String, Int, Color>>, percent: Boolean = false) {
    val c = Infill.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((name, n, colour) in parts) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(colour))
                Text(if (percent) "$name $n%" else "$name ${groupThousands(n.toLong())}", color = c.textDim, fontSize = Type.caption)
            }
        }
    }
}

/** A small reading of a 0 to 255 level: its drawing and a short meter, red when it's high and that's bad. */
@Composable
fun MiniMeter(glyph: Glyph, label: String, value: Int, highIsBad: Boolean, modifier: Modifier = Modifier) {
    val c = Infill.colors
    val share = value / 255f
    val tone = when {
        value < 8 -> Tone.Plain
        highIsBad -> if (value >= 150) Tone.Bad else if (value >= 70) Tone.Warn else Tone.Good
        else -> if (value >= 150) Tone.Good else if (value >= 70) Tone.Warn else Tone.Bad
    }
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlyphIcon(glyph, c.textDim, Modifier.size(15.dp))
        Meter(share.coerceAtLeast(0.04f), if (tone == Tone.Plain) c.textDim.copy(alpha = 0.4f) else toneColour(tone), Modifier.width(30.dp), 4.dp)
    }
}

/** A heading over a part of a panel. */
@Composable
fun Section(title: String, glyph: Glyph? = null, content: @Composable () -> Unit) {
    val c = Infill.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            glyph?.let { GlyphIcon(it, c.accent, Modifier.size(15.dp)) }
            Text(title.uppercase(), color = c.textDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
        }
        content()
    }
}

/** A thin line between parts of a card. */
@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Infill.colors.chromeEdge))
}

/** Something to do from a panel. A [confirm] one asks for a second tap. */
class ActionItem(val glyph: Glyph, val text: String, val detail: String? = null, val confirm: Boolean = false, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
fun Actions(actions: List<ActionItem>) {
    if (actions.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (a in actions) ActionButton(a)
    }
}

@Composable
fun ActionButton(a: ActionItem) {
    val c = Infill.colors
    var asking by remember(a.text) { mutableStateOf(false) }
    val again = stringResource(Res.string.tap_again)
    val tint = if (a.enabled) c.text else c.textDim.copy(alpha = 0.5f)
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (asking) c.bad.copy(alpha = 0.25f) else c.button)
            .clickable(enabled = a.enabled, role = Role.Button) {
                if (a.confirm && !asking) asking = true
                else {
                    asking = false
                    a.onClick()
                }
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphIcon(a.glyph, tint, Modifier.size(16.dp))
        Text(if (asking) again else a.text, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        a.detail?.let { Text(it, color = c.textDim, fontSize = 12.sp) }
    }
}
