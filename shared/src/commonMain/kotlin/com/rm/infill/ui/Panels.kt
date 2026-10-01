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
import com.rm.infill.res.places
import com.rm.infill.res.cares_for
import com.rm.infill.res.for_sale
import com.rm.infill.res.for_sale_months
import com.rm.infill.res.inspect_ages
import com.rm.infill.res.inspect_health
import com.rm.infill.res.inspect_schooling
import com.rm.infill.res.inspect_adults_schooled
import com.rm.infill.res.wealth_poor
import com.rm.infill.res.wealth_middle
import com.rm.infill.res.wealth_well_off
import com.rm.infill.res.health_poor
import com.rm.infill.res.health_fair
import com.rm.infill.res.health_good
import com.rm.infill.res.school
import com.rm.infill.res.high_school
import com.rm.infill.res.clinic
import com.rm.infill.res.hospital
import com.rm.infill.sim.Education
import com.rm.infill.sim.Broken
import com.rm.infill.sim.Material
import com.rm.infill.sim.Pipe
import com.rm.infill.res.track
import com.rm.infill.res.brownfield
import com.rm.infill.res.heritage
import com.rm.infill.res.sewage_works
import com.rm.infill.res.treatment_plant
import com.rm.infill.res.laid_in
import com.rm.infill.res.laid
import com.rm.infill.res.built_in
import com.rm.infill.res.broken_down
import com.rm.infill.res.works_on
import com.rm.infill.res.works_waiting
import com.rm.infill.res.mend_main
import com.rm.infill.res.mend_sewer
import com.rm.infill.res.mend_drain
import com.rm.infill.res.mend_road
import com.rm.infill.res.mend_track
import com.rm.infill.res.material_cast_iron
import com.rm.infill.res.material_wood
import com.rm.infill.res.material_ductile_iron
import com.rm.infill.res.material_plastic_main
import com.rm.infill.res.material_brick
import com.rm.infill.res.material_concrete_sewer
import com.rm.infill.res.material_plastic_sewer
import com.rm.infill.res.material_clay_drain
import com.rm.infill.res.material_concrete_drain
import com.rm.infill.res.material_plastic_drain
import com.rm.infill.sim.Density
import com.rm.infill.res.density_low
import com.rm.infill.res.density_medium
import com.rm.infill.res.density_high
import com.rm.infill.res.going_up
import com.rm.infill.res.building_row_houses
import com.rm.infill.res.building_apartments
import com.rm.infill.res.building_apartment_court
import com.rm.infill.res.building_main_street
import com.rm.infill.res.building_office_block
import com.rm.infill.res.building_department_store
import com.rm.infill.res.building_works
import com.rm.infill.sim.Wealth
import com.rm.infill.res.inspect_bridge
import com.rm.infill.res.inspect_crossing
import com.rm.infill.res.inspect_linked
import com.rm.infill.res.inspect_not_linked
import com.rm.infill.res.inspect_no_road
import com.rm.infill.res.inspect_mains
import com.rm.infill.res.inspect_well
import com.rm.infill.res.inspect_sewer
import com.rm.infill.res.inspect_septic
import com.rm.infill.res.inspect_flooded
import com.rm.infill.res.inspect_foul
import com.rm.infill.res.inspect_shut
import com.rm.infill.res.inspect_flooded_before
import com.rm.infill.res.embankment
import com.rm.infill.res.event_river_flood
import com.rm.infill.res.inspect_river_high
import com.rm.infill.res.pumping_station
import com.rm.infill.res.well_field
import com.rm.infill.res.water_tower
import com.rm.infill.res.sewer_outfall
import com.rm.infill.res.storm_pond
import com.rm.infill.res.storm_outfall
import com.rm.infill.res.riders
import com.rm.infill.res.loads_by_train
import com.rm.infill.res.station
import com.rm.infill.res.freight_yard
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
import com.rm.infill.sim.Balance
import com.rm.infill.sim.Rail
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
            val h = building.people
            when {
                building.underway > 0 -> add(pluralStringResource(Res.plurals.going_up, building.underway, building.underway))
                h != null && h.empty -> add(
                    if (h.forSale == 0) stringResource(Res.string.for_sale)
                    else pluralStringResource(Res.plurals.for_sale_months, h.forSale, h.forSale),
                )
                h != null -> {
                    add(stringResource(wealthName(h.wealth)) + ", " + pluralStringResource(Res.plurals.residents, h.size, h.size).replaceFirstChar { it.lowercase() })
                    add(stringResource(Res.string.inspect_ages, h.children, h.adults, h.elderly))
                    add(stringResource(Res.string.inspect_health, healthWord(h.health)))
                    if (h.children > 0) add(stringResource(Res.string.inspect_schooling, level(h.schooling * 255 / 100)))
                    if (h.adults > 0) {
                        val schooled = (h.schooled[Education.SCHOOLED] + h.schooled[Education.EDUCATED]) * 100 / h.adults
                        add(stringResource(Res.string.inspect_adults_schooled, schooled))
                    }
                }
                t.school -> {
                    add(pluralStringResource(Res.plurals.jobs, t.capacity, t.capacity))
                    val n = (if (t == BuildingType.SCHOOL) Balance.SCHOOL_PLACES else Balance.HIGH_SCHOOL_PLACES) * city.schoolFunding / 100
                    add(pluralStringResource(Res.plurals.places, n, n))
                }
                t.health -> {
                    add(pluralStringResource(Res.plurals.jobs, t.capacity, t.capacity))
                    val n = (if (t == BuildingType.CLINIC) Balance.CLINIC_CARES else Balance.HOSPITAL_CARES) * city.healthFunding / 100
                    add(pluralStringResource(Res.plurals.cares_for, n, n))
                }
                else -> add(pluralStringResource(Res.plurals.jobs, t.capacity, t.capacity))
            }
            if (t.zone != Zone.NONE) densityName(map.density[i])?.let { add(stringResource(it)) }
            if (t.needsPower || t == BuildingType.COAL_PLANT) {
                add(stringResource(if (map.powered[i]) Res.string.has_power else Res.string.no_power))
            }
            if (building.burning > 0) add(stringResource(Res.string.on_fire))
            if (building.outage > 0) add(pluralStringResource(Res.plurals.broken_down, building.outage, building.outage))
            if (building.underway == 0 && t != BuildingType.PARK) add(stringResource(Res.string.built_in, yearOf(building.built)))
            if (city.isHeritage(building)) add(stringResource(Res.string.heritage))
            if (t.zone != Zone.NONE) {
                add(stringResource(if (map.watered[i]) Res.string.inspect_mains else Res.string.inspect_well))
                add(stringResource(if (map.sewered[i]) Res.string.inspect_sewer else Res.string.inspect_septic))
            }
            if ((map.flood[i].toInt() and 0xff) >= Balance.FLOODED) {
                add(stringResource(if (t.zone == Zone.COMMERCIAL || t.zone == Zone.INDUSTRIAL) Res.string.inspect_shut else Res.string.inspect_flooded))
            } else if ((map.floodMemory[i].toInt() and 0xff) >= FLOODED_BEFORE) {
                add(stringResource(Res.string.inspect_flooded_before))
            }
            if (t.railway) {
                if (t.station) add(pluralStringResource(Res.plurals.riders, city.riders(building), city.riders(building)))
                else add(pluralStringResource(Res.plurals.loads_by_train, city.freightSent(building), city.freightSent(building)))
                add(stringResource(if (city.railLinked(building)) Res.string.inspect_linked else Res.string.inspect_not_linked))
                if (!city.reachable(building)) add(stringResource(Res.string.inspect_no_road))
            }
            // No one commutes from an empty home or a site.
            when (val commute = if (building.underway > 0 || building.people?.empty == true) 0 else map.commute[i].toInt() and 0xff) {
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
                        map.bank[i].toInt() != 0 -> Res.string.embankment
                        road != null && map.rail[i] != Rail.NONE -> Res.string.inspect_crossing
                        road != null -> roadName(road)
                        map.rail[i] != Rail.NONE -> Res.string.track
                        map.power[i] != Power.NONE -> Res.string.inspect_power_line
                        map.terrain[i] == Terrain.WATER -> Res.string.inspect_water
                        map.terrain[i] == Terrain.TREES -> Res.string.inspect_trees
                        else -> Res.string.inspect_grass
                    },
                ),
            )
            if (map.rail[i] != Rail.NONE && map.terrain[i] == Terrain.WATER) add(stringResource(Res.string.inspect_bridge))
            if (map.terrain[i] == Terrain.WATER && map.foulLevel(i) > 0) add(stringResource(Res.string.inspect_foul))
            if (map.terrain[i] == Terrain.WATER) {
                when {
                    city.river > Balance.BANKFULL -> add(stringResource(Res.string.event_river_flood))
                    city.river > Balance.BANKFULL - 20 -> add(stringResource(Res.string.inspect_river_high))
                }
            }
            if ((map.flood[i].toInt() and 0xff) >= Balance.FLOODED) add(stringResource(Res.string.inspect_flooded))
            else if ((map.floodMemory[i].toInt() and 0xff) >= FLOODED_BEFORE) add(stringResource(Res.string.inspect_flooded_before))
            // When the road and track were laid, and what's underneath and when.
            if (road != null && map.bank[i].toInt() == 0) {
                // The road's name is the title already, unless it's a crossing.
                if (map.rail[i] == Rail.NONE) add(stringResource(Res.string.laid, yearOf(map.roadLaid[i].toInt())))
                else add(stringResource(Res.string.laid_in, stringResource(roadName(road)), yearOf(map.roadLaid[i].toInt())))
            }
            if (map.rail[i] != Rail.NONE) add(stringResource(Res.string.laid_in, stringResource(Res.string.track), yearOf(map.railLaid[i].toInt())))
            for ((kind, layer, laid) in listOf(Triple(Pipe.WATER, map.waterPipe, map.waterLaid), Triple(Pipe.SEWER, map.sewerPipe, map.sewerLaid), Triple(Pipe.STORM, map.stormPipe, map.stormLaid))) {
                val material = Material.of(kind, layer[i]) ?: continue
                add(stringResource(Res.string.laid_in, stringResource(materialName(material)), yearOf(laid[i].toInt())))
            }
            brokenLine(map, i)?.let { add(it) }
            if (map.brownfield[i].toInt() != 0) add(stringResource(Res.string.brownfield))
            if (road != null) {
                if (map.terrain[i] == Terrain.WATER) add(stringResource(Res.string.inspect_bridge))
                add(stringResource(Res.string.inspect_traffic, level(map.congestion[i].toInt() and 0xff)))
            }
            when (map.zone[i]) {
                Zone.RESIDENTIAL -> add(stringResource(Res.string.inspect_zone_residential))
                Zone.COMMERCIAL -> add(stringResource(Res.string.inspect_zone_commercial))
                Zone.INDUSTRIAL -> add(stringResource(Res.string.inspect_zone_industrial))
            }
            densityName(map.density[i])?.let { add(stringResource(it)) }
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
    BuildingType.STATION, BuildingType.STATION_NS -> Res.string.station
    BuildingType.FREIGHT_YARD, BuildingType.FREIGHT_YARD_NS -> Res.string.freight_yard
    BuildingType.PUMPING_STATION -> Res.string.pumping_station
    BuildingType.WELL_FIELD -> Res.string.well_field
    BuildingType.WATER_TOWER -> Res.string.water_tower
    BuildingType.OUTFALL -> Res.string.sewer_outfall
    BuildingType.SEWAGE_WORKS -> Res.string.sewage_works
    BuildingType.TREATMENT_PLANT -> Res.string.treatment_plant
    BuildingType.STORM_POND -> Res.string.storm_pond
    BuildingType.STORM_OUTFALL -> Res.string.storm_outfall
    BuildingType.SCHOOL -> Res.string.school
    BuildingType.HIGH_SCHOOL -> Res.string.high_school
    BuildingType.CLINIC -> Res.string.clinic
    BuildingType.HOSPITAL -> Res.string.hospital
    BuildingType.ROW_HOUSES -> Res.string.building_row_houses
    BuildingType.APARTMENTS -> Res.string.building_apartments
    BuildingType.APARTMENT_COURT -> Res.string.building_apartment_court
    BuildingType.MAIN_STREET -> Res.string.building_main_street
    BuildingType.OFFICE_BLOCK -> Res.string.building_office_block
    BuildingType.DEPARTMENT_STORE -> Res.string.building_department_store
    BuildingType.WORKS -> Res.string.building_works
}

