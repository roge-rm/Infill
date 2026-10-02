package com.rm.infill.ui

import com.rm.infill.res.value_cents
import com.rm.infill.res.toll_rate
import com.rm.infill.res.label_hotel_rooms
import com.rm.infill.res.label_visitors
import com.rm.infill.res.by_air
import com.rm.infill.res.by_sea
import com.rm.infill.res.by_rail
import com.rm.infill.res.by_road
import com.rm.infill.res.visitors
import com.rm.infill.res.upkeep_ports
import com.rm.infill.res.income_dues
import com.rm.infill.res.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow

import com.rm.infill.sim.Density
import org.jetbrains.compose.resources.pluralStringResource
import com.rm.infill.sim.Action
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.GameState
import com.rm.infill.res.free_fares
import com.rm.infill.res.no_trucks
import com.rm.infill.res.clean_works
import com.rm.infill.res.rent_control
import com.rm.infill.res.districts
import com.rm.infill.res.no_districts
import com.rm.infill.res.district_figures
import com.rm.infill.res.district_value
import com.rm.infill.res.district_taxes
import com.rm.infill.res.tax_homes
import com.rm.infill.res.tax_shops
import com.rm.infill.res.tax_works
import com.rm.infill.res.height_limit
import com.rm.infill.res.height_none
import com.rm.infill.res.keep_heritage
import com.rm.infill.res.limit_parking
import com.rm.infill.res.no_heavy_industry
import com.rm.infill.res.yes
import com.rm.infill.res.no
import com.rm.infill.res.tram_line
import com.rm.infill.res.bus_line
import com.rm.infill.res.trolley_line
import com.rm.infill.res.line_stops
import com.rm.infill.res.clear
import com.rm.infill.res.make_line
import com.rm.infill.res.lines
import com.rm.infill.res.no_lines
import com.rm.infill.res.line_name
import com.rm.infill.res.remove
import com.rm.infill.res.line_info
import com.rm.infill.res.line_riders
import com.rm.infill.res.line_not_running
import com.rm.infill.res.line_vehicles
import com.rm.infill.res.traffic_flow
import com.rm.infill.res.income_offices
import com.rm.infill.res.trade
import com.rm.infill.res.goal_flow
import com.rm.infill.res.trade_out
import com.rm.infill.res.trade_in
import com.rm.infill.res.emergency_repairs
import com.rm.infill.res.upkeep_garbage
import com.rm.infill.res.upkeep_disasters
import com.rm.infill.res.power
import com.rm.infill.res.power_capacity
import com.rm.infill.res.power_peak
import com.rm.infill.res.power_short
import com.rm.infill.res.garbage
import com.rm.infill.res.garbage_taken
import com.rm.infill.res.dump_room
import com.rm.infill.res.tonnes
import com.rm.infill.res.smog
import com.rm.infill.res.megawatts
import com.rm.infill.res.Res
import com.rm.infill.res.budget
import com.rm.infill.res.done
import com.rm.infill.res.fire_station
import com.rm.infill.res.funding
import com.rm.infill.res.graphs
import com.rm.infill.res.last_month
import com.rm.infill.res.less
import com.rm.infill.res.money
import com.rm.infill.res.month_short
import com.rm.infill.res.more
import com.rm.infill.res.net
import com.rm.infill.res.no_history
import com.rm.infill.res.park
import com.rm.infill.res.percent
import com.rm.infill.res.police_station
import com.rm.infill.res.series_crime
import com.rm.infill.res.series_funds
import com.rm.infill.res.series_income
import com.rm.infill.res.series_jobs
import com.rm.infill.res.series_land_value
import com.rm.infill.res.series_pollution
import com.rm.infill.res.series_population
import com.rm.infill.res.series_upkeep
import com.rm.infill.res.tax_commercial
import com.rm.infill.res.tax_industrial
import com.rm.infill.res.tax_residential
import com.rm.infill.res.taxes
import com.rm.infill.res.upkeep_power
import com.rm.infill.res.upkeep_roads
import com.rm.infill.res.upkeep_rail
import com.rm.infill.res.upkeep_water
import com.rm.infill.res.upkeep_flood
import com.rm.infill.res.upkeep_schools
import com.rm.infill.res.upkeep_health
import com.rm.infill.res.upkeep_repairs
import com.rm.infill.res.upkeep_transit
import com.rm.infill.res.income_fares
import com.rm.infill.res.commutes
import com.rm.infill.res.mode_walk
import com.rm.infill.res.mode_car
import com.rm.infill.res.mode_bus
import com.rm.infill.res.mode_trolley
import com.rm.infill.res.trolley_wire
import com.rm.infill.sim.Balance
import com.rm.infill.res.mode_tram
import com.rm.infill.res.mode_subway
import com.rm.infill.res.mode_train
import com.rm.infill.sim.Series
import com.rm.infill.res.era_township
import com.rm.infill.res.era_streetcar
import com.rm.infill.res.era_motor
import com.rm.infill.res.era_renewal
import com.rm.infill.res.era_infill
import com.rm.infill.res.era_future
import com.rm.infill.res.era_township_line
import com.rm.infill.res.era_streetcar_line
import com.rm.infill.res.era_motor_line
import com.rm.infill.res.era_renewal_line
import com.rm.infill.res.era_infill_line
import com.rm.infill.res.era_future_line
import com.rm.infill.res.era_brings
import com.rm.infill.res.era_next
import com.rm.infill.res.goal_people
import com.rm.infill.res.goal_mains_or_station
import com.rm.infill.res.goal_on_mains
import com.rm.infill.res.goal_on_sewer
import com.rm.infill.res.goal_powered
import com.rm.infill.res.goal_downtown
import com.rm.infill.res.goal_high_school
import com.rm.infill.res.goal_land_built
import com.rm.infill.res.goal_met
import com.rm.infill.res.goal_kept_up
import com.rm.infill.res.goal_green_trips
import com.rm.infill.res.goal_not_met
import com.rm.infill.sim.Era
import com.rm.infill.sim.Material
import com.rm.infill.sim.Goal
import com.rm.infill.sim.GoalKind
import com.rm.infill.sim.RoadType
import com.rm.infill.sim.BuildingType
import com.rm.infill.sim.Wealth
import androidx.compose.ui.text.style.TextAlign
import com.rm.infill.res.people
import com.rm.infill.res.children
import com.rm.infill.res.adults
import com.rm.infill.res.elderly
import com.rm.infill.res.health
import com.rm.infill.res.empty_homes
import com.rm.infill.res.born
import com.rm.infill.res.died
import com.rm.infill.res.moved_in
import com.rm.infill.res.moved_out
import com.rm.infill.res.homes_by_wealth
import com.rm.infill.res.work_by_schooling
import com.rm.infill.res.workers
import com.rm.infill.res.jobs_heading
import com.rm.infill.res.unschooled
import com.rm.infill.res.schooled
import com.rm.infill.res.educated
import com.rm.infill.res.school_places
import com.rm.infill.res.high_school_places
import com.rm.infill.res.care_places
import com.rm.infill.res.taken_of
import com.rm.infill.res.schools_and_care
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * A window over the map: the map dims behind it and a tap outside closes it.
 * Its title has a drawing and a close button. It keeps clear of the camera
 * cutout and scrolls if the screen is short.
 */
