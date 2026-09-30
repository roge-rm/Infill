package com.rm.infill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.rm.infill.res.building_bank
import com.rm.infill.res.building_cottage
import com.rm.infill.res.building_factory
import com.rm.infill.res.building_general_store
import com.rm.infill.res.building_hotel
import com.rm.infill.res.building_house
import com.rm.infill.res.building_large_house
import com.rm.infill.res.building_mill
import com.rm.infill.res.building_shop
import com.rm.infill.res.building_tenement
import com.rm.infill.res.building_warehouse
import com.rm.infill.res.building_workshop
import com.rm.infill.res.close
import com.rm.infill.res.fire_station
import com.rm.infill.res.inspect_crime
import com.rm.infill.res.inspect_land_value
import com.rm.infill.res.inspect_pollution
import com.rm.infill.res.level_high
import com.rm.infill.res.level_low
import com.rm.infill.res.level_medium
import com.rm.infill.res.level_none
import com.rm.infill.res.on_fire
import com.rm.infill.res.park
import com.rm.infill.res.police_station
import com.rm.infill.res.coal_plant
import com.rm.infill.res.has_power
import com.rm.infill.res.inspect_power_line
import com.rm.infill.res.jobs
import com.rm.infill.res.no_power
import com.rm.infill.res.residents
import com.rm.infill.res.inspect_bridge
import com.rm.infill.res.inspect_commute
import com.rm.infill.res.inspect_no_commute
import com.rm.infill.res.inspect_traffic
import com.rm.infill.res.inspect_grass
import com.rm.infill.res.inspect_tile
import com.rm.infill.res.inspect_trees
import com.rm.infill.res.inspect_water
import com.rm.infill.res.inspect_zone_commercial
import com.rm.infill.res.inspect_zone_industrial
import com.rm.infill.res.inspect_zone_residential
import com.rm.infill.sim.BuildingType
import com.rm.infill.sim.Power
import com.rm.infill.sim.RoadType
import com.rm.infill.sim.Terrain
import com.rm.infill.sim.Zone
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The choices a tool has, like the kinds of zone, shown above the toolbar while it's picked. */
@Composable
fun <T> OptionPicker(
    options: List<T>,
    selected: T,
    title: (T) -> StringResource,
    dot: (T) -> Color?,
    onSelect: (T) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Infill.colors
    ChromeBox(modifier) {
        // Scrolls sideways when the choices are wider than the screen.
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (option in options) {
                val on = option == selected
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) c.accent else c.button)
                        .semantics(mergeDescendants = true) { this.selected = on }
                        .clickable(role = Role.Tab) { onSelect(option) }
                        .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    dot(option)?.let { Box(Modifier.size(10.dp).clip(CircleShape).background(it)) }
                    Text(
                        stringResource(title(option)), color = if (on) c.onAccent else c.text,
                        fontSize = if (compact) 12.sp else 13.sp, maxLines = 1, softWrap = false,
                    )
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
    val city = game.city
    val map = city.map
    val building = city.buildingAt(x, y)
    val i = map.index(x, y)
    val lines = buildList {
        if (building != null) {
            val t = building.type
            add(stringResource(buildingName(t)))
            when (t.zone) {
                Zone.RESIDENTIAL -> add(pluralStringResource(Res.plurals.residents, t.capacity, t.capacity))
                else -> add(pluralStringResource(Res.plurals.jobs, t.capacity, t.capacity))
            }
            if (t.needsPower || t == BuildingType.COAL_PLANT) {
                add(stringResource(if (map.powered[i]) Res.string.has_power else Res.string.no_power))
            }
            if (building.burning > 0) add(stringResource(Res.string.on_fire))
            when (val commute = map.commute[i].toInt() and 0xff) {
                0 -> {}
                255 -> add(stringResource(Res.string.inspect_no_commute))
                // Half minutes, from one up.
                else -> add(stringResource(Res.string.inspect_commute, maxOf(1, (commute - 1) / 2)))
            }
        } else {
            val road = RoadType.of(map.road[i])
            add(
                stringResource(
                    when {
                        road != null -> roadName(road)
                        map.power[i] != Power.NONE -> Res.string.inspect_power_line
                        map.terrain[i] == Terrain.WATER -> Res.string.inspect_water
                        map.terrain[i] == Terrain.TREES -> Res.string.inspect_trees
                        else -> Res.string.inspect_grass
                    },
                ),
            )
            if (road != null) {
                if (map.terrain[i] == Terrain.WATER) add(stringResource(Res.string.inspect_bridge))
                add(stringResource(Res.string.inspect_traffic, level(map.congestion[i].toInt() and 0xff)))
            }
            when (map.zone[i]) {
                Zone.RESIDENTIAL -> add(stringResource(Res.string.inspect_zone_residential))
                Zone.COMMERCIAL -> add(stringResource(Res.string.inspect_zone_commercial))
                Zone.INDUSTRIAL -> add(stringResource(Res.string.inspect_zone_industrial))
            }
        }
    }
    val value = level(map.landValue[i].toInt() and 0xff)
    val crime = level(map.crime[i].toInt() and 0xff)
    val pollution = level(map.pollution[i].toInt() and 0xff)
    val details = if (map.terrain[i] == Terrain.WATER) emptyList() else listOf(
        stringResource(Res.string.inspect_land_value, value),
        stringResource(Res.string.inspect_crime, crime),
        stringResource(Res.string.inspect_pollution, pollution),
    )
    val close = stringResource(Res.string.close)
    ChromeBox(modifier.widthIn(min = 200.dp, max = 360.dp)) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(lines.first(), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                for (line in lines.drop(1)) Text(line, color = c.text, fontSize = 14.sp)
                for (line in details) Text(line, color = c.textDim, fontSize = 13.sp)
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

fun buildingName(t: BuildingType): StringResource = when (t) {
    BuildingType.COTTAGE -> Res.string.building_cottage
    BuildingType.HOUSE -> Res.string.building_house
    BuildingType.LARGE_HOUSE -> Res.string.building_large_house
    BuildingType.TENEMENT -> Res.string.building_tenement
    BuildingType.GENERAL_STORE -> Res.string.building_general_store
    BuildingType.SHOP -> Res.string.building_shop
    BuildingType.BANK -> Res.string.building_bank
    BuildingType.HOTEL -> Res.string.building_hotel
    BuildingType.WORKSHOP -> Res.string.building_workshop
    BuildingType.MILL -> Res.string.building_mill
    BuildingType.WAREHOUSE -> Res.string.building_warehouse
    BuildingType.FACTORY -> Res.string.building_factory
    BuildingType.COAL_PLANT -> Res.string.coal_plant
    BuildingType.POLICE_STATION -> Res.string.police_station
    BuildingType.FIRE_STATION -> Res.string.fire_station
    BuildingType.PARK -> Res.string.park
}

/** A short message that goes away by itself. If it's about a place, tapping it goes there. */
@Composable
fun MessageChip(text: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = Infill.colors
    ChromeBox(modifier) {
        Text(
            text, color = c.text, fontSize = 14.sp,
            modifier = (if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** A 0 to 255 level as a word. */
@Composable
private fun level(v: Int): String = stringResource(
    when {
        v < 8 -> Res.string.level_none
        v < 70 -> Res.string.level_low
        v < 150 -> Res.string.level_medium
        else -> Res.string.level_high
    },
)

fun zoneColour(zone: Byte): Color = Color(0xFF000000.toInt() or MapRenderer.ZONE_COLOURS[zone.toInt()])