fun densityName(density: Byte): StringResource? = when (density) {
    Density.LOW -> Res.string.density_low
    Density.MEDIUM -> Res.string.density_medium
    Density.HIGH -> Res.string.density_high
    else -> null
}

/** The year of a month counted from January 1900. */
private fun yearOf(month: Int): Int = 1900 + month / 12

fun materialName(m: Material): StringResource = when (m) {
    Material.CAST_IRON -> Res.string.material_cast_iron
    Material.WOOD -> Res.string.material_wood
    Material.DUCTILE_IRON -> Res.string.material_ductile_iron
    Material.PLASTIC_MAIN -> Res.string.material_plastic_main
    Material.BRICK -> Res.string.material_brick
    Material.CONCRETE_SEWER -> Res.string.material_concrete_sewer
    Material.PLASTIC_SEWER -> Res.string.material_plastic_sewer
    Material.CLAY_DRAIN -> Res.string.material_clay_drain
    Material.CONCRETE_DRAIN -> Res.string.material_concrete_drain
    Material.PLASTIC_DRAIN -> Res.string.material_plastic_drain
}

/** What's broken on a tile, or the works there, and how long it'll be. */
@Composable
private fun brokenLine(map: com.rm.infill.sim.CityMap, i: Int): String? {
    val bits = map.broken[i].toInt()
    if (bits == 0) return null
    val days = map.mendingDays(i)
    if (bits and Broken.WORKS != 0) {
        return if (map.underRepair(i)) pluralStringResource(Res.plurals.works_on, days, days) else stringResource(Res.string.works_waiting)
    }
    val which = when {
        bits and Broken.WATER != 0 -> Res.plurals.mend_main
        bits and Broken.SEWER != 0 -> Res.plurals.mend_sewer
        bits and Broken.STORM != 0 -> Res.plurals.mend_drain
        bits and Broken.RAIL != 0 -> Res.plurals.mend_track
        else -> Res.plurals.mend_road
    }
    return pluralStringResource(which, days, days)
}

fun wealthName(wealth: Int): StringResource = when (wealth) {
    Wealth.POOR -> Res.string.wealth_poor
    Wealth.WELL_OFF -> Res.string.wealth_well_off
    else -> Res.string.wealth_middle
}

/** Health, 0 to 100, as a word. */
@Composable
fun healthWord(health: Int): String = stringResource(
    when {
        health < 45 -> Res.string.health_poor
        health < 65 -> Res.string.health_fair
        else -> Res.string.health_good
    },
)

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

/** How much a tile has to remember of a flood for inspect to mention it. */
private const val FLOODED_BEFORE = 32
