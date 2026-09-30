package com.rm.infill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.GameState
import com.rm.infill.map.MapRenderer
import com.rm.infill.res.Res
import com.rm.infill.res.close
import com.rm.infill.res.inspect_dirt_road
import com.rm.infill.res.inspect_grass
import com.rm.infill.res.inspect_tile
import com.rm.infill.res.inspect_trees
import com.rm.infill.res.inspect_water
import com.rm.infill.res.inspect_zone_commercial
import com.rm.infill.res.inspect_zone_industrial
import com.rm.infill.res.inspect_zone_residential
import com.rm.infill.sim.Road
import com.rm.infill.sim.Terrain
import com.rm.infill.sim.Zone
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.stringResource

/** The three kinds of zone, shown while the zone tool is picked. */
@Composable
fun ZonePicker(selected: ZoneKind, onSelect: (ZoneKind) -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    val c = Infill.colors
    ChromeBox(modifier) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (kind in ZoneKind.entries) {
                val on = kind == selected
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) c.accent else c.button)
                        .semantics(mergeDescendants = true) { this.selected = on }
                        .clickable(role = Role.Tab) { onSelect(kind) }
                        .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(zoneColour(kind.zone)))
                    Text(stringResource(kind.title), color = if (on) c.onAccent else c.text, fontSize = if (compact) 12.sp else 13.sp)
                }
            }
        }
    }
}

/** What's on a tile. */
@Composable
fun InspectPanel(game: GameState, x: Int, y: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = Infill.colors
    game.revision
    val map = game.city.map
    val lines = buildList {
        add(
            stringResource(
                when {
                    map.roadAt(x, y) == Road.DIRT -> Res.string.inspect_dirt_road
                    map.terrainAt(x, y) == Terrain.WATER -> Res.string.inspect_water
                    map.terrainAt(x, y) == Terrain.TREES -> Res.string.inspect_trees
                    else -> Res.string.inspect_grass
                },
            ),
        )
        when (map.zoneAt(x, y)) {
            Zone.RESIDENTIAL -> add(stringResource(Res.string.inspect_zone_residential))
            Zone.COMMERCIAL -> add(stringResource(Res.string.inspect_zone_commercial))
            Zone.INDUSTRIAL -> add(stringResource(Res.string.inspect_zone_industrial))
        }
    }
    val close = stringResource(Res.string.close)
    ChromeBox(modifier.widthIn(min = 200.dp, max = 360.dp)) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(lines.first(), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                for (line in lines.drop(1)) Text(line, color = c.text, fontSize = 14.sp)
                Text(stringResource(Res.string.inspect_tile, x, y), color = c.textDim, fontSize = 12.sp)
            }
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { contentDescription = close }
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text("×", color = c.textDim, fontSize = 20.sp) }
        }
    }
}

/** A short message that goes away by itself. */
@Composable
fun MessageChip(text: String, modifier: Modifier = Modifier) {
    val c = Infill.colors
    ChromeBox(modifier) {
        Text(text, color = c.text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

fun zoneColour(zone: Byte): Color = Color(0xFF000000.toInt() or MapRenderer.ZONE_COLOURS[zone.toInt()])
