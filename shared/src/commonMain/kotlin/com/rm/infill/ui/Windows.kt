package com.rm.infill.ui

import androidx.compose.ui.text.drawText
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.rm.infill.res.graph_all
import com.rm.infill.res.graph_recent
import com.rm.infill.res.series_smog
import com.rm.infill.res.series_deaths
import com.rm.infill.res.series_births
import com.rm.infill.res.series_unemployment
import com.rm.infill.res.series_health
import com.rm.infill.res.chronicle
import com.rm.infill.res.era_then_and_now
import com.rm.infill.res.next_month
import com.rm.infill.res.income_trade
import com.rm.infill.res.sell_bond
import com.rm.infill.res.bond_line
import com.rm.infill.res.bonds_none
import com.rm.infill.res.bond_rating
import com.rm.infill.res.overseer_note
import com.rm.infill.res.bonds
import com.rm.infill.res.edge_link_no
import com.rm.infill.res.edge_link_yes
import com.rm.infill.res.edge_link_what
import com.rm.infill.res.edge_link_title
import com.rm.infill.res.upkeep_civic
import com.rm.infill.res.ordinances
import com.rm.infill.res.law_from
import com.rm.infill.res.law_era
import com.rm.infill.res.approval
import com.rm.infill.res.opinion
import com.rm.infill.res.series_approval
import com.rm.infill.res.district_mood
import com.rm.infill.res.law_cost
import com.rm.infill.res.law_free
import com.rm.infill.res.ordinances_summary
import com.rm.infill.res.ordinances_none
import com.rm.infill.res.ordinance_cost
import com.rm.infill.res.topic_safety
import com.rm.infill.res.topic_health
import com.rm.infill.res.topic_morals
import com.rm.infill.res.topic_environment
import com.rm.infill.res.topic_traffic
import com.rm.infill.res.topic_waste
import com.rm.infill.res.topic_energy
import com.rm.infill.res.topic_culture
import com.rm.infill.res.law_building_code
import com.rm.infill.res.law_public_health_act
import com.rm.infill.res.law_smoke_abatement
import com.rm.infill.res.law_liquor_licences
import com.rm.infill.res.law_sunday_closing
import com.rm.infill.res.law_youth_curfew
import com.rm.infill.res.law_tenement_act
import com.rm.infill.res.law_daylight_saving
import com.rm.infill.res.law_prohibition
import com.rm.infill.res.law_speed_limits
import com.rm.infill.res.law_parking_meters
import com.rm.infill.res.law_school_meals
import com.rm.infill.res.law_fluoridation
import com.rm.infill.res.law_dog_licences
import com.rm.infill.res.law_noise_bylaw
import com.rm.infill.res.law_clean_air_act
import com.rm.infill.res.law_bottle_deposit
import com.rm.infill.res.law_percent_for_art
import com.rm.infill.res.law_tree_protection
import com.rm.infill.res.law_energy_code
import com.rm.infill.res.law_curbside_recycling
import com.rm.infill.res.law_late_licences
import com.rm.infill.res.law_heat_plan
import com.rm.infill.res.law_smoking_ban
import com.rm.infill.res.law_congestion_charge
import com.rm.infill.res.law_carbon_price
import com.rm.infill.res.law_building_code_what
import com.rm.infill.res.law_public_health_act_what
import com.rm.infill.res.law_smoke_abatement_what
import com.rm.infill.res.law_liquor_licences_what
import com.rm.infill.res.law_sunday_closing_what
import com.rm.infill.res.law_youth_curfew_what
import com.rm.infill.res.law_tenement_act_what
import com.rm.infill.res.law_daylight_saving_what
import com.rm.infill.res.law_prohibition_what
import com.rm.infill.res.law_speed_limits_what
import com.rm.infill.res.law_parking_meters_what
import com.rm.infill.res.law_school_meals_what
import com.rm.infill.res.law_fluoridation_what
import com.rm.infill.res.law_dog_licences_what
import com.rm.infill.res.law_noise_bylaw_what
import com.rm.infill.res.law_clean_air_act_what
import com.rm.infill.res.law_bottle_deposit_what
import com.rm.infill.res.law_percent_for_art_what
import com.rm.infill.res.law_tree_protection_what
import com.rm.infill.res.law_energy_code_what
import com.rm.infill.res.law_curbside_recycling_what
import com.rm.infill.res.law_late_licences_what
import com.rm.infill.res.law_heat_plan_what
import com.rm.infill.res.law_smoking_ban_what
import com.rm.infill.res.law_congestion_charge_what
import com.rm.infill.res.law_carbon_price_what
import com.rm.infill.res.parks_and_leisure
import com.rm.infill.res.label_leisure
import com.rm.infill.res.leisure_value
import com.rm.infill.res.series_leisure
import com.rm.infill.res.green_roofs
import com.rm.infill.res.cool_roofs
import com.rm.infill.res.carbon_heating
import com.rm.infill.res.carbon_works
import com.rm.infill.res.carbon_traffic
import com.rm.infill.res.carbon_power
import com.rm.infill.res.value_tonnes
import com.rm.infill.res.label_carbon
import com.rm.infill.res.label_floods
import com.rm.infill.res.label_heat_waves
import com.rm.infill.res.value_degrees
import com.rm.infill.res.label_warming
import com.rm.infill.res.climate
import com.rm.infill.res.series_carbon
import com.rm.infill.res.goal_low_carbon
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

import com.rm.infill.sim.EventKind
import com.rm.infill.sim.Bonds
import com.rm.infill.sim.Topic
import com.rm.infill.sim.Ordinance
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.runtime.compositionLocalOf
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
import com.rm.infill.res.unschooled
import com.rm.infill.res.schooled
import com.rm.infill.res.educated
import com.rm.infill.res.school_places
import com.rm.infill.res.high_school_places
import com.rm.infill.res.care_places
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
fun Window(
    title: StringResource, onClose: () -> Unit, glyph: Glyph? = null, top: (@Composable () -> Unit)? = null,
    /** The id of the manual's section about this window (its file name in manual/ without the number), for a button that opens it. */
    help: String? = null,
    content: @Composable () -> Unit,
) {
    WindowFrame(stringResource(title), onClose, glyph, top, help, content)
}

