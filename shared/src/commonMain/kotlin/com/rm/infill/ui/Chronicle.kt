package com.rm.infill.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.GameState
import com.rm.infill.map.imageBitmapOf
import com.rm.infill.messageOf
import com.rm.infill.messageText
import com.rm.infill.res.Res
import com.rm.infill.res.chronicle
import com.rm.infill.res.chronicle_all
import com.rm.infill.res.chronicle_empty
import com.rm.infill.res.chronicle_headlines
import com.rm.infill.res.date
import com.rm.infill.res.month_short
import com.rm.infill.res.paper_feed
import com.rm.infill.res.paper_gazette
import com.rm.infill.res.paper_star
import com.rm.infill.sim.Era
import com.rm.infill.sim.EventKind
import com.rm.infill.sim.Snapshot
import com.rm.infill.sim.Story
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** The news that makes the headlines; the rest is only in the full chronicle. */
private val HEADLINES = setOf(
    EventKind.EraArrived, EventKind.FirstBuilt, EventKind.Milestone, EventKind.OrdinanceEnded, EventKind.OrdinanceAvailable,
    EventKind.OverseerIn, EventKind.OverseerOut, EventKind.RatingDown, EventKind.RatingUp, EventKind.BuildingLost,
    EventKind.RiverFlood, EventKind.Smog, EventKind.Blizzard, EventKind.HeatWave, EventKind.IndustrialAccident,
    EventKind.NuclearAccident, EventKind.Earthquake, EventKind.Epidemic, EventKind.EpidemicOver,
    EventKind.MedicalAdvance, EventKind.Drought, EventKind.StormSurge, EventKind.WorkedOut,
    EventKind.Protest, EventKind.GrantOffered, EventKind.GrantPaid, EventKind.ElectionWon, EventKind.ElectionLost,
)

/** The most months the full chronicle shows at once, newest first. */
private const val MONTHS_SHOWN = 120

/**
 * The town's story, newest first, a month at a time under the masthead of
 * the paper of its day: a broadsheet gazette, then a tabloid, then a feed.
 * A story with a place goes there.
 */
@Composable
fun ChronicleWindow(game: GameState, onGo: (Int, Int) -> Unit, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    var everything by remember { mutableStateOf(false) }
    val months = stringArrayResource(Res.array.month_short)
    Window(Res.string.chronicle, onClose, Glyph.Calendar, help = "the-towns-story") {
        Chips(listOf(false, true), everything, { stringResource(if (it) Res.string.chronicle_all else Res.string.chronicle_headlines) }) { everything = it }
        // Each story with the era it happened in.
        val told = remember(city.chronicle.size, everything) {
            var era = Era.TOWNSHIP
            val out = ArrayList<Pair<Story, Era>>()
            // Copied under the town's lock, since the sim adds to it on its own thread.
            for (t in game.locked { city.chronicle.toList() }) {
                if (t.event.kind == EventKind.EraArrived) t.event.era?.let { era = it }
                if (everything || t.event.kind in HEADLINES) out += t to era
            }
            out.groupBy { (t, _) -> t.year * 12 + t.month }.entries.sortedByDescending { it.key }.take(MONTHS_SHOWN)
        }
        if (told.isEmpty()) Text(stringResource(Res.string.chronicle_empty), color = c.textDim, fontSize = 14.sp)
        for ((when_, stories) in told) {
            val era = stories.last().second
            val year = when_ / 12
            val month = when_ % 12
            val paper = when {
                era <= Era.STREETCAR -> Res.string.paper_gazette
                era <= Era.RENEWAL -> Res.string.paper_star
                else -> Res.string.paper_feed
            }
            val family = if (era <= Era.STREETCAR) FontFamily.Serif else FontFamily.SansSerif
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(paper, city.name).let { if (era in Era.MOTOR..Era.RENEWAL) it.uppercase() else it },
                        color = c.text, fontSize = 13.sp, fontFamily = family,
                        fontWeight = if (era <= Era.STREETCAR) FontWeight.Normal else FontWeight.Black,
                        fontStyle = if (era <= Era.STREETCAR) FontStyle.Italic else FontStyle.Normal,
                    )
                    Text(stringResource(Res.string.date, months.getOrElse(month) { "" }, year), color = c.textDim, fontSize = 12.sp)
                }
                HorizontalDivider(color = c.textDim.copy(alpha = 0.4f))
                for ((t, _) in stories.asReversed()) {
                    val m = messageOf(t.event) ?: continue
                    val text = messageText(m)
                    Text(
                        when {
                            era <= Era.STREETCAR -> text
                            era <= Era.RENEWAL -> text.uppercase()
                            else -> "• $text"
                        },
                        color = c.text, fontFamily = family,
                        fontSize = if (era in Era.MOTOR..Era.RENEWAL) 13.sp else 14.sp,
                        fontWeight = if (era in Era.MOTOR..Era.RENEWAL) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (m.x >= 0) Modifier.clickable(role = Role.Button) { onGo(m.x, m.y) } else Modifier)
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** The colour of each [Snapshot] kind in a town's picture. */
private val SNAPSHOT_COLOURS = intArrayOf(
    0xFF9AB87A.toInt(), 0xFF4A7AA8.toInt(), 0xFF4E7A44.toInt(), 0xFF6E6A66.toInt(), 0xFF5A4A6A.toInt(),
    0xFF5FB04A.toInt(), 0xFF3F7FD8.toInt(), 0xFFE0B030.toInt(), 0xFF2FA8A0.toInt(), 0xFFC8A060.toInt(),
    0xFFE8E4DA.toInt(), 0xFF2E8A3A.toInt(),
)

/** A small picture of a town, a pixel a tile, from a [Snapshot]'s tiles. */
@Composable
fun TownPicture(tiles: ByteArray, width: Int, modifier: Modifier = Modifier) {
    val height = tiles.size / width
    val image = remember(tiles) {
        imageBitmapOf(IntArray(tiles.size) { SNAPSHOT_COLOURS.getOrElse(tiles[it].toInt()) { SNAPSHOT_COLOURS[0] } }, width, height)
    }
    Image(
        image, null, filterQuality = FilterQuality.None,
        modifier = modifier.aspectRatio(width / height.toFloat()).clip(RoundedCornerShape(6.dp)),
    )
}
