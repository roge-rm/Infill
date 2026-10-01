package com.rm.infill.ui

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
 * It keeps clear of the camera cutout and scrolls if the screen is short.
 */
@Composable
fun Window(title: StringResource, onClose: () -> Unit, content: @Composable () -> Unit) {
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
                .widthIn(max = 460.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(title), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.padding(vertical = 10.dp)) { content() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        stringResource(Res.string.done),
                        color = c.onAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.accent)
                            .clickable(role = Role.Button, onClick = onClose)
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Taxes, what each service gets, and last month's money in and out. */
@Composable
fun BudgetWindow(game: GameState, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    val s = city.stats
    Window(Res.string.budget, onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Heading(Res.string.taxes)
            Stepper(Res.string.tax_residential, city.residentialTax, 1) { game.setTaxes(r = (city.residentialTax + it).coerceIn(0, 20)) }
            Stepper(Res.string.tax_commercial, city.commercialTax, 1) { game.setTaxes(c = (city.commercialTax + it).coerceIn(0, 20)) }
            Stepper(Res.string.tax_industrial, city.industrialTax, 1) { game.setTaxes(i = (city.industrialTax + it).coerceIn(0, 20)) }
            Heading(Res.string.funding)
            Stepper(Res.string.police_station, city.policeFunding, 10) { game.setFunding(police = (city.policeFunding + it).coerceIn(0, 100)) }
            Stepper(Res.string.fire_station, city.fireFunding, 10) { game.setFunding(fire = (city.fireFunding + it).coerceIn(0, 100)) }
            Stepper(Res.string.park, city.parkFunding, 10) { game.setFunding(parks = (city.parkFunding + it).coerceIn(0, 100)) }
            Stepper(Res.string.upkeep_schools, city.schoolFunding, 10) { game.setFunding(schools = (city.schoolFunding + it).coerceIn(0, 100)) }
            Stepper(Res.string.upkeep_health, city.healthFunding, 10) { game.setFunding(health = (city.healthFunding + it).coerceIn(0, 100)) }
            Stepper(Res.string.emergency_repairs, city.reliefFunding, 25) { game.setFunding(relief = (city.reliefFunding + it).coerceIn(50, 200)) }
            Heading(Res.string.power)
            @Composable
            fun mw(kw: Long) = stringResource(Res.string.megawatts, groupThousands((kw + 500) / 1000))
            CountLine(Res.string.power_capacity, mw(s.powerCapacity))
            CountLine(Res.string.power_peak, mw(s.powerDemand))
            if (s.powerShort > 0) CountLine(Res.string.power_short, mw(s.powerShort))
            Heading(Res.string.garbage)
            CountLine(Res.string.garbage_taken, stringResource(Res.string.percent, s.wasteCollected))
            if (s.dumpRoom > 0) CountLine(Res.string.dump_room, stringResource(Res.string.tonnes, groupThousands(s.dumpRoom.toLong())))
            if (s.smog > 0) CountLine(Res.string.smog, stringResource(Res.string.percent, s.smog * 100 / 255))
            // What the town's goods fetched outside, and what it had to bring in.
            if (s.exportValue > 0 || s.importValue > 0) {
                Heading(Res.string.trade)
                // What the town's businesses trade, not the town's own money.
                CountLine(Res.string.trade_out, moneyText(s.exportValue))
                CountLine(Res.string.trade_in, moneyText(s.importValue))
            }
            Heading(Res.string.last_month)
            MoneyLine(Res.string.tax_residential, s.residentialIncome)
            MoneyLine(Res.string.tax_commercial, s.commercialIncome)
            MoneyLine(Res.string.tax_industrial, s.industrialIncome)
            if (s.officeIncome > 0) MoneyLine(Res.string.income_offices, s.officeIncome)
            if (s.fareIncome > 0) MoneyLine(Res.string.income_fares, s.fareIncome)
            MoneyLine(Res.string.upkeep_roads, -s.roadUpkeep)
            if (s.railUpkeep > 0) MoneyLine(Res.string.upkeep_rail, -s.railUpkeep)
            if (s.waterUpkeep > 0) MoneyLine(Res.string.upkeep_water, -s.waterUpkeep)
            if (s.floodCost > 0) MoneyLine(Res.string.upkeep_flood, -s.floodCost)
            MoneyLine(Res.string.upkeep_power, -s.powerUpkeep)
            MoneyLine(Res.string.police_station, -s.policeUpkeep)
            MoneyLine(Res.string.fire_station, -s.fireUpkeep)
            MoneyLine(Res.string.park, -s.parkUpkeep)
            if (s.schoolUpkeep > 0) MoneyLine(Res.string.upkeep_schools, -s.schoolUpkeep)
            if (s.healthUpkeep > 0) MoneyLine(Res.string.upkeep_health, -s.healthUpkeep)
            if (s.repairCost > 0) MoneyLine(Res.string.upkeep_repairs, -s.repairCost)
            if (s.transitUpkeep > 0) MoneyLine(Res.string.upkeep_transit, -s.transitUpkeep)
            if (s.environmentUpkeep > 0) MoneyLine(Res.string.upkeep_garbage, -s.environmentUpkeep)
            if (s.disasterCost > 0) MoneyLine(Res.string.upkeep_disasters, -s.disasterCost)
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.chromeEdge))
            MoneyLine(Res.string.net, s.income - s.upkeep, bold = true)
        }
    }
}