@Composable
fun Window(title: StringResource, onClose: () -> Unit, glyph: Glyph? = null, content: @Composable () -> Unit) {
    WindowFrame(stringResource(title), onClose, glyph, content)
}

@Composable
fun WindowFrame(title: String, onClose: () -> Unit, glyph: Glyph? = null, content: @Composable () -> Unit) {
    val c = Infill.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        ChromeBox(
            Modifier
                .widthIn(max = 480.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    glyph?.let {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                            GlyphIcon(it, c.onAccent, Modifier.size(20.dp))
                        }
                    }
                    Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    CloseButton(onClose)
                }
                Column(Modifier.padding(top = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) { content() }
            }
        }
    }
}

/** Taxes, what each service gets, last month's money in and out, and the power and garbage. */
@Composable
fun BudgetWindow(game: GameState, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    val s = city.stats
    Window(Res.string.budget, onClose, Glyph.Coins) {
        Section(stringResource(Res.string.taxes), Glyph.Coin) {
            Stepper(ZoneMark(com.rm.infill.sim.Zone.RESIDENTIAL), stringResource(Res.string.tax_residential), city.residentialTax, 1) { game.setTaxes(r = (city.residentialTax + it).coerceIn(0, 20)) }
            Stepper(ZoneMark(com.rm.infill.sim.Zone.COMMERCIAL), stringResource(Res.string.tax_commercial), city.commercialTax, 1) { game.setTaxes(c = (city.commercialTax + it).coerceIn(0, 20)) }
            Stepper(ZoneMark(com.rm.infill.sim.Zone.INDUSTRIAL), stringResource(Res.string.tax_industrial), city.industrialTax, 1) { game.setTaxes(i = (city.industrialTax + it).coerceIn(0, 20)) }
        }
        Section(stringResource(Res.string.funding), Glyph.Civic) {
            Stepper(GlyphMark(Glyph.Star), stringResource(Res.string.police_station), city.policeFunding, 10) { game.setFunding(police = (city.policeFunding + it).coerceIn(0, 100)) }
            Stepper(GlyphMark(Glyph.Flame), stringResource(Res.string.fire_station), city.fireFunding, 10) { game.setFunding(fire = (city.fireFunding + it).coerceIn(0, 100)) }
            Stepper(GlyphMark(Glyph.Tree), stringResource(Res.string.park), city.parkFunding, 10) { game.setFunding(parks = (city.parkFunding + it).coerceIn(0, 100)) }
            Stepper(GlyphMark(Glyph.Cap), stringResource(Res.string.upkeep_schools), city.schoolFunding, 10) { game.setFunding(schools = (city.schoolFunding + it).coerceIn(0, 100)) }
            Stepper(GlyphMark(Glyph.Cross), stringResource(Res.string.upkeep_health), city.healthFunding, 10) { game.setFunding(health = (city.healthFunding + it).coerceIn(0, 100)) }
            Stepper(GlyphMark(Glyph.Wrench), stringResource(Res.string.emergency_repairs), city.reliefFunding, 25) { game.setFunding(relief = (city.reliefFunding + it).coerceIn(50, 200)) }
            if (s.tolls > 0 || city.anyTolls) {
                StepperRow(GlyphMark(Glyph.Bridge), stringResource(Res.string.toll_rate), stringResource(Res.string.value_cents, city.tollRate), { game.setTollRate(city.tollRate + it) }, 5)
            }
        }
        Section(stringResource(Res.string.last_month), Glyph.Calendar) {
            val income = listOfNotNull(
                Triple(Glyph.Person, Res.string.tax_residential, s.residentialIncome),
                Triple(Glyph.Crate, Res.string.tax_commercial, s.commercialIncome),
                Triple(Glyph.Building, Res.string.tax_industrial, s.industrialIncome),
                if (s.officeIncome > 0) Triple(Glyph.Briefcase, Res.string.income_offices, s.officeIncome) else null,
                if (s.fareIncome > 0) Triple(Glyph.Bus, Res.string.income_fares, s.fareIncome) else null,
                if (s.duesIncome + s.tollIncome > 0) Triple(Glyph.Anchor, Res.string.income_dues, s.duesIncome + s.tollIncome) else null,
            )
            val upkeep = listOfNotNull(
                Triple(Glyph.Road, Res.string.upkeep_roads, s.roadUpkeep),
                if (s.railUpkeep > 0) Triple(Glyph.Rail, Res.string.upkeep_rail, s.railUpkeep) else null,
                if (s.waterUpkeep > 0) Triple(Glyph.Drop, Res.string.upkeep_water, s.waterUpkeep) else null,
                if (s.floodCost > 0) Triple(Glyph.Rain, Res.string.upkeep_flood, s.floodCost) else null,
                Triple(Glyph.Bolt, Res.string.upkeep_power, s.powerUpkeep),
                Triple(Glyph.Star, Res.string.police_station, s.policeUpkeep),
                Triple(Glyph.Flame, Res.string.fire_station, s.fireUpkeep),
                Triple(Glyph.Tree, Res.string.park, s.parkUpkeep),
                if (s.schoolUpkeep > 0) Triple(Glyph.Cap, Res.string.upkeep_schools, s.schoolUpkeep) else null,
                if (s.healthUpkeep > 0) Triple(Glyph.Cross, Res.string.upkeep_health, s.healthUpkeep) else null,
                if (s.repairCost > 0) Triple(Glyph.Wrench, Res.string.upkeep_repairs, s.repairCost) else null,
                if (s.transitUpkeep > 0) Triple(Glyph.Tram, Res.string.upkeep_transit, s.transitUpkeep) else null,
                if (s.environmentUpkeep > 0) Triple(Glyph.Bin, Res.string.upkeep_garbage, s.environmentUpkeep) else null,
                if (s.disasterCost > 0) Triple(Glyph.Warn, Res.string.upkeep_disasters, s.disasterCost) else null,
                if (s.phoneUpkeep > 0) Triple(Glyph.Phone, Res.string.upkeep_phone, s.phoneUpkeep) else null,
                if (s.portUpkeep > 0) Triple(Glyph.Anchor, Res.string.upkeep_ports, s.portUpkeep) else null,
            )
            // Every bar against the biggest, in or out.
            val most = maxOf(1L, (income + upkeep).maxOf { it.third })
            for ((g, label, amount) in income) MoneyBar(g, stringResource(label), amount, most, c.good)
            Divider()
            for ((g, label, amount) in upkeep) MoneyBar(g, stringResource(label), -amount, most, c.bad)
            Divider()
            val net = s.income - s.upkeep
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.net), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(moneyText(net), color = if (net < 0) c.bad else c.good, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        @Composable
        fun mw(kw: Long) = stringResource(Res.string.megawatts, groupThousands((kw + 500) / 1000))
        Section(stringResource(Res.string.power), Glyph.Bolt) {
            val use = if (s.powerCapacity > 0) (s.powerDemand * 100 / s.powerCapacity).toInt() else 0
            StatGrid(
                listOfNotNull(
                    StatItem(Glyph.Bolt, stringResource(Res.string.power_peak), stringResource(Res.string.value_of, mw(s.powerDemand), mw(s.powerCapacity)), minOf(1f, use / 100f), when { use > 100 -> Tone.Bad; use > 85 -> Tone.Warn; else -> Tone.Good }, wide = true),
                    if (s.powerShort > 0) StatItem(Glyph.Warn, stringResource(Res.string.power_short), mw(s.powerShort), 1f, Tone.Bad) else null,
                ),
            )
        }
        Section(stringResource(Res.string.garbage), Glyph.Bin) {
            StatGrid(
                listOfNotNull(
                    StatItem(Glyph.Bin, stringResource(Res.string.garbage_taken), stringResource(Res.string.percent, s.wasteCollected), s.wasteCollected / 100f, toneOf(s.wasteCollected, 95, 70)),
                    if (s.dumpRoom > 0) StatItem(Glyph.Mountain, stringResource(Res.string.dump_room), stringResource(Res.string.tonnes, groupThousands(s.dumpRoom.toLong()))) else null,
                    if (s.smog > 0) StatItem(Glyph.Smoke, stringResource(Res.string.smog), stringResource(Res.string.percent, s.smog * 100 / 255), s.smog / 255f, if (s.smog >= 128) Tone.Bad else Tone.Warn) else null,
                ),
            )
        }
        // What the town's businesses trade. The town's own money is above.
        if (s.exportValue > 0 || s.importValue > 0) {
            Section(stringResource(Res.string.trade), Glyph.Crate) {
                StatGrid(
                    listOf(
                        StatItem(Glyph.Arrows, stringResource(Res.string.trade_out), moneyText(s.exportValue)),
                        StatItem(Glyph.Arrows, stringResource(Res.string.trade_in), moneyText(s.importValue)),
                    ),
                )
            }
        }
    }
}

/** What's drawn at the start of a stepper's row: a zone's colour or a drawing. */
sealed class Mark
class ZoneMark(val zone: Byte) : Mark()
class GlyphMark(val glyph: Glyph) : Mark()

@Composable
private fun MarkIcon(mark: Mark) {
    val c = Infill.colors
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(c.button), contentAlignment = Alignment.Center) {
        when (mark) {
            is ZoneMark -> Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(zoneColour(mark.zone)))
            is GlyphMark -> GlyphIcon(mark.glyph, c.textDim, Modifier.size(17.dp))
        }
    }
}

