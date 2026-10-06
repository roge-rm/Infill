package com.rm.infill.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.res.Res
import com.rm.infill.res.close
import com.rm.infill.res.guide_budget
import com.rm.infill.res.guide_road_island
import com.rm.infill.res.guide_done
import com.rm.infill.res.guide_first_homes
import com.rm.infill.res.guide_homes
import com.rm.infill.res.guide_play
import com.rm.infill.res.guide_power
import com.rm.infill.res.guide_road
import com.rm.infill.res.guide_step
import com.rm.infill.res.guide_work
import com.rm.infill.sim.City
import com.rm.infill.sim.Generation
import com.rm.infill.sim.Zone
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The steps of the guided first town, in order; each is done when [done] says the town shows it. */
enum class GuideStep(val text: StringResource) {
    ROAD(Res.string.guide_road),
    HOMES(Res.string.guide_homes),
    WORK(Res.string.guide_work),
    PLAY(Res.string.guide_play),
    FIRST_HOMES(Res.string.guide_first_homes),
    POWER(Res.string.guide_power),
    BUDGET(Res.string.guide_budget),
    DONE(Res.string.guide_done),
}

/** What the guide watches besides the town: whether time's running and whether the budget's been opened. */
class GuideSeen(val running: Boolean, val budgetSeen: Boolean)

/**
 * Whether [step] is done in [city]. Looks at the map and the buildings, so
 * it's asked under the town's lock.
 */
fun guideDone(step: GuideStep, city: City, seen: GuideSeen): Boolean {
    val m = city.map
    fun zoned(zone: Byte) = m.zone.count { it == zone }
    return when (step) {
        // On an island there's no edge to reach: a street of any length does.
        GuideStep.ROAD -> if (city.island) m.road.count { it.toInt() != 0 } >= ISLAND_STREET else city.hasWayIn()
        GuideStep.HOMES -> zoned(Zone.RESIDENTIAL) >= HOMES
        GuideStep.WORK -> zoned(Zone.COMMERCIAL) > 0 && zoned(Zone.INDUSTRIAL) > 0
        GuideStep.PLAY -> seen.running
        GuideStep.FIRST_HOMES -> city.stats.population >= FIRST_PEOPLE
        GuideStep.POWER -> city.allBuildings.any { Generation.station(it.type) && it.underway == 0 } && m.power.any { it.toInt() != 0 }
        GuideStep.BUDGET -> seen.budgetSeen
        GuideStep.DONE -> false
    }
}

/**
 * The guided first town's card, under the strip: the step it's on, out of
 * how many, and a button to put it away. The last step's card is closed
 * by the player. The first step says it differently on an [island].
 */
@Composable
fun GuideCard(step: GuideStep, onClose: () -> Unit, island: Boolean = false) {
    val c = Infill.colors
    ChromeBox(Modifier.widthIn(max = 520.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (step != GuideStep.DONE) {
                    Text(stringResource(Res.string.guide_step, step.ordinal + 1, GuideStep.DONE.ordinal), color = c.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(stringResource(if (step == GuideStep.ROAD && island) Res.string.guide_road_island else step.text), color = c.text, fontSize = 14.sp)
            }
            val close = stringResource(Res.string.close)
            GlyphIcon(
                Glyph.Remove, c.textDim,
                Modifier.size(36.dp).clickable(onClickLabel = close, role = Role.Button, onClick = onClose).padding(10.dp)
                    .semantics { contentDescription = close },
            )
        }
    }
}

/** How many tiles of homes the guide asks for, and how many people for the first homes. */
private const val HOMES = 6
private const val FIRST_PEOPLE = 30

/** Tiles of street that start an island town in the guide. */
private const val ISLAND_STREET = 10