/** Opens the help at a section of the manual, or at its contents for null. Null where there's no help to open. */
val LocalHelp = staticCompositionLocalOf<((String?) -> Unit)?> { null }

/** Whether the game is being played from the keyboard just now: a key was pressed since the last touch or click. */
val LocalKeyboardPlay = compositionLocalOf { false }

@Composable
fun WindowFrame(title: String, onClose: () -> Unit, glyph: Glyph? = null, top: (@Composable () -> Unit)? = null, help: String? = null, content: @Composable () -> Unit) {
    val c = Infill.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // The dimmed map behind: a tap on it closes the window. Screen readers
        // close it with back or the close button, so it's left out for them.
        Box(
            Modifier
                .matchParentSize()
                .background(Color(0x66000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                .clearAndSetSemantics { },
        )
        ChromeBox(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                .widthIn(max = 480.dp)
                // Taps on the window stay in it, without making it one big button for screen readers.
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { paneTitle = title },
        ) {
            // Tab stays in the window, going round from the last button to the first, and Shift and Tab the other way.
            val whole = remember { FocusRequester() }
            val focusManager = LocalFocusManager.current
            val inputMode = LocalInputModeManager.current
            Column(
                Modifier
                    .padding(16.dp)
                    .focusRequester(whole)
                    .focusProperties { onExit = { cancelFocusChange() } }
                    .focusGroup()
                    .onPreviewKeyEvent { e ->
                        if (e.key != Key.Tab || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        inputMode.requestInputMode(InputMode.Keyboard)
                        if (!e.isShiftPressed) {
                            if (!focusManager.moveFocus(FocusDirection.Next)) runCatching { whole.requestFocus() }
                        } else if (!focusManager.moveFocus(FocusDirection.Previous)) {
                            // From the first, round to the last.
                            runCatching { whole.requestFocus() }
                            repeat(MAX_TABS) { if (!focusManager.moveFocus(FocusDirection.Next)) return@onPreviewKeyEvent true }
                        }
                        true
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    glyph?.let {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                            GlyphIcon(it, c.onAccent, Modifier.size(20.dp))
                        }
                    }
                    Text(
                        title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val openHelp = LocalHelp.current
                    if (help != null && openHelp != null) HelpButton { openHelp(help) }
                    CloseButton(onClose)
                }
                // Opened from the keyboard, the keys go straight into the window.
                val keyed = LocalKeyboardPlay.current
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    if (!keyed) return@LaunchedEffect
                    // Buttons only take focus once the keys are in use.
                    inputMode.requestInputMode(InputMode.Keyboard)
                    runCatching { first.requestFocus() }
                }
                // What stays put above the part that scrolls, such as tabs.
                Column(Modifier.focusRequester(first).focusGroup()) {
                    top?.let { Box(Modifier.padding(top = 14.dp)) { it() } }
                    Column(Modifier.padding(top = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) { content() }
                }
            }
        }
    }
}

/** Taxes, what each service gets, last month's money in and out, and the power and garbage. */
@Composable
fun BudgetWindow(game: GameState, onClose: () -> Unit, onOrdinances: () -> Unit = {}) {
    val c = Infill.colors
    game.revision
    val city = game.city
    val s = city.stats
    Window(Res.string.budget, onClose, Glyph.Coins, help = "money") {
        Section(stringResource(Res.string.taxes), Glyph.Coin) {
            Stepper(ZoneMark(com.rm.infill.sim.Zone.RESIDENTIAL), stringResource(Res.string.tax_residential), city.residentialTax, 1) { game.setTaxes(r = (city.residentialTax + it).coerceIn(0, city.maxTax())) }
            Stepper(ZoneMark(com.rm.infill.sim.Zone.COMMERCIAL), stringResource(Res.string.tax_commercial), city.commercialTax, 1) { game.setTaxes(c = (city.commercialTax + it).coerceIn(0, city.maxTax())) }
            Stepper(ZoneMark(com.rm.infill.sim.Zone.INDUSTRIAL), stringResource(Res.string.tax_industrial), city.industrialTax, 1) { game.setTaxes(i = (city.industrialTax + it).coerceIn(0, city.maxTax())) }
        }
        Section(stringResource(Res.string.bonds), Glyph.Coins) {
            if (city.overseen) Text(stringResource(Res.string.overseer_note), color = c.bad, fontSize = 13.sp)
            Text(stringResource(Res.string.bond_rating, Bonds.RATINGS[city.rating]), color = c.text, fontSize = 14.sp)
            if (city.bonds.isEmpty()) Text(stringResource(Res.string.bonds_none), color = c.textDim, fontSize = 13.sp)
            for (b in city.bonds) {
                Text(
                    stringResource(Res.string.bond_line, moneyText(b.raised), b.year, moneyText(b.payment), city.year + (b.monthsLeft + 11) / 12),
                    color = c.textDim, fontSize = 13.sp,
                )
            }
            val rate = Bonds.rate(city.year, city.rating)
            val percent = "${rate / 100}.${(rate % 100).toString().padStart(2, '0')}"
            for (years in listOf(1, 3)) if (city.canSellBond(years)) {
                TextButton(stringResource(Res.string.sell_bond, moneyText(city.bondSize(years)), percent), false) { game.sellBond(years) }
            }
        }
        Section(stringResource(Res.string.ordinances), Glyph.Gavel) {
            val inForce = Ordinance.entries.count { city.has(it) }
            Text(
                if (inForce == 0) stringResource(Res.string.ordinances_none)
                else stringResource(Res.string.ordinances_summary, inForce, moneyText(city.ordinanceCost()), moneyText(city.ordinanceIncome())),
                color = c.textDim, fontSize = 13.sp,
            )
            TextButton(stringResource(Res.string.ordinances), false, onOrdinances)
        }
        Section(stringResource(Res.string.funding), Glyph.Civic) {
            Stepper(GlyphMark(Glyph.Star), stringResource(Res.string.police_station), city.policeFunding, 10) { game.setFunding(police = (city.policeFunding + it).coerceIn(0, 100)) }
            Stepper(GlyphMark(Glyph.Flame), stringResource(Res.string.fire_station), city.fireFunding, 10) { game.setFunding(fire = (city.fireFunding + it).coerceIn(0, 100)) }
            Stepper(GlyphMark(Glyph.Tree), stringResource(Res.string.parks_and_leisure), city.parkFunding, 10) { game.setFunding(parks = (city.parkFunding + it).coerceIn(0, 100)) }
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
                if (s.ordinanceIncome > 0) Triple(Glyph.Gavel, Res.string.ordinance_cost, s.ordinanceIncome) else null,
                if (s.neighbourIncome > 0) Triple(Glyph.Arrows, Res.string.income_next_door, s.neighbourIncome) else null,
                if (s.tradeIncome > 0) Triple(Glyph.Crate, Res.string.income_trade, s.tradeIncome) else null,
            )
            val upkeep = listOfNotNull(
                Triple(Glyph.Road, Res.string.upkeep_roads, s.roadUpkeep),
                if (s.railUpkeep > 0) Triple(Glyph.Rail, Res.string.upkeep_rail, s.railUpkeep) else null,
                if (s.waterUpkeep > 0) Triple(Glyph.Drop, Res.string.upkeep_water, s.waterUpkeep) else null,
                if (s.floodCost > 0) Triple(Glyph.Rain, Res.string.upkeep_flood, s.floodCost) else null,
                Triple(Glyph.Bolt, Res.string.upkeep_power, s.powerUpkeep),
                Triple(Glyph.Star, Res.string.police_station, s.policeUpkeep),
                Triple(Glyph.Flame, Res.string.fire_station, s.fireUpkeep),
                Triple(Glyph.Tree, Res.string.parks_and_leisure, s.parkUpkeep),
                if (s.schoolUpkeep > 0) Triple(Glyph.Cap, Res.string.upkeep_schools, s.schoolUpkeep) else null,
                if (s.healthUpkeep > 0) Triple(Glyph.Cross, Res.string.upkeep_health, s.healthUpkeep) else null,
                if (s.repairCost > 0) Triple(Glyph.Wrench, Res.string.upkeep_repairs, s.repairCost) else null,
                if (s.transitUpkeep > 0) Triple(Glyph.Tram, Res.string.upkeep_transit, s.transitUpkeep) else null,
                if (s.environmentUpkeep > 0) Triple(Glyph.Bin, Res.string.upkeep_garbage, s.environmentUpkeep) else null,
                if (s.disasterCost > 0) Triple(Glyph.Warn, Res.string.upkeep_disasters, s.disasterCost) else null,
                if (s.phoneUpkeep > 0) Triple(Glyph.Phone, Res.string.upkeep_phone, s.phoneUpkeep) else null,
                if (s.portUpkeep > 0) Triple(Glyph.Anchor, Res.string.upkeep_ports, s.portUpkeep) else null,
                if (s.neighbourCost > 0) Triple(Glyph.Arrows, Res.string.upkeep_next_door, s.neighbourCost) else null,
                if (s.ordinanceCost > 0) Triple(Glyph.Gavel, Res.string.ordinance_cost, s.ordinanceCost) else null,
                if (s.civicUpkeep > 0) Triple(Glyph.Civic, Res.string.upkeep_civic, s.civicUpkeep) else null,
                if (s.bondCost > 0) Triple(Glyph.Coins, Res.string.bonds, s.bondCost) else null,
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
            val next = city.nextMonth()
            Text(stringResource(Res.string.next_month, moneyText(next)), color = c.textDim, fontSize = 13.sp)
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
        // Power, water and garbage over the border, by the deals with the neighbours.
        if (s.powerIn + s.powerOut + s.waterIn + s.waterOut + s.garbageIn + s.garbageOut > 0) {
            Section(stringResource(Res.string.next_door), Glyph.Arrows) {
                // A small town's deal can be well under a megawatt.
                @Composable
                fun power(kw: Int) = if (kw < 1000) stringResource(Res.string.kilowatts, groupThousands(kw.toLong())) else mw(kw.toLong())
                @Composable
                fun people(n: Int) = pluralStringResource(Res.plurals.water_for, n, groupThousands(n.toLong()))
                @Composable
                fun tonnes(n: Int) = stringResource(Res.string.tonnes, groupThousands(n.toLong()))
                StatGrid(
                    listOfNotNull(
                        if (s.powerIn > 0) StatItem(Glyph.Bolt, stringResource(Res.string.power_bought), power(s.powerIn)) else null,
                        if (s.powerOut > 0) StatItem(Glyph.Bolt, stringResource(Res.string.power_sold), power(s.powerOut)) else null,
                        if (s.waterIn > 0) StatItem(Glyph.Drop, stringResource(Res.string.water_bought), people(s.waterIn)) else null,
                        if (s.waterOut > 0) StatItem(Glyph.Drop, stringResource(Res.string.water_sold), people(s.waterOut)) else null,
                        if (s.garbageOut > 0) StatItem(Glyph.Bin, stringResource(Res.string.garbage_sent), tonnes(s.garbageOut)) else null,
                        if (s.garbageIn > 0) StatItem(Glyph.Bin, stringResource(Res.string.garbage_taken_in), tonnes(s.garbageIn)) else null,
                    ),
                )
            }
        }
        // What the town's businesses trade. The town's own money is above.
        if (s.exportValue > 0 || s.importValue > 0) {
            Section(stringResource(Res.string.trade), Glyph.Crate) {
                val from = s.fromNeighbours.sum()
                val to = s.toNeighbours.sum()
                StatGrid(
                    listOfNotNull(
                        StatItem(Glyph.Arrows, stringResource(Res.string.trade_out), moneyText(s.exportValue)),
                        StatItem(Glyph.Arrows, stringResource(Res.string.trade_in), moneyText(s.importValue)),
                        // Over the border, in loads a month.
                        if (to > 0) StatItem(Glyph.Crate, stringResource(Res.string.goods_to_neighbours), pluralStringResource(Res.plurals.loads_a_month, to, groupThousands(to.toLong()))) else null,
                        if (from > 0) StatItem(Glyph.Crate, stringResource(Res.string.goods_from_neighbours), pluralStringResource(Res.plurals.loads_a_month, from, groupThousands(from.toLong()))) else null,
                    ),
                )
            }
        }
    }
}

/**
 * What each zone wants and why: how much is wanted or how much too much, the
 * parts that make it up, the zone's tax, and anything holding it back, then
 * the town's work and homes. Opened from the demand bars.
 */
@Composable
fun DemandWindow(game: GameState, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    val s = city.stats
    fun n(v: Int) = groupThousands(v.toLong())
    Window(Res.string.demand, onClose, Glyph.Zone, help = "zones-and-growth") {
        for (z in city.demandParts()) {
            if (!city.allowsZone(z.zone)) continue
            val kind = ZoneKind.entries.first { it.zone == z.zone }
            val homes = z.zone == com.rm.infill.sim.Zone.RESIDENTIAL
            @Composable
            fun amount(v: Int) = if (homes) pluralStringResource(Res.plurals.count_people, kotlin.math.abs(v), n(kotlin.math.abs(v))) else pluralStringResource(Res.plurals.count_jobs, kotlin.math.abs(v), n(kotlin.math.abs(v)))
            Section(stringResource(kind.title)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarkIcon(ZoneMark(z.zone))
                    Text(
                        when {
                            z.total > 0 -> stringResource(Res.string.demand_wanted, amount(z.total))
                            z.total < 0 -> stringResource(Res.string.demand_too_much, amount(z.total))
                            else -> stringResource(Res.string.demand_steady)
                        },
                        color = when {
                            z.total > 0 -> c.good
                            z.total < 0 -> c.bad
                            else -> c.textDim
                        },
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                // The sum, a line a part, what calls for more and what takes it away.
                Column(Modifier.semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (p in z.parts) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(stringResource(demandSourceName(p.source)), color = c.textDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(
                                if (p.amount > 0) "+${n(p.amount)}" else "\u2212${n(-p.amount)}",
                                color = c.text, fontSize = 13.sp,
                            )
                        }
                    }
                }
                val tax = stringResource(Res.string.tax)
                when (z.zone) {
                    com.rm.infill.sim.Zone.RESIDENTIAL ->
                        Stepper(GlyphMark(Glyph.Coins), tax, city.residentialTax, 1) { game.setTaxes(r = (city.residentialTax + it).coerceIn(0, city.maxTax())) }
                    com.rm.infill.sim.Zone.COMMERCIAL, com.rm.infill.sim.Zone.OFFICE ->
                        Stepper(GlyphMark(Glyph.Coins), tax, city.commercialTax, 1) { game.setTaxes(c = (city.commercialTax + it).coerceIn(0, city.maxTax())) }
                    else ->
                        Stepper(GlyphMark(Glyph.Coins), tax, city.industrialTax, 1) { game.setTaxes(i = (city.industrialTax + it).coerceIn(0, city.maxTax())) }
                }
                // Shops and offices share a rate, and so do works and farms.
                when (z.zone) {
                    com.rm.infill.sim.Zone.COMMERCIAL -> Res.string.tax_shared_offices
                    com.rm.infill.sim.Zone.OFFICE -> Res.string.tax_shared_shops
                    com.rm.infill.sim.Zone.INDUSTRIAL -> Res.string.tax_shared_farms
                    com.rm.infill.sim.Zone.FARMLAND -> Res.string.tax_shared_works
                    else -> null
                }?.let { Text(stringResource(it), color = c.textDim, fontSize = 12.sp) }
                // What's stopping it growing, as the advice line has it.
                for (a in city.advice.filter { it.zone == z.zone }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlyphIcon(adviceGlyph(a.kind), c.warn, Modifier.size(16.dp))
                        Text(adviceText(a), color = c.text, fontSize = 13.sp)
                    }
                }
                if (homes && city.needsWayIn()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlyphIcon(Glyph.Road, c.warn, Modifier.size(16.dp))
                        Text(stringResource(Res.string.advice_no_way_in), color = c.text, fontSize = 13.sp)
                    }
                }
            }
        }
        if (city.allowsZone(com.rm.infill.sim.Zone.MIXED)) {
            Section(stringResource(Res.string.zone_mixed)) {
                Text(stringResource(Res.string.demand_mixed), color = c.textDim, fontSize = 13.sp)
            }
        }
        Section(stringResource(Res.string.demand_town), Glyph.Person) {
            StatGrid(
                listOfNotNull(
                    StatItem(Glyph.Briefcase, stringResource(Res.string.label_unemployed), stringResource(Res.string.percent, s.unemployment), s.unemployment / 100f, when { s.unemployment >= 15 -> Tone.Bad; s.unemployment >= 7 -> Tone.Warn; else -> Tone.Good }),
                    StatItem(Glyph.Briefcase, stringResource(Res.string.jobs_spare), n(s.vacant)),
                    StatItem(Glyph.Tag, stringResource(Res.string.empty_homes), n(s.emptyHomes)),
                    StatItem(Glyph.Arrows, stringResource(Res.string.moved_in), n(s.movedIn)),
                    StatItem(Glyph.Road, stringResource(Res.string.way_in), stringResource(if (city.wayInNow) Res.string.way_in_open else Res.string.way_in_none), tone = if (city.wayInNow) Tone.Good else Tone.Bad),
                    // Parks, sport and culture near home, against what people expect in the year.
                    city.leisureExpected().let { e ->
                        StatItem(Glyph.Tree, stringResource(Res.string.label_leisure), stringResource(Res.string.leisure_value, s.leisure, e), s.leisure / 100f,
                            when { s.leisure >= e -> Tone.Good; s.leisure + 10 >= e -> Tone.Warn; else -> Tone.Bad })
                    },
                    if (s.commutersOut > 0) StatItem(Glyph.Arrows, stringResource(Res.string.commuting_out), n(s.commutersOut)) else null,
                    if (s.commutersIn > 0) StatItem(Glyph.Arrows, stringResource(Res.string.commuting_in), n(s.commutersIn)) else null,
                ),
            )
        }
    }
}

/** What a part of a zone's demand is called. */
private fun demandSourceName(source: com.rm.infill.sim.DemandSource) = when (source) {
    com.rm.infill.sim.DemandSource.WORKERS_NEEDED -> Res.string.demand_workers_needed
    com.rm.infill.sim.DemandSource.SETTLERS -> Res.string.demand_settlers
    com.rm.infill.sim.DemandSource.LIVING_HERE -> Res.string.demand_living_here
    com.rm.infill.sim.DemandSource.EMPTY_HOMES -> Res.string.demand_empty_homes
    com.rm.infill.sim.DemandSource.GOING_UP -> Res.string.demand_going_up
    com.rm.infill.sim.DemandSource.SPENDING -> Res.string.demand_spending
    com.rm.infill.sim.DemandSource.SHOPPERS_IN -> Res.string.shopping_in
    com.rm.infill.sim.DemandSource.SHOPPERS_OUT -> Res.string.shopping_out
    com.rm.infill.sim.DemandSource.JOBS_HERE -> Res.string.demand_jobs_here
    com.rm.infill.sim.DemandSource.MARKET -> Res.string.demand_market
    com.rm.infill.sim.DemandSource.BROUGHT_IN -> Res.string.demand_brought_in
    com.rm.infill.sim.DemandSource.TOWN_SIZE -> Res.string.demand_town_size
    com.rm.infill.sim.DemandSource.AIRPORTS -> Res.string.demand_airports
    com.rm.infill.sim.DemandSource.TAX -> Res.string.demand_tax
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
        StepButton("−", stringResource(Res.string.less_of, name)) { change(-step) }
        Text(
            value, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center,
        )
        StepButton("+", stringResource(Res.string.more_of, name)) { change(step) }
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
    Triple(Series.Carbon, Res.string.series_carbon, Glyph.Smoke),
    Triple(Series.Leisure, Res.string.series_leisure, Glyph.Tree),
    Triple(Series.Health, Res.string.series_health, Glyph.Cross),
    Triple(Series.Unemployment, Res.string.series_unemployment, Glyph.Briefcase),
    Triple(Series.Births, Res.string.series_births, Glyph.Plus),
    Triple(Series.Deaths, Res.string.series_deaths, Glyph.Hourglass),
    Triple(Series.Smog, Res.string.series_smog, Glyph.Smoke),
    Triple(Series.Approval, Res.string.series_approval, Glyph.Check),
)

/** The town over the years, one thing at a time. */
@Composable
fun GraphsWindow(game: GameState, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val history = game.city.history
    var series by remember { mutableStateOf(Series.Population) }
    // The last twenty years month by month, or every year since the start.
    var allYears by remember { mutableStateOf(false) }
    Window(Res.string.graphs, onClose, Glyph.Arrows, help = "the-towns-story") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (history.yearsKept >= 2) {
                Chips(listOf(false, true), allYears, {
                    if (it) stringResource(Res.string.graph_all, history.yearAt(0)) else stringResource(Res.string.graph_recent)
                }) { allYears = it }
            }
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
                                .selectable(selected = on, role = Role.Tab) { series = s }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GlyphIcon(glyph, if (on) c.onAccent else c.text, Modifier.size(18.dp))
                            Text(stringResource(name), color = if (on) c.onAccent else c.text, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
            val yearly = allYears && history.yearsKept >= 2
            if (history.count < 2) {
                Text(stringResource(Res.string.no_history), color = c.textDim, fontSize = 14.sp)
            } else {
                val values = if (yearly) history.yearValues(series) else history.values(series)
                val months = stringArrayResource(Res.array.month_short)
                // Where each point falls, in months since 1900, for the eras' lines.
                val at = if (yearly) LongArray(values.size) { history.yearAt(it) * 12L + 11 }
                else LongArray(values.size) { history.dateOf(it).let { (y, m) -> y * 12L + m } }
                val top = values.max()
                val money = series == Series.Funds || series == Series.Income || series == Series.Upkeep
                Row(Modifier.fillMaxWidth()) {
                    Text(shownText(top, money), color = c.textDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(shownText(values.last(), money), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                // Each era's arrival on the chart, with its name.
                val eras = game.city.chronicle.filter { it.event.kind == EventKind.EraArrived }.mapNotNull { t ->
                    val month = t.year * 12L + t.month
                    if (month <= at.first() || month > at.last()) null
                    else ((month - at.first()).toFloat() / (at.last() - at.first())) to stringResource(eraName(t.event.era ?: return@mapNotNull null))
                }
                LineChart(values, c.accent, c.chromeEdge, Modifier.fillMaxWidth().height(180.dp), eras, c.textDim)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (yearly) {
                        Text(history.yearAt(0).toString(), color = c.textDim, fontSize = 12.sp)
                        Text(history.yearAt(history.yearsKept - 1).toString(), color = c.textDim, fontSize = 12.sp)
                    } else {
                        val (y0, m0) = history.dateOf(0)
                        val (y1, m1) = history.dateOf(history.count - 1)
                        Text(stringResource(Res.string.date, months.getOrElse(m0) { "" }, y0), color = c.textDim, fontSize = 12.sp)
                        Text(stringResource(Res.string.date, months.getOrElse(m1) { "" }, y1), color = c.textDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun shownText(v: Long, money: Boolean) = if (money) moneyText(v) else groupThousands(v)

/** A line over time from zero up to the highest value, with a soft fill under it, and [marks] across it: a share of the way and a name. */
@Composable
private fun LineChart(values: LongArray, line: Color, grid: Color, modifier: Modifier, marks: List<Pair<Float, String>> = emptyList(), markColour: Color = grid) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    Canvas(modifier) {
        for ((share, name) in marks) {
            val x = size.width * share
            drawLine(markColour, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            drawText(measurer, name, Offset(x + 3.dp.toPx(), 2.dp.toPx()), androidx.compose.ui.text.TextStyle(color = markColour, fontSize = 10.sp))
        }
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

/** Colours for where carbon comes from: power, traffic, works and heating. */
private val CARBON_COLOURS = listOf(Color(0xFF5A5A60), Color(0xFF8A8F98), Color(0xFF8A5A2A), Color(0xFFD8402F))

/** Colours for the ways visitors come: road, rail, sea and air. */
private val VISITOR_COLOURS = listOf(Color(0xFF8A8F98), Color(0xFF8E44AD), Color(0xFF2F6FD8), Color(0xFF16A2A2))

/** Who lives in the town, the work they're schooled for, places at school and with a doctor, and how justice is doing. */
@Composable
fun PeopleWindow(game: GameState, onGraphs: () -> Unit, onOpinion: () -> Unit, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val s = game.city.stats
    fun n(v: Int) = groupThousands(v.toLong())
    Window(Res.string.people, onClose, Glyph.Person, help = "people") {
        StatGrid(
            listOfNotNull(
                StatItem(Glyph.Person, stringResource(Res.string.population), n(s.population)),
                StatItem(Glyph.Check, stringResource(Res.string.approval), stringResource(Res.string.percent, game.city.approval), game.city.approval / 100f, toneOf(game.city.approval, 60, 40)),
                StatItem(Glyph.Cross, stringResource(Res.string.health), healthWord(s.health).replaceFirstChar { it.uppercase() }, s.health / 100f, toneOf(s.health, 65, 45)),
                StatItem(Glyph.Briefcase, stringResource(Res.string.label_unemployed), stringResource(Res.string.percent, s.unemployment), s.unemployment / 100f, when { s.unemployment >= 15 -> Tone.Bad; s.unemployment >= 7 -> Tone.Warn; else -> Tone.Good }),
                if (s.commute > 0) StatItem(Glyph.Car, stringResource(Res.string.label_commute), stringResource(Res.string.value_minutes, s.commute)) else null,
                // Over the border to the neighbours and back, for a town in a region.
                if (s.commutersOut > 0) StatItem(Glyph.Arrows, stringResource(Res.string.commuting_out), n(s.commutersOut)) else null,
                if (s.commutersIn > 0) StatItem(Glyph.Arrows, stringResource(Res.string.commuting_in), n(s.commutersIn)) else null,
                // Spending over the border, as the shoppers it takes.
                if (s.shoppingOut > 0) StatItem(Glyph.Crate, stringResource(Res.string.shopping_out), n((s.shoppingOut * com.rm.infill.sim.Balance.RESIDENTS_PER_SHOP_JOB).toInt())) else null,
                if (s.shoppingIn > 0) StatItem(Glyph.Crate, stringResource(Res.string.shopping_in), n((s.shoppingIn * com.rm.infill.sim.Balance.RESIDENTS_PER_SHOP_JOB).toInt())) else null,
                StatItem(Glyph.Lights, stringResource(Res.string.traffic_flow), stringResource(Res.string.percent, s.flow), s.flow / 100f, toneOf(s.flow, 80, 50)),
                if (s.emptyHomes > 0) StatItem(Glyph.Tag, stringResource(Res.string.empty_homes), n(s.emptyHomes)) else null,
            ),
        )
        Actions(listOf(ActionItem(Glyph.Check, stringResource(Res.string.opinion), onClick = onOpinion), ActionItem(Glyph.Arrows, stringResource(Res.string.graphs), onClick = onGraphs)))
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
                        Text(stringResource(Res.string.list_join, pluralStringResource(Res.plurals.count_workers, s.workersBy[k], n(s.workersBy[k])), pluralStringResource(Res.plurals.count_jobs, s.jobsBy[k], n(s.jobsBy[k]))), color = if (short) c.bad else c.textDim, fontSize = 12.sp)
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
        Section(stringResource(Res.string.climate), Glyph.Heat) {
            val city = game.city
            val warming = city.warming
            StatGrid(
                listOfNotNull(
                    StatItem(Glyph.Heat, stringResource(Res.string.label_warming), stringResource(Res.string.value_degrees, tenths(warming)), minOf(1f, warming / 30f), toneOf(30 - warming, 20, 10)),
                    StatItem(Glyph.Flame, stringResource(Res.string.label_heat_waves), n(city.heatWavesLastYear)),
                    StatItem(Glyph.Rain, stringResource(Res.string.label_floods), n(city.floodsLastYear)),
                    StatItem(Glyph.Smoke, stringResource(Res.string.label_carbon), stringResource(Res.string.value_tonnes, groupThousands(s.carbon)), wide = true),
                ),
            )
            if (s.carbon > 0) BarWithKey(
                listOf(
                    Triple(stringResource(Res.string.carbon_power), (s.carbonPower * 100 / s.carbon).toInt(), CARBON_COLOURS[0]),
                    Triple(stringResource(Res.string.carbon_traffic), (s.carbonTraffic * 100 / s.carbon).toInt(), CARBON_COLOURS[1]),
                    Triple(stringResource(Res.string.carbon_works), (s.carbonWorks * 100 / s.carbon).toInt(), CARBON_COLOURS[2]),
                    Triple(stringResource(Res.string.carbon_heating), (s.carbonHeating * 100 / s.carbon).toInt(), CARBON_COLOURS[3]),
                ).filter { it.second > 0 },
                percent = true,
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
fun EraWindow(game: GameState, era: Era, onChronicle: () -> Unit, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    Window(eraName(era), onClose, Glyph.Calendar, help = "eras") {
        Text(stringResource(eraLine(era)), color = c.text, fontSize = 15.sp)
        // The town as the era began, beside how it was when the one before it did.
        val shots = city.snapshots
        val now = shots.lastOrNull { it.era == era }
        val then = now?.let { n -> shots.getOrNull(shots.indexOf(n) - 1) }
        if (now != null) {
            Section(stringResource(Res.string.era_then_and_now), Glyph.Calendar) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (shot in listOfNotNull(then, now)) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TownPicture(shot.tiles, city.map.width, Modifier.fillMaxWidth())
                            Text(shot.year.toString(), color = c.textDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        TextButton(stringResource(Res.string.chronicle), false, onChronicle)
        // What it brings: in the township, only what comes after the start.
        fun brought(year: Int, of: Era = Era.of(year)) = of == era && (era != Era.TOWNSHIP || year > Era.TOWNSHIP.year)
        val brings = buildList {
            for (t in RoadType.entries) if (t != RoadType.RAMP && brought(t.year)) add(stringResource(roadName(t)) to roadIcon(t))
            for (t in BuildingType.entries) if (brought(t.year, t.era)) add(stringResource(buildingName(t)) to buildingIcon(t))
            for (m in Material.entries) if (brought(m.year)) add(stringResource(materialName(m)) to ChoiceIcon(glyph = Glyph.Pipe, glyphColour = PIPE_COLOURS[m.pipe]))
            if (Era.of(Balance.TROLLEYBUS_YEAR) == era) add(stringResource(Res.string.trolley_wire) to ChoiceIcon(intArrayOf(com.rm.infill.map.Atlas.ROAD_STREET + 10, com.rm.infill.map.Atlas.TROLLEY_WIRE + 10)))
            // Districts, offices and homes over shops with the streetcar, towers with the motor age.
            if (era == Era.STREETCAR) {
                add(stringResource(Res.string.districts) to ChoiceIcon(glyph = Glyph.District))
                add(stringResource(Res.string.zone_mixed) to ChoiceIcon(buildingIcon(BuildingType.SHOPHOUSE).sprites, back = zoneColour(com.rm.infill.sim.Zone.MIXED)))
                add(stringResource(Res.string.zone_office) to ChoiceIcon(buildingIcon(BuildingType.OFFICES).sprites, back = zoneColour(com.rm.infill.sim.Zone.OFFICE)))
            }
            if (era == Era.MOTOR) add(stringResource(Res.string.density_tower) to ChoiceIcon(glyph = Glyph.Tower))
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
    GoalKind.People -> pluralStringResource(Res.plurals.goal_people, goal.need, groupThousands(goal.need.toLong()))
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
    GoalKind.LowCarbon -> stringResource(Res.string.goal_low_carbon, Balance.FUTURE_CARBON.toInt())
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
                stringResource(Res.string.list_join, stringResource(if (tram) Res.string.tram_line else Res.string.bus_line), pluralStringResource(Res.plurals.line_stops, stops, stops)),
                color = c.text, fontSize = 14.sp,
            )
            if (stops > 0) TextButton(stringResource(Res.string.clear), false, onClear)
            if (stops >= 2) TextButton(stringResource(Res.string.make_line), true, onMake)
        }
    }
}

/** Asks whether a road reaching the edge of the map leads out of town, or stays in it. */
@Composable
fun EdgeLinkWindow(onLink: () -> Unit, onKeep: () -> Unit, onClose: () -> Unit) {
    val c = Infill.colors
    Window(Res.string.edge_link_title, onClose, Glyph.Road) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.edge_link_what), color = c.textDim, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(stringResource(Res.string.edge_link_yes), true, onLink)
                TextButton(stringResource(Res.string.edge_link_no), false, onKeep)
            }
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
    Window(Res.string.lines, onClose, Glyph.Route, help = "transit-and-rail") {
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
    Window(Res.string.districts, onClose, Glyph.District, help = "districts") {
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
                f.worst?.let { worst ->
                    Text(stringResource(Res.string.district_mood, f.mood, stringResource(concernName(worst)).lowercase()), color = toneColour(toneOf(f.mood, 60, 40)), fontSize = 13.sp)
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
                    Chips(listOf(Density.NONE, Density.LOW, Density.MEDIUM, Density.HIGH), d.height, {
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
                    ) + listOfNotNull(
                        if (game.city.everything || game.city.year >= Balance.COOL_ROOF_YEAR) {
                            Triple(Glyph.Heat, Res.string.cool_roofs, d.coolRoofs) to { v: Boolean -> set { it.coolRoofs = v } }
                        } else null,
                        if (game.city.everything || game.city.year >= Balance.GREEN_ROOF_YEAR) {
                            Triple(Glyph.Tree, Res.string.green_roofs, d.greenRoofs) to { v: Boolean -> set { it.greenRoofs = v } }
                        } else null,
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
            .toggleable(value = on, role = Role.Switch) { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphIcon(glyph, if (on) c.onAccent else c.textDim, Modifier.size(18.dp))
        Text(label, color = if (on) c.onAccent else c.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** As many buttons as a window could have, to stop at the last one going round. */
private const val MAX_TABS = 400


/** A town-wide law's name and what it does. */
fun ordinanceName(o: Ordinance): StringResource = when (o) {
    Ordinance.BUILDING_CODE -> Res.string.law_building_code
    Ordinance.PUBLIC_HEALTH_ACT -> Res.string.law_public_health_act
    Ordinance.SMOKE_ABATEMENT -> Res.string.law_smoke_abatement
    Ordinance.LIQUOR_LICENCES -> Res.string.law_liquor_licences
    Ordinance.SUNDAY_CLOSING -> Res.string.law_sunday_closing
    Ordinance.YOUTH_CURFEW -> Res.string.law_youth_curfew
    Ordinance.TENEMENT_ACT -> Res.string.law_tenement_act
    Ordinance.DAYLIGHT_SAVING -> Res.string.law_daylight_saving
    Ordinance.PROHIBITION -> Res.string.law_prohibition
    Ordinance.SPEED_LIMITS -> Res.string.law_speed_limits
    Ordinance.PARKING_METERS -> Res.string.law_parking_meters
    Ordinance.SCHOOL_MEALS -> Res.string.law_school_meals
    Ordinance.FLUORIDATION -> Res.string.law_fluoridation
    Ordinance.DOG_LICENCES -> Res.string.law_dog_licences
    Ordinance.NOISE_BYLAW -> Res.string.law_noise_bylaw
    Ordinance.CLEAN_AIR_ACT -> Res.string.law_clean_air_act
    Ordinance.BOTTLE_DEPOSIT -> Res.string.law_bottle_deposit
    Ordinance.PERCENT_FOR_ART -> Res.string.law_percent_for_art
    Ordinance.TREE_PROTECTION -> Res.string.law_tree_protection
    Ordinance.ENERGY_CODE -> Res.string.law_energy_code
    Ordinance.CURBSIDE_RECYCLING -> Res.string.law_curbside_recycling
    Ordinance.LATE_LICENCES -> Res.string.law_late_licences
    Ordinance.HEAT_PLAN -> Res.string.law_heat_plan
    Ordinance.SMOKING_BAN -> Res.string.law_smoking_ban
    Ordinance.CONGESTION_CHARGE -> Res.string.law_congestion_charge
    Ordinance.CARBON_PRICE -> Res.string.law_carbon_price
}

fun ordinanceWhat(o: Ordinance): StringResource = when (o) {
    Ordinance.BUILDING_CODE -> Res.string.law_building_code_what
    Ordinance.PUBLIC_HEALTH_ACT -> Res.string.law_public_health_act_what
    Ordinance.SMOKE_ABATEMENT -> Res.string.law_smoke_abatement_what
    Ordinance.LIQUOR_LICENCES -> Res.string.law_liquor_licences_what
    Ordinance.SUNDAY_CLOSING -> Res.string.law_sunday_closing_what
    Ordinance.YOUTH_CURFEW -> Res.string.law_youth_curfew_what
    Ordinance.TENEMENT_ACT -> Res.string.law_tenement_act_what
    Ordinance.DAYLIGHT_SAVING -> Res.string.law_daylight_saving_what
    Ordinance.PROHIBITION -> Res.string.law_prohibition_what
    Ordinance.SPEED_LIMITS -> Res.string.law_speed_limits_what
    Ordinance.PARKING_METERS -> Res.string.law_parking_meters_what
    Ordinance.SCHOOL_MEALS -> Res.string.law_school_meals_what
    Ordinance.FLUORIDATION -> Res.string.law_fluoridation_what
    Ordinance.DOG_LICENCES -> Res.string.law_dog_licences_what
    Ordinance.NOISE_BYLAW -> Res.string.law_noise_bylaw_what
    Ordinance.CLEAN_AIR_ACT -> Res.string.law_clean_air_act_what
    Ordinance.BOTTLE_DEPOSIT -> Res.string.law_bottle_deposit_what
    Ordinance.PERCENT_FOR_ART -> Res.string.law_percent_for_art_what
    Ordinance.TREE_PROTECTION -> Res.string.law_tree_protection_what
    Ordinance.ENERGY_CODE -> Res.string.law_energy_code_what
    Ordinance.CURBSIDE_RECYCLING -> Res.string.law_curbside_recycling_what
    Ordinance.LATE_LICENCES -> Res.string.law_late_licences_what
    Ordinance.HEAT_PLAN -> Res.string.law_heat_plan_what
    Ordinance.SMOKING_BAN -> Res.string.law_smoking_ban_what
    Ordinance.CONGESTION_CHARGE -> Res.string.law_congestion_charge_what
    Ordinance.CARBON_PRICE -> Res.string.law_carbon_price_what
}

private fun topicName(t: Topic): StringResource = when (t) {
    Topic.SAFETY -> Res.string.topic_safety
    Topic.HEALTH -> Res.string.topic_health
    Topic.MORALS -> Res.string.topic_morals
    Topic.ENVIRONMENT -> Res.string.topic_environment
    Topic.TRAFFIC -> Res.string.topic_traffic
    Topic.WASTE -> Res.string.topic_waste
    Topic.ENERGY -> Res.string.topic_energy
    Topic.CULTURE -> Res.string.topic_culture
}

private fun topicGlyph(t: Topic): Glyph = when (t) {
    Topic.SAFETY -> Glyph.Flame
    Topic.HEALTH -> Glyph.Cross
    Topic.MORALS -> Glyph.Glass
    Topic.ENVIRONMENT -> Glyph.Smoke
    Topic.TRAFFIC -> Glyph.Car
    Topic.WASTE -> Glyph.Bin
    Topic.ENERGY -> Glyph.Bolt
    Topic.CULTURE -> Glyph.Tree
}

/**
 * The town's laws, by topic: each with what it does and what it costs a
 * month at the town's size now. Those whose years haven't come say when
 * they will; those history has ended are gone.
 */
@Composable
fun OrdinancesWindow(game: GameState, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    Window(Res.string.ordinances, onClose, Glyph.Gavel, help = "ordinances") {
        for (t in Topic.entries) {
            val laws = Ordinance.entries.filter { it.topic == t && (it.until == null || city.year <= it.until!!) }
            if (laws.isEmpty()) continue
            Section(stringResource(topicName(t)), topicGlyph(t)) {
                for (o in laws) {
                    val can = city.allows(o)
                    val on = city.passed(o)
                    val cost = o.cost(city.stats.population)
                    val label = stringResource(ordinanceName(o))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) c.accent else c.button)
                            .semantics(mergeDescendants = true) {}
                            .toggleable(value = on, enabled = can || on, role = Role.Switch) { game.setOrdinance(o, it) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = if (on) c.onAccent else if (can) c.text else c.textDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(ordinanceWhat(o)), color = if (on) c.onAccent else c.textDim, fontSize = 12.sp)
                        }
                        Text(
                            when {
                                // Past its year but waiting on its era.
                                !can && !on && city.year >= o.from -> stringResource(Res.string.law_era, stringResource(eraName(Era.of(o.from))))
                                !can && !on -> stringResource(Res.string.law_from, o.from)
                                cost == 0L -> stringResource(Res.string.law_free)
                                else -> stringResource(Res.string.law_cost, moneyText(cost))
                            },
                            color = if (on) c.onAccent else c.textDim, fontSize = 12.sp, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
