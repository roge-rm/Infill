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
import com.rm.infill.res.inspect_district
import com.rm.infill.res.inspect_scrubbed
import com.rm.infill.res.inspect_staffed
import com.rm.infill.res.inspect_worn
import com.rm.infill.res.inspect_renovatable
import com.rm.infill.res.renovating
import com.rm.infill.res.volunteer_hall
import com.rm.infill.res.ladder_company
import com.rm.infill.res.ambulance_station
import com.rm.infill.res.nursing_home
import com.rm.infill.res.library
import com.rm.infill.res.college
import com.rm.infill.res.police_hq
import com.rm.infill.res.exchange
import com.rm.infill.res.cell_tower
import com.rm.infill.res.courthouse
import com.rm.infill.res.jail
import com.rm.infill.res.inspect_cases
import com.rm.infill.res.inspect_held
import com.rm.infill.res.inspect_arrests
import com.rm.infill.res.inspect_theft
import com.rm.infill.res.inspect_vice
import com.rm.infill.res.inspect_rackets
import com.rm.infill.res.inspect_crowded
import com.rm.infill.res.inspect_taking
import com.rm.infill.res.inspect_line_load
import com.rm.infill.res.inspect_line_overloaded
import com.rm.infill.res.inspect_lines
import com.rm.infill.res.inspect_bus_lane
import com.rm.infill.res.line_name
import com.rm.infill.res.tram_line
import com.rm.infill.res.bus_line
import com.rm.infill.res.inspect_junction
import com.rm.infill.res.building_offices
import com.rm.infill.res.building_office_building
import com.rm.infill.res.building_office_tower
import com.rm.infill.res.building_glass_tower
import com.rm.infill.res.inspect_zone_office
import com.rm.infill.res.building_oil_well
import com.rm.infill.res.good_oil
import com.rm.infill.res.good_fuel
import com.rm.infill.res.inspect_oil
import com.rm.infill.res.inspect_stock_local
import com.rm.infill.res.inspect_short_of_stock
import com.rm.infill.res.inspect_fuel_local
import com.rm.infill.res.building_farm
import com.rm.infill.res.building_woodlot
import com.rm.infill.res.building_mine
import com.rm.infill.res.building_colliery
import com.rm.infill.res.good_food
import com.rm.infill.res.good_timber
import com.rm.infill.res.good_ore
import com.rm.infill.res.good_coal
import com.rm.infill.res.good_lumber
import com.rm.infill.res.good_metal
import com.rm.infill.res.good_goods
import com.rm.infill.res.and_also
import com.rm.infill.res.inspect_makes
import com.rm.infill.res.inspect_makes_from
import com.rm.infill.res.inspect_local_needs
import com.rm.infill.res.inspect_local_sold
import com.rm.infill.res.inspect_coal_local
import com.rm.infill.res.inspect_zone_farmland
import com.rm.infill.res.inspect_fertile
import com.rm.infill.res.inspect_ore
import com.rm.infill.res.inspect_coal_seam
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
import com.rm.infill.res.mend_power
import com.rm.infill.sim.Generation
import com.rm.infill.res.oil_plant
import com.rm.infill.res.gas_plant
import com.rm.infill.res.hydro_plant
import com.rm.infill.res.nuclear_plant
import com.rm.infill.res.substation
import com.rm.infill.res.high_line
import com.rm.infill.res.dump
import com.rm.infill.res.incinerator
import com.rm.infill.res.recycling
import com.rm.infill.res.street_trees
import com.rm.infill.res.station_output
import com.rm.infill.res.station_drawing
import com.rm.infill.res.megawatts
import com.rm.infill.res.inspect_garbage
import com.rm.infill.res.dump_fill
import com.rm.infill.res.snowed_in
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
import com.rm.infill.res.tram_track
import com.rm.infill.res.tram_stop
import com.rm.infill.res.bus_stop
import com.rm.infill.res.subway
import com.rm.infill.res.trolley_wire
import com.rm.infill.res.tram_depot
import com.rm.infill.res.bus_garage
import com.rm.infill.res.subway_station
import com.rm.infill.res.no_service
import com.rm.infill.res.stop_riders
import com.rm.infill.sim.Stop
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
import com.rm.infill.res.mend_tram
import com.rm.infill.res.mend_wire
import com.rm.infill.res.mend_tunnel
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
import com.rm.infill.sim.Ageing
import com.rm.infill.sim.Junction
import com.rm.infill.sim.Resource
import com.rm.infill.sim.Land
import com.rm.infill.sim.Good
import com.rm.infill.sim.Rail
import com.rm.infill.sim.RoadType
import com.rm.infill.sim.Terrain
import com.rm.infill.sim.Zone
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
    BuildingType.TRAM_DEPOT -> Res.string.tram_depot
    BuildingType.BUS_GARAGE -> Res.string.bus_garage
    BuildingType.SUBWAY_STATION -> Res.string.subway_station
    BuildingType.TREATMENT_PLANT -> Res.string.treatment_plant
    BuildingType.STORM_POND -> Res.string.storm_pond
    BuildingType.STORM_OUTFALL -> Res.string.storm_outfall
    BuildingType.SCHOOL -> Res.string.school
    BuildingType.HIGH_SCHOOL -> Res.string.high_school
    BuildingType.CLINIC -> Res.string.clinic
    BuildingType.HOSPITAL -> Res.string.hospital
    BuildingType.VOLUNTEER_HALL -> Res.string.volunteer_hall
    BuildingType.LADDER_COMPANY -> Res.string.ladder_company
    BuildingType.AMBULANCE_STATION -> Res.string.ambulance_station
    BuildingType.NURSING_HOME -> Res.string.nursing_home
    BuildingType.LIBRARY -> Res.string.library
    BuildingType.COLLEGE -> Res.string.college
    BuildingType.POLICE_HQ -> Res.string.police_hq
    BuildingType.EXCHANGE -> Res.string.exchange
    BuildingType.CELL_TOWER -> Res.string.cell_tower
    BuildingType.COURTHOUSE -> Res.string.courthouse
    BuildingType.JAIL -> Res.string.jail
    BuildingType.ROW_HOUSES -> Res.string.building_row_houses
    BuildingType.APARTMENTS -> Res.string.building_apartments
    BuildingType.APARTMENT_COURT -> Res.string.building_apartment_court
    BuildingType.MAIN_STREET -> Res.string.building_main_street
    BuildingType.OFFICE_BLOCK -> Res.string.building_office_block
    BuildingType.DEPARTMENT_STORE -> Res.string.building_department_store
    BuildingType.WORKS -> Res.string.building_works
    BuildingType.OIL_PLANT -> Res.string.oil_plant
    BuildingType.GAS_PLANT -> Res.string.gas_plant
    BuildingType.HYDRO_PLANT -> Res.string.hydro_plant
    BuildingType.NUCLEAR_PLANT -> Res.string.nuclear_plant
    BuildingType.SUBSTATION -> Res.string.substation
    BuildingType.DUMP -> Res.string.dump
    BuildingType.INCINERATOR -> Res.string.incinerator
    BuildingType.RECYCLING -> Res.string.recycling
    BuildingType.FARM -> Res.string.building_farm
    BuildingType.WOODLOT -> Res.string.building_woodlot
    BuildingType.MINE -> Res.string.building_mine
    BuildingType.COLLIERY -> Res.string.building_colliery
    BuildingType.OIL_WELL -> Res.string.building_oil_well
    BuildingType.OFFICES -> Res.string.building_offices
    BuildingType.OFFICE_BUILDING -> Res.string.building_office_building
    BuildingType.OFFICE_TOWER -> Res.string.building_office_tower
    BuildingType.GLASS_TOWER -> Res.string.building_glass_tower
}

fun goodName(g: Good): StringResource = when (g) {
    Good.FOOD -> Res.string.good_food
    Good.TIMBER -> Res.string.good_timber
    Good.ORE -> Res.string.good_ore
    Good.COAL -> Res.string.good_coal
    Good.OIL -> Res.string.good_oil
    Good.FUEL -> Res.string.good_fuel
    Good.LUMBER -> Res.string.good_lumber
    Good.METAL -> Res.string.good_metal
    Good.GOODS -> Res.string.good_goods
}

fun densityName(density: Byte): StringResource? = when (density) {
    Density.LOW -> Res.string.density_low
    Density.MEDIUM -> Res.string.density_medium
    Density.HIGH -> Res.string.density_high
    else -> null
}

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
internal fun level(v: Int): String = stringResource(
    when {
        v < 8 -> Res.string.level_none
        v < 70 -> Res.string.level_low
        v < 150 -> Res.string.level_medium
        else -> Res.string.level_high
    },
)

fun zoneColour(zone: Byte): Color = Color(0xFF000000.toInt() or MapRenderer.ZONE_COLOURS[zone.toInt()])