@Composable
private fun Heading(text: StringResource) {
    Text(stringResource(text), color = Infill.colors.textDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
}

/** A name, a percentage and buttons to take [step] off it or add it. */
@Composable
private fun Stepper(label: StringResource, value: Int, step: Int, change: (Int) -> Unit) {
    val c = Infill.colors
    val name = stringResource(label)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        StepButton("−", stringResource(Res.string.less) + " " + name) { change(-step) }
        Text(
            stringResource(Res.string.percent, value),
            color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 52.dp).padding(horizontal = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton("+", stringResource(Res.string.more) + " " + name) { change(step) }
    }
}

@Composable
private fun StepButton(sign: String, description: String, onClick: () -> Unit) {
    val c = Infill.colors
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.button)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(sign, color = c.text, fontSize = 18.sp) }
}

@Composable
private fun MoneyLine(label: StringResource, amount: Long, bold: Boolean = false) {
    val c = Infill.colors
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(label), color = c.textDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            moneyText(amount),
            color = c.text, fontSize = 14.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private val SERIES_NAMES = mapOf(
    Series.Population to Res.string.series_population,
    Series.Jobs to Res.string.series_jobs,
    Series.Funds to Res.string.series_funds,
    Series.Income to Res.string.series_income,
    Series.Upkeep to Res.string.series_upkeep,
    Series.Crime to Res.string.series_crime,
    Series.Pollution to Res.string.series_pollution,
    Series.LandValue to Res.string.series_land_value,
)

/** The town over the years, one thing at a time. */
@Composable
fun GraphsWindow(game: GameState, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val history = game.city.history
    var series by remember { mutableStateOf(Series.Population) }
    Window(Res.string.graphs, onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Two rows of choices, so they fit an upright phone.
            for (row in Series.entries.chunked(4)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (s in row) {
                        val on = s == series
                        Text(
                            stringResource(SERIES_NAMES.getValue(s)),
                            color = if (on) c.onAccent else c.text,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) c.accent else c.button)
                                .clickable(role = Role.Tab) { series = s }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
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
                val topLabel = if (money) stringResource(Res.string.money, groupThousands(top)) else groupThousands(top)
                Row(Modifier.fillMaxWidth()) {
                    Text(topLabel, color = c.textDim, fontSize = 12.sp)
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

/** Who lives in the town: their ages, health and wealth, the work they're schooled for, and places at school and with a doctor. */
@Composable
fun PeopleWindow(game: GameState, onGraphs: () -> Unit, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val s = game.city.stats
    fun n(v: Int) = groupThousands(v.toLong())
    Window(Res.string.people, onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CountLine(Res.string.children, n(s.children))
            CountLine(Res.string.adults, n(s.adults))
            CountLine(Res.string.elderly, n(s.elderly))
            CountLine(Res.string.health, healthWord(s.health))
            CountLine(Res.string.traffic_flow, stringResource(Res.string.percent, s.flow))
            if (s.emptyHomes > 0) CountLine(Res.string.empty_homes, n(s.emptyHomes))
            Heading(Res.string.last_month)
            CountLine(Res.string.born, n(s.births))
            CountLine(Res.string.died, n(s.deaths))
            CountLine(Res.string.moved_in, n(s.movedIn))
            CountLine(Res.string.moved_out, n(s.movedOut))
            // How commutes are made, those used at all.
            val trips = s.byMode.sum()
            if (trips > 0) {
                Heading(Res.string.commutes)
                val names = listOf(Res.string.mode_walk, Res.string.mode_car, Res.string.mode_bus, Res.string.mode_trolley, Res.string.mode_tram, Res.string.mode_subway, Res.string.mode_train)
                for (k in s.byMode.indices) if (s.byMode[k] > 0) CountLine(names[k], stringResource(Res.string.percent, s.byMode[k] * 100 / trips))
            }
            Heading(Res.string.homes_by_wealth)
            for (w in 0 until Wealth.LEVELS) CountLine(wealthName(w), n(s.byWealth[w]))
            Heading(Res.string.work_by_schooling)
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f))
                Text(stringResource(Res.string.workers), color = c.textDim, fontSize = 13.sp, modifier = Modifier.widthIn(min = 72.dp), textAlign = TextAlign.End)
                Text(stringResource(Res.string.jobs_heading), color = c.textDim, fontSize = 13.sp, modifier = Modifier.widthIn(min = 72.dp), textAlign = TextAlign.End)
            }
            for ((k, name) in listOf(Res.string.unschooled, Res.string.schooled, Res.string.educated).withIndex()) {
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(name), color = c.textDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(n(s.workersBy[k]), color = c.text, fontSize = 14.sp, modifier = Modifier.widthIn(min = 72.dp), textAlign = TextAlign.End)
                    // Jobs short of workers show in red.
                    val short = s.filledBy[k] < s.jobsBy[k]
                    Text(
                        n(s.jobsBy[k]), color = if (short) Color(0xFFD84343) else c.text, fontSize = 14.sp,
                        modifier = Modifier.widthIn(min = 72.dp), textAlign = TextAlign.End,
                    )
                }
            }
            Heading(Res.string.schools_and_care)
            CountLine(Res.string.school_places, stringResource(Res.string.taken_of, n(s.pupils), n(s.schoolPlaces)))
            if (s.highSchoolPlaces > 0) CountLine(Res.string.high_school_places, stringResource(Res.string.taken_of, n(s.highSchoolPupils), n(s.highSchoolPlaces)))
            CountLine(Res.string.care_places, stringResource(Res.string.taken_of, n(s.cared), n(s.carePlaces)))
            Text(
                stringResource(Res.string.graphs),
                color = c.text, fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.button)
                    .clickable(role = Role.Button, onClick = onGraphs)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CountLine(label: StringResource, value: String) {
    val c = Infill.colors
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(label), color = c.textDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = c.text, fontSize = 14.sp)
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
 * An era: what it's about, the roads and buildings it brings, and what the
 * town needs for the next one against what it has.
 */
@Composable
fun EraWindow(game: GameState, era: Era, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    Window(eraName(era), onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(eraLine(era)), color = c.text, fontSize = 15.sp)
            val roads = RoadType.entries.filter { Era.of(it.year) == era && era != Era.TOWNSHIP }.map { roadName(it) }
            val buildings = BuildingType.entries.filter { Era.of(it.year) == era && era != Era.TOWNSHIP }.map { buildingName(it) } +
                Material.entries.filter { Era.of(it.year) == era && era != Era.TOWNSHIP }.map { materialName(it) } +
                (if (Era.of(Balance.TROLLEYBUS_YEAR) == era) listOf(Res.string.trolley_wire) else emptyList())
            if (roads.isNotEmpty() || buildings.isNotEmpty()) {
                Heading(Res.string.era_brings)
                for (name in roads + buildings) Text(stringResource(name), color = c.text, fontSize = 14.sp)
            }
            // What the next era needs, from the one the town's in.
            val next = city.era.next
            if (next != null && era == city.era) {
                Text(
                    stringResource(Res.string.era_next, stringResource(eraName(next)), next.year),
                    color = c.textDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
                for (goal in city.goals(next)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(goalText(goal), color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(goalHave(goal), color = if (goal.met) Color(0xFF3FA85A) else c.textDim, fontSize = 14.sp)
                    }
                }
            }
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

/** Every line: what runs on it, how it's doing, its vehicles to add or take off, and taking it off altogether. */
@Composable
fun LinesWindow(game: GameState, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    Window(Res.string.lines, onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (city.lines.isEmpty()) Text(stringResource(Res.string.no_lines), color = c.textDim, fontSize = 14.sp)
            var buses = 0
            var trams = 0
            for (line in city.lines) {
                val state = city.lineState(line.id)
                val number = if (line.tram) ++trams else ++buses
                val kind = when {
                    line.tram -> Res.string.tram_line
                    state?.mode == com.rm.infill.sim.Mode.TROLLEY -> Res.string.trolley_line
                    else -> Res.string.bus_line
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(lineColour(line.id)))
                        Text(stringResource(Res.string.line_name, stringResource(kind), number), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(stringResource(Res.string.remove), false) { game.apply(Action.RemoveLine(line.id)) }
                    }
                    if (state != null && state.running) {
                        Text(stringResource(Res.string.line_info, line.stops.size, state.roundTrip / 60, maxOf(1, state.wait / 60)), color = c.textDim, fontSize = 13.sp)
                        val riders = city.lineRiders(line.id)
                        Text(pluralStringResource(Res.plurals.line_riders, riders, groupThousands(riders.toLong())), color = c.textDim, fontSize = 13.sp)
                    } else {
                        Text(stringResource(Res.string.line_not_running), color = c.textDim, fontSize = 13.sp)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.line_vehicles), color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        StepButton("−", stringResource(Res.string.less)) { if (line.vehicles > 1) game.apply(Action.SetVehicles(line.id, line.vehicles - 1)) }
                        Text(
                            "${line.vehicles}", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.widthIn(min = 40.dp).padding(horizontal = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        StepButton("+", stringResource(Res.string.more)) { game.apply(Action.SetVehicles(line.id, line.vehicles + 1)) }
                    }
                }
            }
        }
    }
}