/** A name, a percentage and buttons to take [step] off it or add it. */
@Composable
private fun Stepper(mark: Mark, name: String, value: Int, step: Int, change: (Int) -> Unit) {
    StepperRow(mark, name, stringResource(Res.string.percent, value), change, step)
}

@Composable
private fun StepperRow(mark: Mark?, name: String, value: String, change: (Int) -> Unit, step: Int = 1) {
    val c = Infill.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        mark?.let { MarkIcon(it) }
        Text(name, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        StepButton("−", stringResource(Res.string.less) + " " + name) { change(-step) }
        Text(
            value, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center,
        )
        StepButton("+", stringResource(Res.string.more) + " " + name) { change(step) }
    }
}

@Composable
private fun StepButton(sign: String, description: String, onClick: () -> Unit) {
    val c = Infill.colors
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.button)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(sign, color = c.text, fontSize = 18.sp) }
}

/** A line of money: its drawing and name, a bar as long as its share of [most], and the amount. */
@Composable
private fun MoneyBar(glyph: Glyph, label: String, amount: Long, most: Long, colour: Color) {
    val c = Infill.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlyphIcon(glyph, c.textDim, Modifier.size(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = c.textDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Meter(kotlin.math.abs(amount) / most.toFloat(), colour, Modifier.fillMaxWidth(), 6.dp)
        }
        Text(moneyText(amount), color = c.text, fontSize = 14.sp, modifier = Modifier.widthIn(min = 72.dp), textAlign = TextAlign.End)
    }
}

private val SERIES = listOf(
    Triple(Series.Population, Res.string.series_population, Glyph.Person),
    Triple(Series.Jobs, Res.string.series_jobs, Glyph.Briefcase),
    Triple(Series.Funds, Res.string.series_funds, Glyph.Coins),
    Triple(Series.Income, Res.string.series_income, Glyph.Coin),
    Triple(Series.Upkeep, Res.string.series_upkeep, Glyph.Wrench),
    Triple(Series.Crime, Res.string.series_crime, Glyph.Cuffs),
    Triple(Series.Pollution, Res.string.series_pollution, Glyph.Smoke),
    Triple(Series.LandValue, Res.string.series_land_value, Glyph.Mountain),
)

/** The town over the years, one thing at a time. */
@Composable
fun GraphsWindow(game: GameState, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val history = game.city.history
    var series by remember { mutableStateOf(Series.Population) }
    Window(Res.string.graphs, onClose, Glyph.Arrows) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Two rows of four, so they fit an upright phone.
            for (row in SERIES.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((s, name, glyph) in row) {
                        val on = s == series
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) c.accent else c.button)
                                .clickable(role = Role.Tab) { series = s }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GlyphIcon(glyph, if (on) c.onAccent else c.text, Modifier.size(18.dp))
                            Text(stringResource(name), color = if (on) c.onAccent else c.text, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
            if (history.count < 2) {
                Text(stringResource(Res.string.no_history), color = c.textDim, fontSize = 14.sp)
            } else {
                val values = history.values(series)
                val months = stringArrayResource(Res.array.month_short)
                val (y0, m0) = history.dateOf(0)
                val (y1, m1) = history.dateOf(history.count - 1)
                val top = values.max()
                val money = series == Series.Funds || series == Series.Income || series == Series.Upkeep
                Row(Modifier.fillMaxWidth()) {
                    Text(shownText(top, money), color = c.textDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(shownText(values.last(), money), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                LineChart(values, c.accent, c.chromeEdge, Modifier.fillMaxWidth().height(180.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${months.getOrElse(m0) { "" }} $y0", color = c.textDim, fontSize = 12.sp)
                    Text("${months.getOrElse(m1) { "" }} $y1", color = c.textDim, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun shownText(v: Long, money: Boolean) = if (money) moneyText(v) else groupThousands(v)

/** A line over time from zero up to the highest value, with a soft fill under it. */
@Composable
private fun LineChart(values: LongArray, line: Color, grid: Color, modifier: Modifier) {
    Canvas(modifier) {
        val top = maxOf(1L, values.max()).toFloat()
        val bottom = minOf(0L, values.min()).toFloat()
        val span = top - bottom
        for (k in 0..3) {
            val y = size.height * k / 3f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        fun at(k: Int) = Offset(
            size.width * k / (values.size - 1).coerceAtLeast(1),
            size.height - (values[k] - bottom) / span * size.height,
        )
        val path = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (k in 1 until values.size) lineTo(at(k).x, at(k).y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, line.copy(alpha = 0.15f))
        drawPath(path, line, style = Stroke(2.dp.toPx()))
    }
}

/** Colours for the ages, the wealth and the ways to work, in the people window's bars. */
private val AGES = listOf(Color(0xFF7FC4E8), Color(0xFF4C8FD6), Color(0xFF9A7AC8))
private val WEALTH = listOf(Color(0xFFB08A5A), Color(0xFF8FA85A), Color(0xFFE0B83A))
private val MODES = listOf(
    Color(0xFF8FBF6A), Color(0xFF8A8F98), Color(0xFF2FA85A), Color(0xFF16A2A2), Color(0xFFD8302F), Color(0xFF2F6FD8), Color(0xFF8E44AD),
)

/** Colours for the ways visitors come: road, rail, sea and air. */
private val VISITOR_COLOURS = listOf(Color(0xFF8A8F98), Color(0xFF8E44AD), Color(0xFF2F6FD8), Color(0xFF16A2A2))

/** Who lives in the town, the work they're schooled for, places at school and with a doctor, and how justice is doing. */
@Composable
fun PeopleWindow(game: GameState, onGraphs: () -> Unit, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val s = game.city.stats
    fun n(v: Int) = groupThousands(v.toLong())
    Window(Res.string.people, onClose, Glyph.Person) {
        StatGrid(
            listOfNotNull(
                StatItem(Glyph.Person, stringResource(Res.string.population), n(s.population)),
                StatItem(Glyph.Cross, stringResource(Res.string.health), healthWord(s.health).replaceFirstChar { it.uppercase() }, s.health / 100f, toneOf(s.health, 65, 45)),
                StatItem(Glyph.Briefcase, stringResource(Res.string.label_unemployed), stringResource(Res.string.percent, s.unemployment), s.unemployment / 100f, when { s.unemployment >= 15 -> Tone.Bad; s.unemployment >= 7 -> Tone.Warn; else -> Tone.Good }),
                if (s.commute > 0) StatItem(Glyph.Car, stringResource(Res.string.label_commute), stringResource(Res.string.value_minutes, s.commute)) else null,
                StatItem(Glyph.Lights, stringResource(Res.string.traffic_flow), stringResource(Res.string.percent, s.flow), s.flow / 100f, toneOf(s.flow, 80, 50)),
                if (s.emptyHomes > 0) StatItem(Glyph.Tag, stringResource(Res.string.empty_homes), n(s.emptyHomes)) else null,
            ),
        )
        Section(stringResource(Res.string.label_ages), Glyph.Hourglass) {
            BarWithKey(listOf(Triple(stringResource(Res.string.children), s.children, AGES[0]), Triple(stringResource(Res.string.adults), s.adults, AGES[1]), Triple(stringResource(Res.string.elderly), s.elderly, AGES[2])))
        }
        Section(stringResource(Res.string.homes_by_wealth), Glyph.Coins) {
            BarWithKey((0 until Wealth.LEVELS).map { Triple(stringResource(wealthName(it)), s.byWealth[it], WEALTH[it]) })
        }
        Section(stringResource(Res.string.last_month), Glyph.Calendar) {
            StatGrid(
                listOf(
                    StatItem(Glyph.Plus, stringResource(Res.string.born), n(s.births)),
                    StatItem(Glyph.Erase, stringResource(Res.string.died), n(s.deaths)),
                    StatItem(Glyph.Arrows, stringResource(Res.string.moved_in), n(s.movedIn)),
                    StatItem(Glyph.Arrows, stringResource(Res.string.moved_out), n(s.movedOut)),
                ),
            )
        }
        // How commutes are made, those used at all.
        val trips = s.byMode.sum()
        if (trips > 0) {
            Section(stringResource(Res.string.commutes), Glyph.Car) {
                val names = listOf(Res.string.mode_walk, Res.string.mode_car, Res.string.mode_bus, Res.string.mode_trolley, Res.string.mode_tram, Res.string.mode_subway, Res.string.mode_train)
                BarWithKey(s.byMode.indices.filter { s.byMode[it] > 0 }.map { Triple(stringResource(names[it]), s.byMode[it] * 100 / trips, MODES[it]) }, percent = true)
            }
        }
        Section(stringResource(Res.string.work_by_schooling), Glyph.Briefcase) {
            val most = maxOf(1, (s.workersBy + s.jobsBy).max())
            for ((k, name) in listOf(Res.string.unschooled, Res.string.schooled, Res.string.educated).withIndex()) {
                val short = s.filledBy[k] < s.jobsBy[k]
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(name), color = c.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(stringResource(Res.string.workers_jobs, n(s.workersBy[k]), n(s.jobsBy[k])), color = if (short) c.bad else c.textDim, fontSize = 12.sp)
                    }
                    Meter(s.workersBy[k] / most.toFloat(), c.accent, Modifier.fillMaxWidth(), 5.dp)
                    Meter(s.jobsBy[k] / most.toFloat(), if (short) c.bad else c.textDim, Modifier.fillMaxWidth(), 5.dp)
                }
            }
        }
        Section(stringResource(Res.string.schools_and_care), Glyph.Cap) {
            StatGrid(
                listOfNotNull(
                    places(Glyph.Cap, stringResource(Res.string.school_places), s.pupils, s.schoolPlaces),
                    if (s.highSchoolPlaces > 0) places(Glyph.Cap, stringResource(Res.string.high_school_places), s.highSchoolPupils, s.highSchoolPlaces) else null,
                    places(Glyph.Cross, stringResource(Res.string.care_places), s.cared, s.carePlaces),
                ),
            )
        }
        Section(stringResource(Res.string.tool_phone), Glyph.Phone) {
            StatGrid(
                listOfNotNull(
                    StatItem(Glyph.Phone, stringResource(Res.string.label_with_phone), stringResource(Res.string.percent, s.withPhone), s.withPhone / 100f, toneOf(s.withPhone, 80, 40)),
                    if (s.withBroadband > 0) StatItem(Glyph.Mast, stringResource(Res.string.label_with_broadband), stringResource(Res.string.percent, s.withBroadband), s.withBroadband / 100f, toneOf(s.withBroadband, 70, 30)) else null,
                    if (s.workingFromHome > 0) StatItem(Glyph.Building, stringResource(Res.string.label_wfh), n(s.workingFromHome)) else null,
                ),
            )
        }
        if (s.visitors > 0) Section(stringResource(Res.string.visitors), Glyph.Suitcase) {
            val ways = listOf(Res.string.by_road, Res.string.by_rail, Res.string.by_sea, Res.string.by_air)
            BarWithKey(ways.indices.filter { s.visitorsBy[it] > 0 }.map { Triple(stringResource(ways[it]), s.visitorsBy[it], VISITOR_COLOURS[it]) })
            StatGrid(
                listOf(
                    StatItem(Glyph.Suitcase, stringResource(Res.string.label_visitors), n(s.visitors)),
                    places(Glyph.Building, stringResource(Res.string.label_hotel_rooms), s.guests, s.rooms),
                ),
            )
        }
        Section(stringResource(Res.string.justice), Glyph.Gavel) {
            StatGrid(
                listOfNotNull(
                    StatItem(Glyph.Cuffs, stringResource(Res.string.label_offences), n(s.offences)),
                    StatItem(Glyph.Star, stringResource(Res.string.label_arrests_all), n(s.arrests)),
                    StatItem(Glyph.Gavel, stringResource(Res.string.label_justice), stringResource(Res.string.percent, s.justice), s.justice / 100f, toneOf(s.justice, 90, 60)),
                    places(Glyph.Cuffs, stringResource(Res.string.label_prisoners), s.prisoners, s.cells),
                    if (s.rackets > 0) StatItem(Glyph.Hat, stringResource(Res.string.label_rackets), level(s.rackets).replaceFirstChar { it.uppercase() }, minOf(1f, s.rackets / 64f), Tone.Bad) else null,
                ),
            )
        }
        Actions(listOf(ActionItem(Glyph.Arrows, stringResource(Res.string.graphs), onClick = onGraphs)))
    }
}

/** How full some places are: green with room, amber near full, red past it. */
@Composable
private fun places(glyph: Glyph, label: String, taken: Int, room: Int): StatItem {
    val share = if (room == 0) (if (taken > 0) 200 else 0) else taken * 100 / room
    return StatItem(
        glyph, label, stringResource(Res.string.value_of, groupThousands(taken.toLong()), groupThousands(room.toLong())),
        minOf(1f, share / 100f), when { share > 100 -> Tone.Bad; share > 90 -> Tone.Warn; else -> Tone.Good },
    )
}

/** A bar of parts with its key under it. */
@Composable
private fun BarWithKey(parts: List<Triple<String, Int, Color>>, percent: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StackedBar(parts.map { it.second to it.third }, Modifier.fillMaxWidth(), 12.dp)
        BarKey(parts.map { Triple(it.first, it.second, it.third) }, percent)
    }
}

fun eraName(era: Era): StringResource = when (era) {
    Era.TOWNSHIP -> Res.string.era_township
    Era.STREETCAR -> Res.string.era_streetcar
    Era.MOTOR -> Res.string.era_motor
    Era.RENEWAL -> Res.string.era_renewal
    Era.INFILL -> Res.string.era_infill
    Era.FUTURE -> Res.string.era_future
}

private fun eraLine(era: Era): StringResource = when (era) {
    Era.TOWNSHIP -> Res.string.era_township_line
    Era.STREETCAR -> Res.string.era_streetcar_line
    Era.MOTOR -> Res.string.era_motor_line
    Era.RENEWAL -> Res.string.era_renewal_line
    Era.INFILL -> Res.string.era_infill_line
    Era.FUTURE -> Res.string.era_future_line
}

/**
 * An era: what it's about, the roads and buildings it brings as pictures, and
 * how far the town is towards each thing the next one needs.
 */
@Composable
fun EraWindow(game: GameState, era: Era, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    Window(eraName(era), onClose, Glyph.Calendar) {
        Text(stringResource(eraLine(era)), color = c.text, fontSize = 15.sp)
        val brings = buildList {
            if (era != Era.TOWNSHIP) {
                for (t in RoadType.entries) if (Era.of(t.year) == era) add(stringResource(roadName(t)) to roadIcon(t))
                for (t in BuildingType.entries) if (Era.of(t.year) == era) add(stringResource(buildingName(t)) to buildingIcon(t))
                for (m in Material.entries) if (Era.of(m.year) == era) add(stringResource(materialName(m)) to ChoiceIcon(glyph = Glyph.Pipe, glyphColour = PIPE_COLOURS[m.pipe]))
                if (Era.of(Balance.TROLLEYBUS_YEAR) == era) add(stringResource(Res.string.trolley_wire) to ChoiceIcon(intArrayOf(com.rm.infill.map.Atlas.ROAD_STREET + 10, com.rm.infill.map.Atlas.TROLLEY_WIRE + 10)))
            }
        }
        if (brings.isNotEmpty()) {
            Section(stringResource(Res.string.era_brings), Glyph.Plus) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((name, icon) in brings) {
                        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            ChoiceTile(LocalAtlas.current, icon, name, false, 52.dp, null)
                            Text(name, color = c.textDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        // What the next era needs, from the one the town's in.
        val next = city.era.next
        if (next != null && era == city.era) {
            Section(stringResource(Res.string.era_next, stringResource(eraName(next)), next.year), Glyph.Target) {
                for (goal in city.goals(next)) GoalRow(goal)
            }
        }
    }
}

@Composable
private fun GoalRow(goal: Goal) {
    val c = Infill.colors
    val share = if (goal.need > 0) minOf(1f, goal.have / goal.need.toFloat()) else if (goal.met) 1f else 0f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(if (goal.met) c.good else c.button),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(if (goal.met) Glyph.Check else Glyph.Hourglass, if (goal.met) Color.White else c.textDim, Modifier.size(16.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row {
                Text(goalText(goal), color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(goalHave(goal), color = if (goal.met) c.good else c.textDim, fontSize = 13.sp)
            }
            Meter(share, if (goal.met) c.good else c.accent, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun goalText(goal: Goal): String = when (goal.kind) {
    GoalKind.People -> stringResource(Res.string.goal_people, groupThousands(goal.need.toLong()))
    GoalKind.MainsOrStation -> stringResource(Res.string.goal_mains_or_station)
    GoalKind.OnMains -> stringResource(Res.string.goal_on_mains, goal.need)
    GoalKind.OnSewer -> stringResource(Res.string.goal_on_sewer, goal.need)
    GoalKind.Powered -> stringResource(Res.string.goal_powered, goal.need)
    GoalKind.Downtown -> stringResource(Res.string.goal_downtown)
    GoalKind.HighSchool -> stringResource(Res.string.goal_high_school)
    GoalKind.LandBuilt -> stringResource(Res.string.goal_land_built, goal.need)
    GoalKind.KeptUp -> stringResource(Res.string.goal_kept_up, goal.need)
    GoalKind.GreenTrips -> stringResource(Res.string.goal_green_trips, goal.need)
    GoalKind.Flow -> stringResource(Res.string.goal_flow, goal.need)
}

/** How far the town is towards a goal: a count, a share, or a tick. */
@Composable
private fun goalHave(goal: Goal): String = when (goal.kind) {
    GoalKind.People -> groupThousands(goal.have.toLong())
    GoalKind.OnMains, GoalKind.OnSewer, GoalKind.Powered, GoalKind.LandBuilt, GoalKind.KeptUp, GoalKind.GreenTrips, GoalKind.Flow ->
        stringResource(Res.string.percent, goal.have)
    else -> stringResource(if (goal.met) Res.string.goal_met else Res.string.goal_not_met)
}

/** A line's colour on the map and in the list, by its id. */
fun lineColour(id: Int): Color = LINE_COLOURS[id.mod(LINE_COLOURS.size)]

private val LINE_COLOURS = listOf(
    Color(0xFFD8302F), Color(0xFF2F6FD8), Color(0xFF2FA85A), Color(0xFFE09A1F), Color(0xFF8E44AD),
    Color(0xFF16A2A2), Color(0xFFD84B9A), Color(0xFF6B4A2E),
)

/** While a line's being planned: how many stops it has so far, and making it or starting again. */
@Composable
fun LineDraftBar(stops: Int, tram: Boolean, onClear: () -> Unit, onMake: () -> Unit) {
    val c = Infill.colors
    ChromeBox {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlyphIcon(Glyph.Route, c.accent, Modifier.size(20.dp))
            Text(
                stringResource(if (tram) Res.string.tram_line else Res.string.bus_line) + ", " + pluralStringResource(Res.plurals.line_stops, stops, stops),
                color = c.text, fontSize = 14.sp,
            )
            if (stops > 0) TextButton(stringResource(Res.string.clear), false, onClear)
            if (stops >= 2) TextButton(stringResource(Res.string.make_line), true, onMake)
        }
    }
}

@Composable
private fun TextButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val c = Infill.colors
    Text(
        text, color = if (primary) c.onAccent else c.text, fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) c.accent else c.button)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** A card in a window: a coloured square with a drawing or letters, a name, a remove button, then what's in it. */
@Composable
private fun ItemCard(colour: Color, glyph: Glyph?, letters: String?, title: String, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val c = Infill.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.button.copy(alpha = 0.55f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(colour), contentAlignment = Alignment.Center) {
                glyph?.let { GlyphIcon(it, Color.White, Modifier.size(18.dp)) }
                letters?.let { Text(it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            ActionButton(ActionItem(Glyph.Remove, stringResource(Res.string.remove), confirm = true, onClick = onRemove))
        }
        content()
    }
}

/** Every line: what runs on it, how it's doing, its vehicles to add or take off, and taking it off altogether. */
@Composable
fun LinesWindow(game: GameState, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    Window(Res.string.lines, onClose, Glyph.Route) {
        if (city.lines.isEmpty()) Text(stringResource(Res.string.no_lines), color = c.textDim, fontSize = 14.sp)
        var buses = 0
        var trams = 0
        for (line in city.lines) {
            val state = city.lineState(line.id)
            val number = if (line.tram) ++trams else ++buses
            val trolley = state?.mode == com.rm.infill.sim.Mode.TROLLEY
            val kind = when {
                line.tram -> Res.string.tram_line
                trolley -> Res.string.trolley_line
                else -> Res.string.bus_line
            }
            val glyph = if (line.tram) Glyph.Tram else Glyph.Bus
            ItemCard(lineColour(line.id), glyph, null, stringResource(Res.string.line_name, stringResource(kind), number), { game.apply(Action.RemoveLine(line.id)) }) {
                if (state != null && state.running) {
                    StatGrid(
                        listOf(
                            StatItem(Glyph.Route, stringResource(Res.string.label_stops), line.stops.size.toString()),
                            StatItem(Glyph.Person, stringResource(Res.string.label_riders), groupThousands(city.lineRiders(line.id).toLong())),
                            StatItem(Glyph.Hourglass, stringResource(Res.string.label_round_trip), stringResource(Res.string.value_minutes, state.roundTrip / 60)),
                            StatItem(Glyph.Hourglass, stringResource(Res.string.label_wait), stringResource(Res.string.value_minutes, maxOf(1, state.wait / 60))),
                        ),
                    )
                } else {
                    Pills(listOf(PillItem(Glyph.Warn, stringResource(Res.string.line_not_running), Tone.Warn)))
                }
                StepperRow(GlyphMark(glyph), stringResource(Res.string.line_vehicles), "${line.vehicles}", { d ->
                    val v = line.vehicles + d
                    if (v >= 1) game.apply(Action.SetVehicles(line.id, v))
                })
            }
        }
    }
}

/** Every district: its figures, and its policies to set. */
@Composable
fun DistrictsWindow(game: GameState, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    Window(Res.string.districts, onClose, Glyph.District) {
        if (city.districts.isEmpty()) Text(stringResource(Res.string.no_districts), color = c.textDim, fontSize = 14.sp)
        for (d in city.districts) {
            fun set(change: (com.rm.infill.sim.District) -> Unit) {
                game.apply(Action.SetDistrict(d.id, d.copy().also(change)))
            }
            val f = city.districtFigures(d.id)
            ItemCard(lineColour(d.id), null, d.name.take(2).uppercase(), d.name, { game.apply(Action.RemoveDistrict(d.id)) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell(StatItem(Glyph.Person, stringResource(Res.string.people), groupThousands(f.people.toLong())), Modifier.weight(1f))
                    StatCell(StatItem(Glyph.Briefcase, stringResource(Res.string.label_jobs), groupThousands(f.jobs.toLong())), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniMeter(Glyph.Coin, stringResource(Res.string.label_land_value), f.landValue, highIsBad = false)
                    MiniMeter(Glyph.Cuffs, stringResource(Res.string.label_crime), f.crime, highIsBad = true)
                    MiniMeter(Glyph.Smoke, stringResource(Res.string.label_pollution), f.pollution, highIsBad = true)
                }
                Section(stringResource(Res.string.district_taxes), Glyph.Coin) {
                    val range = Balance.DISTRICT_TAX_RANGE
                    for ((k, label) in listOf(Res.string.tax_homes, Res.string.tax_shops, Res.string.tax_works).withIndex()) {
                        val zone = listOf(com.rm.infill.sim.Zone.RESIDENTIAL, com.rm.infill.sim.Zone.COMMERCIAL, com.rm.infill.sim.Zone.INDUSTRIAL)[k]
                        val shown = if (d.tax[k] > 0) "+${d.tax[k]}" else if (d.tax[k] < 0) "−${-d.tax[k]}" else "0"
                        StepperRow(ZoneMark(zone), stringResource(label), shown, { step -> set { it.tax[k] = (it.tax[k] + step).coerceIn(-range, range) } })
                    }
                }
                Section(stringResource(Res.string.height_limit), Glyph.High) {
                    Chips(listOf(Density.NONE, Density.LOW, Density.MEDIUM), d.height, {
                        stringResource(if (it == Density.NONE) Res.string.height_none else densityName(it)!!)
                    }) { h -> set { it.height = h } }
                }
                Section(stringResource(Res.string.policies), Glyph.List) {
                    val policies = listOf(
                        Triple(Glyph.Star, Res.string.keep_heritage, d.heritage) to { v: Boolean -> set { it.heritage = v } },
                        Triple(Glyph.Car, Res.string.limit_parking, d.parking) to { v: Boolean -> set { it.parking = v } },
                        Triple(Glyph.Building, Res.string.no_heavy_industry, d.lightIndustry) to { v: Boolean -> set { it.lightIndustry = v } },
                        Triple(Glyph.Scrubber, Res.string.clean_works, d.cleanWorks) to { v: Boolean -> set { it.cleanWorks = v } },
                        Triple(Glyph.Crate, Res.string.no_trucks, d.noTrucks) to { v: Boolean -> set { it.noTrucks = v } },
                        Triple(Glyph.Bus, Res.string.free_fares, d.freeFares) to { v: Boolean -> set { it.freeFares = v } },
                        Triple(Glyph.Tag, Res.string.rent_control, d.rentControl) to { v: Boolean -> set { it.rentControl = v } },
                    )
                    for (row in policies.chunked(2)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((p, change) in row) PolicyTile(p.first, stringResource(p.second), p.third, Modifier.weight(1f)) { change(!p.third) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** A policy that's on or off: its drawing and name, lit when it's on. */
@Composable
private fun PolicyTile(glyph: Glyph, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Infill.colors
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) c.accent else c.button)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphIcon(glyph, if (on) c.onAccent else c.textDim, Modifier.size(18.dp))
        Text(label, color = if (on) c.onAccent else c.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
