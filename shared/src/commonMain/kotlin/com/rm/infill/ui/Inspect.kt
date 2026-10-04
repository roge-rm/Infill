package com.rm.infill.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rm.infill.res.action_keep_in
import com.rm.infill.res.action_link_out
import com.rm.infill.res.pill_kept_in
import com.rm.infill.res.pill_leads_out
import com.rm.infill.res.label_hall_saves
import com.rm.infill.res.label_leisure
import com.rm.infill.res.leisure_value
import com.rm.infill.res.action_bring_up_to_date
import com.rm.infill.res.pill_newer_kind
import com.rm.infill.res.pill_nonconforming
import com.rm.infill.res.pill_upset
import com.rm.infill.res.need_internet
import com.rm.infill.res.need_phone
import com.rm.infill.res.need_water
import com.rm.infill.res.inspect_zone_mixed
import com.rm.infill.res.label_shop_jobs
import com.rm.infill.res.need_power
import com.rm.infill.res.pill_not_fitted
import com.rm.infill.res.pill_no_internet
import com.rm.infill.res.pill_no_phone
import com.rm.infill.res.pill_no_water
import com.rm.infill.res.label_working
import com.rm.infill.res.label_office_draw
import com.rm.infill.res.label_visitors
import com.rm.infill.GameState
import com.rm.infill.map.Atlas
import com.rm.infill.map.BuildingSprites
import com.rm.infill.res.pill_pumps_off
import com.rm.infill.res.pill_tunnel_shut
import com.rm.infill.res.road_tunnel
import com.rm.infill.res.rail_tunnel
import com.rm.infill.res.tunnel_cut_cover
import com.rm.infill.res.tunnel_underpass
import com.rm.infill.res.tunnel_under_water
import com.rm.infill.res.tunnel_portal
import com.rm.infill.res.label_for_ships
import com.rm.infill.res.action_shut
import com.rm.infill.res.action_open
import com.rm.infill.res.action_toll_on
import com.rm.infill.res.action_toll_off
import com.rm.infill.res.pill_toll
import com.rm.infill.res.pill_posted
import com.rm.infill.res.pill_shut
import com.rm.infill.res.pill_gale_shut
import com.rm.infill.res.pill_unsafe
import com.rm.infill.res.ships_blocked
import com.rm.infill.res.ships_it_opens
import com.rm.infill.res.ships_pass_under
import com.rm.infill.res.value_tiles
import com.rm.infill.res.label_span
import com.rm.infill.res.bridge_plain
import com.rm.infill.res.value_of
import com.rm.infill.res.label_guests
import com.rm.infill.res.label_ships
import com.rm.infill.res.label_port_loads
import com.rm.infill.res.pill_no_sea_route
import com.rm.infill.res.pill_open_sea
import com.rm.infill.res.*
import com.rm.infill.sim.Needs
import com.rm.infill.sim.Need
import com.rm.infill.sim.Bridge
import com.rm.infill.sim.Action
import com.rm.infill.sim.Ageing
import com.rm.infill.sim.Balance
import com.rm.infill.sim.Broken
import com.rm.infill.sim.Building
import com.rm.infill.sim.BuildingType
import com.rm.infill.sim.City
import com.rm.infill.sim.Education
import com.rm.infill.sim.Generation
import com.rm.infill.sim.Junction
import com.rm.infill.sim.Land
import com.rm.infill.sim.Material
import com.rm.infill.sim.Pipe
import com.rm.infill.sim.Power
import com.rm.infill.sim.Phone
import com.rm.infill.sim.Rail
import com.rm.infill.sim.Resource
import com.rm.infill.sim.RoadType
import com.rm.infill.sim.Stop
import com.rm.infill.sim.Terrain
import com.rm.infill.sim.Zone
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

/** What a card shows: its picture and name, how it is, its figures, and what can be done. */
private class Card(
    val icon: ChoiceIcon,
    val title: String,
    val subtitle: String?,
    val pills: List<PillItem>,
    val stats: List<StatItem>,
    val actions: List<ActionItem>,
)

/**
 * What's on a tile, as a card: the building or tile's own picture and name,
 * how it's doing in pills and meters, what can be done about it, and a strip
 * of how the land there is along the bottom.
 */
@Composable
fun InspectPanel(
    game: GameState,
    x: Int,
    y: Int,
    onClose: () -> Unit,
    onAction: (Action) -> Unit,
    onLines: () -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    game.revision
    val city = game.city
    val building = city.buildingAt(x, y)
    val card = if (building != null) buildingCard(city, building, onAction) else tileCard(city, x, y, onAction, onLines)
    // Screen readers hear the card's name when it opens.
    ChromeBox(modifier.semantics { paneTitle = card.title }) {
        BoxWithConstraints {
            val columns = if (maxWidth >= 440.dp) 3 else 2
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardHeader(card.icon, card.title, card.subtitle, onClose)
                // The figures scroll only if a short screen can't fit them all.
                Column(
                    Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Pills(card.pills)
                    StatGrid(card.stats, columns)
                    Actions(card.actions)
                }
                Divider()
                LandStrip(city, x, y)
            }
        }
    }
}

/** Along the bottom: land value, crime and its kinds, pollution, then the district and the tile. */
/**
 * What's on a tile in a few words, for a screen reader at the cursor: where
 * it is, what's there and how it is, from the same card Inspect shows.
 */
@Composable
fun tileSummary(game: GameState, x: Int, y: Int): String {
    game.revision
    val city = game.city
    val building = city.buildingAt(x, y)
    val card = if (building != null) buildingCard(city, building, {}) else tileCard(city, x, y, {}, {})
    val where = stringResource(Res.string.tile_at, x, y)
    return listText(listOfNotNull(where, card.title, card.subtitle) + card.pills.map { it.text })
}

@Composable
private fun LandStrip(city: City, x: Int, y: Int) {
    val c = Infill.colors
    val map = city.map
    val i = map.index(x, y)
    fun v(layer: ByteArray) = layer[i].toInt() and 0xff
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (map.terrain[i] != Terrain.WATER) {
            MiniMeter(Glyph.Coin, stringResource(Res.string.label_land_value), v(map.landValue), highIsBad = false)
            MiniMeter(Glyph.Cuffs, stringResource(Res.string.label_crime), v(map.crime), highIsBad = true)
            if (v(map.theft) >= 8) MiniMeter(Glyph.Sack, stringResource(Res.string.label_theft), v(map.theft), highIsBad = true)
            if (v(map.vice) >= 8) MiniMeter(Glyph.Glass, stringResource(Res.string.label_vice), v(map.vice), highIsBad = true)
            if (v(map.rackets) >= 8) MiniMeter(Glyph.Hat, stringResource(Res.string.label_rackets), v(map.rackets), highIsBad = true)
            MiniMeter(Glyph.Smoke, stringResource(Res.string.label_pollution), v(map.pollution), highIsBad = true)
            if (v(map.comms) > 0) MiniMeter(Glyph.Phone, stringResource(Res.string.overlay_comms), v(map.comms) * 85, highIsBad = false)
        }
        Spacer(Modifier.weight(1f))
        Text("$x, $y", color = c.textDim, fontSize = Type.caption, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun buildingCard(city: City, b: Building, onAction: (Action) -> Unit): Card {
    val c = Infill.colors
    val map = city.map
    val t = b.type
    val i = map.index(b.x, b.y)
    val h = b.people
    val built = b.underway == 0
    val pills = ArrayList<PillItem>()
    val stats = ArrayList<StatItem>()
    val actions = ArrayList<ActionItem>()

    // The line under the name: when it was built, how big, how dense, and the district.
    val subtitle = listOfNotNull(
        if (built && t != BuildingType.PARK) stringResource(Res.string.value_built, yearOf(b.built)) else null,
        if (t.width > 1 || t.height > 1) "${t.width}×${t.height}" else null,
        if (t.zone != Zone.NONE && t.zone != Zone.FARMLAND) densityName(map.density[i])?.let { stringResource(it) } else null,
        city.districtAt(i)?.name,
    ).joinToString(" · ").ifEmpty { null }

    // How it is.
    if (b.underway > 0) pills += PillItem(Glyph.Hourglass, pluralStringResource(Res.plurals.going_up, b.underway, b.underway), Tone.Warn)
    if (h != null && h.empty) pills += PillItem(
        Glyph.Tag,
        if (h.forSale == 0) stringResource(Res.string.for_sale) else pluralStringResource(Res.plurals.for_sale_months, h.forSale, h.forSale),
        Tone.Warn,
    )
    if (b.burning > 0) pills += PillItem(Glyph.Flame, stringResource(Res.string.on_fire), Tone.Bad)
    if (b.outage > 0 && t.service && b.built >= city.monthNow - 1) pills += PillItem(Glyph.Wrench, pluralStringResource(Res.plurals.renovating, b.outage, b.outage), Tone.Warn)
    else if (b.outage > 0) pills += PillItem(Glyph.Wrench, pluralStringResource(Res.plurals.broken_down, b.outage, b.outage), Tone.Bad)
    if ((map.flood[i].toInt() and 0xff) >= Balance.FLOODED) {
        val shut = t.zone == Zone.COMMERCIAL || t.zone == Zone.INDUSTRIAL || t.zone == Zone.OFFICE || t.zone == Zone.MIXED
        pills += PillItem(Glyph.Rain, stringResource(if (shut) Res.string.inspect_shut else Res.string.inspect_flooded), Tone.Bad)
    } else if ((map.floodMemory[i].toInt() and 0xff) >= FLOODED_BEFORE) {
        pills += PillItem(Glyph.Rain, stringResource(Res.string.inspect_flooded_before), Tone.Warn)
    }
    val wantsPower = t.needsPower || t == BuildingType.TRAM_DEPOT || t == BuildingType.SUBWAY_STATION
    val unmet = if (built) city.unmet(b) else emptyList()
    if (built && wantsPower && !Generation.station(t) && !map.powered[i] && unmet.none { it.first == Need.POWER }) pills += PillItem(Glyph.Bolt, stringResource(Res.string.no_power), Tone.Bad)
    // What a city-run building needs and hasn't got, and what it has outside but isn't fitted for.
    for ((need, renovate) in unmet) if (!renovate) pills += PillItem(needGlyph(need), stringResource(needMissing(need)), Tone.Bad)
    // Outside, but not fitted for it until it's renovated.
    for ((need, renovate) in unmet) if (renovate) pills += PillItem(needGlyph(need), stringResource(Res.string.pill_not_fitted, stringResource(needName(need))), Tone.Warn)
    if (b.uncollected) pills += PillItem(Glyph.Bin, stringResource(Res.string.inspect_garbage), Tone.Warn)
    if (t.zone == Zone.COMMERCIAL && !t.office && built && city.shortOfStock(b)) pills += PillItem(Glyph.Crate, stringResource(Res.string.pill_short_of_stock), Tone.Warn)
    if (t.railway) {
        if (!city.reachable(b)) pills += PillItem(Glyph.Road, stringResource(Res.string.pill_no_road), Tone.Bad)
        pills += if (city.railLinked(b)) PillItem(Glyph.Rail, stringResource(Res.string.inspect_linked), Tone.Good)
        else PillItem(Glyph.Rail, stringResource(Res.string.inspect_not_linked), Tone.Warn)
    }
    if (t.port) {
        if (!city.reachable(b)) pills += PillItem(Glyph.Road, stringResource(Res.string.pill_no_road), Tone.Bad)
        if (!city.portLinked(b)) pills += PillItem(Glyph.Ship, stringResource(Res.string.pill_no_sea_route), Tone.Bad)
        else if (city.working(b)) pills += PillItem(Glyph.Ship, stringResource(Res.string.pill_open_sea), Tone.Good)
    }
    if (b.scrubbed) pills += PillItem(Glyph.Scrubber, stringResource(Res.string.inspect_scrubbed), Tone.Good)
    if (city.isHeritage(b)) pills += PillItem(Glyph.Star, stringResource(Res.string.heritage), Tone.Good)
    // Rezoned under it: it comes down once it's old.
    if (t.zone != Zone.NONE && !city.conforms(b)) pills += PillItem(Glyph.Zone, stringResource(Res.string.pill_nonconforming), Tone.Warn)
    if (h != null && (map.upset[i].toInt() and 0xff) >= UPSET_SHOWN) pills += PillItem(Glyph.Person, stringResource(Res.string.pill_upset), Tone.Warn)

    // Who lives there.
    if (h != null && !h.empty) {
        val ages = listOf(h.children to AGE_COLOURS[0], h.adults to AGE_COLOURS[1], h.elderly to AGE_COLOURS[2])
        stats += StatItem(
            Glyph.Person, stringResource(Res.string.label_residents),
            stringResource(Res.string.count_then, h.size, stringResource(Res.string.value_ages, h.children, h.adults, h.elderly)), parts = ages, wide = true,
        )
        stats += StatItem(Glyph.Coins, stringResource(Res.string.label_wealth), stringResource(wealthName(h.wealth)))
        stats += StatItem(Glyph.Cross, stringResource(Res.string.label_health), healthWord(h.health).replaceFirstChar { it.uppercase() }, h.health / 100f, toneOf(h.health, 65, 45))
        // Parks, sport and culture near home, against what people expect in the year.
        val leisure = city.leisureAt(i)
        val expected = city.leisureExpected()
        stats += StatItem(Glyph.Tree, stringResource(Res.string.label_leisure), stringResource(Res.string.leisure_value, leisure, expected), leisure / 100f,
            when { leisure >= expected -> Tone.Good; leisure + 10 >= expected -> Tone.Warn; else -> Tone.Bad })
        if (h.children > 0) stats += StatItem(Glyph.Cap, stringResource(Res.string.label_schooling), stringResource(Res.string.percent, h.schooling), h.schooling / 100f, toneOf(h.schooling))
        if (h.adults > 0) {
            val schooled = (h.schooled[Education.SCHOOLED] + h.schooled[Education.EDUCATED]) * 100 / h.adults
            stats += StatItem(Glyph.Briefcase, stringResource(Res.string.label_adults_schooled), stringResource(Res.string.percent, schooled), schooled / 100f, toneOf(schooled, 60, 30))
        }
    }
    // The shops under homes over shops; anything else that isn't a home is its jobs.
    if (t.jobs > 0) stats += StatItem(Glyph.Crate, stringResource(Res.string.label_shop_jobs), groupThousands(t.jobs.toLong()))
    else if (h == null && t.capacity > 0 && !t.green) {
        stats += StatItem(Glyph.Briefcase, stringResource(Res.string.label_jobs), groupThousands(t.capacity.toLong()))
    }

    // A service: staff, wear, and how full it is.
    if ((t.service || t.leisure || t == BuildingType.EXCHANGE) && !t.green && built) {
        val staffed = city.staffed(t)
        stats += StatItem(Glyph.Person, stringResource(Res.string.label_staffed), stringResource(Res.string.percent, staffed), staffed / 100f, toneOf(staffed, 90, 60))
        if (t.life > 0 || unmet.isNotEmpty()) {
            val condition = city.condition(b)
            stats += StatItem(Glyph.Wrench, stringResource(Res.string.label_condition), stringResource(Res.string.percent, condition), condition / 100f, toneOf(condition, 100, 80))
        }
        val room = b.room
        val label = when {
            t == BuildingType.EXCHANGE -> Res.string.label_phone_lines
            t.school -> Res.string.label_pupils
            t.health -> Res.string.label_patients
            t == BuildingType.COURTHOUSE -> Res.string.label_cases
            t == BuildingType.JAIL -> Res.string.label_prisoners
            else -> null
        }
        if (label != null && room > 0) {
            val taken = b.served * 100 / room
            stats += StatItem(
                Glyph.Person, stringResource(label),
                stringResource(Res.string.value_of, groupThousands(b.served.toLong()), groupThousands(room.toLong())),
                min(1f, b.served / room.toFloat()),
                when { taken > 100 -> Tone.Bad; taken > 90 -> Tone.Warn; else -> Tone.Good },
            )
        }
        if (t.patrols) stats += StatItem(Glyph.Cuffs, stringResource(Res.string.label_arrests), b.served.toString())
        if (t.root == BuildingType.TOWN_HALL) stats += StatItem(Glyph.Gavel, stringResource(Res.string.label_hall_saves), stringResource(Res.string.percent, city.hallSaves()))
    }

    // What it makes, and from where.
    // Why a station on the weather is making little.
    if (built && city.stationAvailable(b) == 0) {
        when (if (Generation.storage(t)) BuildingType.BATTERY else t.root) {
            BuildingType.WIND_FARM -> pills += PillItem(
                Glyph.Warn, stringResource(if (city.weather.windSpeed >= com.rm.infill.sim.Weather.GALE) Res.string.pill_gale else Res.string.pill_calm), Tone.Warn,
            )
            BuildingType.SOLAR_FARM -> pills += PillItem(Glyph.Warn, stringResource(Res.string.pill_dark), Tone.Plain)
            BuildingType.BATTERY -> pills += PillItem(Glyph.Warn, stringResource(Res.string.pill_flat), Tone.Warn)
            BuildingType.OFFSHORE_WIND -> pills += PillItem(
                Glyph.Warn, stringResource(if (city.weather.windSpeed >= com.rm.infill.sim.Weather.GALE) Res.string.pill_gale else Res.string.pill_calm), Tone.Warn,
            )
            else -> {}
        }
    }
    if (Generation.station(t)) {
        val made = city.stationOutput(b)
        val could = city.stationAvailable(b)
        stats += StatItem(
            Glyph.Bolt, stringResource(Res.string.label_making),
            stringResource(Res.string.value_of, stringResource(Res.string.megawatts, megawatts(made)), stringResource(Res.string.megawatts, megawatts(could))),
            if (could > 0) made / could.toFloat() else 0f, Tone.Plain, wide = true,
        )
        if ((t.root == BuildingType.COAL_PLANT || t.root == BuildingType.OIL_PLANT) && made > 0) {
            stats += StatItem(Glyph.Target, stringResource(Res.string.label_from_town), stringResource(Res.string.percent, b.local), b.local / 100f, Tone.Plain)
        }
    }
    val kind = b.worksKind
    val land = Land.output(t)
    if (kind != null) {
        val inputs = kind.inputs.map { stringResource(goodName(it.first)) }
        val from = if (inputs.size == 2) stringResource(Res.string.and_also, inputs[0], inputs[1]) else inputs[0]
        stats += StatItem(Glyph.Crate, stringResource(Res.string.label_makes), stringResource(Res.string.inspect_makes_from, stringResource(goodName(kind.output)), from), wide = true)
        if (built) stats += StatItem(Glyph.Target, stringResource(Res.string.label_from_town), stringResource(Res.string.percent, b.local), b.local / 100f, Tone.Plain)
    } else if (land != null) {
        stats += StatItem(Glyph.Crate, stringResource(Res.string.label_makes), stringResource(goodName(land.first)))
        if (built) stats += StatItem(Glyph.Target, stringResource(Res.string.label_sold_in_town), stringResource(Res.string.percent, b.local), b.local / 100f, Tone.Plain)
    } else if (t.zone == Zone.COMMERCIAL && !t.office && built) {
        stats += StatItem(Glyph.Crate, stringResource(Res.string.label_stock), stringResource(Res.string.percent, b.local), b.local / 100f, if (city.shortOfStock(b)) Tone.Bad else Tone.Plain)
    }
    if (t.root == BuildingType.DUMP) {
        val full = (b.fill.toLong() * 100 / city.dumpRoom(b)).toInt()
        stats += StatItem(Glyph.Bin, stringResource(Res.string.label_full), stringResource(Res.string.percent, full), full / 100f, when { full >= 90 -> Tone.Bad; full >= 70 -> Tone.Warn; else -> Tone.Good })
    }
    if (t.airport && built) {
        if (!city.reachable(b)) pills += PillItem(Glyph.Road, stringResource(Res.string.pill_no_road), Tone.Bad)
        stats += StatItem(Glyph.Suitcase, stringResource(Res.string.label_visitors), groupThousands(Balance.AIR_VISITORS[t.airTier].toLong()))
        stats += StatItem(Glyph.Briefcase, stringResource(Res.string.label_office_draw), groupThousands(Balance.AIR_OFFICES[t.airTier].toLong()))
    }
    if (t.port && built) {
        val loads = city.portLoads(b)
        stats += StatItem(Glyph.Crate, stringResource(Res.string.label_port_loads), groupThousands(loads.toLong()))
        stats += StatItem(Glyph.Ship, stringResource(Res.string.label_ships), groupThousands(((loads + Balance.SHIP_LOAD - 1) / Balance.SHIP_LOAD).toLong()))
    }
    if (t.hotel && built && b.room > 0) {
        val share = b.served * 100 / b.room
        stats += StatItem(Glyph.Suitcase, stringResource(Res.string.label_guests), stringResource(Res.string.value_of, groupThousands(b.served.toLong()), groupThousands(b.room.toLong())), share / 100f, toneOf(share, 60, 20))
    }
    if (t.railway) {
        if (t.station) stats += StatItem(Glyph.Person, stringResource(Res.string.label_riders), groupThousands(city.riders(b).toLong()))
        else stats += StatItem(Glyph.Crate, stringResource(Res.string.label_freight), groupThousands(city.freightSent(b).toLong()))
    }

    // Getting to work, and the mains, in town.
    val commute = if (!built || h?.empty == true) 0 else map.commute[i].toInt() and 0xff
    when (commute) {
        0 -> {}
        255 -> stats += StatItem(Glyph.Car, stringResource(Res.string.label_commute), stringResource(Res.string.value_out_of_reach), 0f, Tone.Bad)
        else -> {
            val minutes = maxOf(1, (commute - 1) / 2)
            stats += StatItem(Glyph.Car, stringResource(Res.string.label_commute), stringResource(Res.string.value_minutes, minutes))
        }
    }
    if (t.zone != Zone.NONE && t.zone != Zone.FARMLAND) {
        pills += PillItem(Glyph.Drop, stringResource(if (map.watered[i]) Res.string.value_mains else Res.string.value_well), if (map.watered[i]) Tone.Good else Tone.Plain)
        pills += PillItem(Glyph.Manhole, stringResource(if (map.sewered[i]) Res.string.value_sewer else Res.string.value_septic), if (map.sewered[i]) Tone.Good else Tone.Plain)
    }

    // What can be done: renovate a worn service, fit scrubbers, pull it down.
    // How well a station, port or airport works for what it needs.
    if (!t.service && t != BuildingType.EXCHANGE && unmet.isNotEmpty()) {
        val fit = city.fit(b)
        stats += StatItem(Glyph.Wrench, stringResource(Res.string.label_working), stringResource(Res.string.percent, fit), fit / 100f, toneOf(fit, 100, Needs.WORKING))
    }
    if (city.renovatable(b)) {
        val renew = Action.RenewArea(b.x, b.y, b.x, b.y)
        val plan = city.plan(renew)
        // An older kind is brought up to date as the newest; anything else is made good as it is.
        val newer = city.outdated(b)
        if (plan.ok) actions += ActionItem(Glyph.Wrench, stringResource(if (newer) Res.string.action_bring_up_to_date else Res.string.action_renovate), moneyText(plan.cost)) { onAction(renew) }
        if (newer) {
            val kind = city.newest(t)
            pills += PillItem(Glyph.Hourglass, stringResource(Res.string.pill_newer_kind, stringResource(buildingName(kind)), kind.year), Tone.Warn)
        } else if (city.condition(b) < 100) pills += PillItem(Glyph.Warn, stringResource(Res.string.pill_worn), Tone.Warn)
    }
    if ((t.root == BuildingType.COAL_PLANT || t.root == BuildingType.OIL_PLANT) && !b.scrubbed && city.allowsScrubbers()) {
        val fit = Action.FitScrubbers(b.x, b.y)
        val plan = city.plan(fit)
        if (plan.ok) actions += ActionItem(Glyph.Scrubber, stringResource(Res.string.action_scrubbers), moneyText(plan.cost)) { onAction(fit) }
    }
    val clear = Action.Bulldoze(b.x, b.y, b.x + t.width - 1, b.y + t.height - 1)
    val clearing = city.plan(clear)
    if (clearing.ok) actions += ActionItem(Glyph.Bulldoze, stringResource(Res.string.action_bulldoze), moneyText(clearing.cost), confirm = true) { onAction(clear) }

    val icon = ChoiceIcon(intArrayOf(BuildingSprites.sprite(t.ordinal, b.variant)))
    return Card(icon, stringResource(buildingName(t)), subtitle, pills, stats, actions)
}

@Composable
private fun tileCard(city: City, x: Int, y: Int, onAction: (Action) -> Unit, onLines: () -> Unit): Card {
    val map = city.map
    val i = map.index(x, y)
    val road = RoadType.of(map.road[i])
    val pills = ArrayList<PillItem>()
    val stats = ArrayList<StatItem>()
    val actions = ArrayList<ActionItem>()
    fun wear(laid: Int, life: Int) = Ageing.wear(city.monthNow - laid, life)
    @Composable
    fun wearStat(glyph: Glyph, name: String, laid: Int, life: Int): StatItem {
        val worn = wear(laid, life)
        return StatItem(
            glyph, name, stringResource(Res.string.value_laid, yearOf(laid)), min(1f, worn / 100f),
            when { worn >= 100 -> Tone.Bad; worn >= Balance.RENEWABLE_WEAR -> Tone.Warn; else -> Tone.Good },
        )
    }

    val title = stringResource(
        when {
            map.bank[i].toInt() != 0 -> Res.string.embankment
            map.portal[i].toInt() != 0 -> Res.string.tunnel_portal
            road != null && map.rail[i] != Rail.NONE -> Res.string.inspect_crossing
            road != null -> roadName(road)
            map.rail[i] != Rail.NONE -> Res.string.track
            map.cable(i) && map.power[i] == Power.HIGH -> Res.string.high_cable
            map.cable(i) -> Res.string.power_cable
            map.power[i] == Power.HIGH -> Res.string.high_line
            map.power[i] != Power.NONE -> Res.string.inspect_power_line
            map.terrain[i] == Terrain.WATER -> Res.string.inspect_water
            map.terrain[i] == Terrain.TREES -> Res.string.inspect_trees
            else -> Res.string.inspect_grass
        },
    )
    val zone = when (map.zone[i]) {
        Zone.RESIDENTIAL -> Res.string.inspect_zone_residential
        Zone.COMMERCIAL -> Res.string.inspect_zone_commercial
        Zone.INDUSTRIAL -> Res.string.inspect_zone_industrial
        Zone.FARMLAND -> Res.string.inspect_zone_farmland
        Zone.OFFICE -> Res.string.inspect_zone_office
        Zone.MIXED -> Res.string.inspect_zone_mixed
        else -> null
    }
    val subtitle = listOfNotNull(
        zone?.let { stringResource(it) },
        densityName(map.density[i])?.let { stringResource(it) },
        if (map.bridged(i)) stringResource(map.bridgeKind(i)?.let { bridgeName(it) } ?: Res.string.bridge_plain) else null,
        city.districtAt(i)?.name,
    ).joinToString(" · ").ifEmpty { null }

    // How it is.
    if (map.terrain[i] == Terrain.WATER) {
        if (map.foulLevel(i) > 0) pills += PillItem(Glyph.Smoke, stringResource(Res.string.inspect_foul), Tone.Bad)
        when {
            city.river > Balance.BANKFULL -> pills += PillItem(Glyph.Rain, stringResource(Res.string.event_river_flood), Tone.Bad)
            city.river > Balance.BANKFULL - 20 -> pills += PillItem(Glyph.Rain, stringResource(Res.string.inspect_river_high), Tone.Warn)
        }
    }
    if ((map.flood[i].toInt() and 0xff) >= Balance.FLOODED) pills += PillItem(Glyph.Rain, stringResource(Res.string.inspect_flooded), Tone.Bad)
    else if ((map.floodMemory[i].toInt() and 0xff) >= FLOODED_BEFORE) pills += PillItem(Glyph.Rain, stringResource(Res.string.inspect_flooded_before), Tone.Warn)
    brokenText(map, i)?.let { (text, works) -> pills += PillItem(if (works) Glyph.Wrench else Glyph.Warn, text, if (works) Tone.Warn else Tone.Bad) }
    if (road != null && city.snowedIn > 0) pills += PillItem(Glyph.Snow, stringResource(Res.string.snowed_in), Tone.Warn)
    if (map.brownfield[i].toInt() != 0) pills += PillItem(Glyph.Smoke, stringResource(Res.string.brownfield), Tone.Bad)
    if (map.streetTrees[i].toInt() != 0) pills += PillItem(Glyph.Tree, stringResource(Res.string.street_trees), Tone.Good)
    if (map.lane[i].toInt() != 0) pills += PillItem(Glyph.Diamond, stringResource(Res.string.inspect_bus_lane), Tone.Plain)
    // A road at the edge of the map: whether it leads out of town, and the choice to change it.
    if (road != null && map.atEdge(i)) {
        val out = map.leadsOut(i)
        pills += PillItem(Glyph.Road, stringResource(if (out) Res.string.pill_leads_out else Res.string.pill_kept_in), Tone.Plain)
        val link = Action.LinkOut(i, !out)
        actions += ActionItem(Glyph.Road, stringResource(if (out) Res.string.action_keep_in else Res.string.action_link_out), confirm = out) { onAction(link) }
    }

    // A bridge: how long, whether ships get by, whether trucks may cross, and whether it's open.
    val bridged = map.bridged(i)
    if (bridged) {
        val kind = map.bridgeKind(i)
        stats += StatItem(Glyph.Bridge, stringResource(Res.string.label_span), stringResource(Res.string.value_tiles, city.span(i)))
        stats += StatItem(
            Glyph.Ship, stringResource(Res.string.label_for_ships),
            stringResource(when (map.clearance(i)) { Bridge.HIGH -> Res.string.ships_pass_under; Bridge.OPENS -> Res.string.ships_it_opens; else -> Res.string.ships_blocked }),
        )
        val shut = map.bridgeShut[i].toInt() and 0xff
        when {
            shut == Balance.SHUT_UNSAFE -> pills += PillItem(Glyph.Warn, stringResource(Res.string.pill_unsafe), Tone.Bad)
            shut > 0 -> pills += PillItem(Glyph.Rain, stringResource(Res.string.pill_gale_shut), Tone.Warn)
            map.bridge[i].toInt() and Bridge.SHUT != 0 -> pills += PillItem(Glyph.Remove, stringResource(Res.string.pill_shut), Tone.Bad)
        }
        if (road != null && city.bridgeLight(i)) pills += PillItem(Glyph.Crate, stringResource(Res.string.pill_posted), Tone.Warn)
        val tolled = map.bridge[i].toInt() and Bridge.TOLL != 0
        if (tolled) pills += PillItem(Glyph.Coin, stringResource(Res.string.pill_toll), Tone.Plain)
        if (road != null) {
            val toll = Action.SetBridge(intArrayOf(i), toll = !tolled)
            val plan = city.plan(toll)
            if (plan.ok) actions += ActionItem(Glyph.Coin, stringResource(if (tolled) Res.string.action_toll_off else Res.string.action_toll_on), if (plan.cost > 0) moneyText(plan.cost) else null) { onAction(toll) }
        }
        val isShut = map.bridge[i].toInt() and Bridge.SHUT != 0
        val shutting = Action.SetBridge(intArrayOf(i), shut = !isShut)
        actions += ActionItem(Glyph.Remove, stringResource(if (isShut) Res.string.action_open else Res.string.action_shut), confirm = !isShut) { onAction(shutting) }
        kind?.let { stats += wearStat(Glyph.Bridge, stringResource(Res.string.label_wear), (if (road != null) map.roadLaid[i] else map.railLaid[i]).toInt(), it.life) }
    }

    // A tunnel under it: what kind, whether its pumps have power, and how worn it is.
    if (map.tunnelled(i)) {
        val rail = map.lowRail[i].toInt() != 0
        val where = when {
            map.terrain[i] == Terrain.WATER -> Res.string.tunnel_under_water
            map.road[i] != com.rm.infill.sim.Road.NONE || map.rail[i] != Rail.NONE -> Res.string.tunnel_underpass
            else -> Res.string.tunnel_cut_cover
        }
        stats += StatItem(Glyph.Tunnel, stringResource(if (rail) Res.string.rail_tunnel else Res.string.road_tunnel), stringResource(where))
        stats += wearStat(Glyph.Tunnel, stringResource(Res.string.label_wear), map.lowLaid[i].toInt(), Balance.TUNNEL_LIFE)
        if (map.tunnelShut(i)) pills += PillItem(Glyph.Rain, stringResource(Res.string.pill_tunnel_shut), Tone.Bad)
        else if (!city.pumpedAt(i)) pills += PillItem(Glyph.Bolt, stringResource(Res.string.pill_pumps_off), Tone.Warn)
    }

    // The road and what runs on it.
    if (road != null && map.bank[i].toInt() == 0) {
        if (!bridged || map.bridgeKind(i) == null) stats += wearStat(Glyph.Road, stringResource(Res.string.label_wear), map.roadLaid[i].toInt(), road.life)
        val busy = map.congestion[i].toInt() and 0xff
        stats += StatItem(Glyph.Car, stringResource(Res.string.label_traffic), level(busy).replaceFirstChar { it.uppercase() }, busy / 255f, when { busy >= 150 -> Tone.Bad; busy >= 70 -> Tone.Warn; else -> Tone.Good })
        if (map.control[i] != Junction.NONE) {
            val wait = city.junctionWait(i)
            stats += StatItem(Glyph.Lights, stringResource(junctionName(map.control[i])), stringResource(Res.string.value_seconds, wait))
        }
    }
    if (map.rail[i] != Rail.NONE && (!bridged || map.bridgeKind(i) == null)) stats += wearStat(Glyph.Rail, stringResource(Res.string.track), map.railLaid[i].toInt(), Balance.TRACK_LIFE)
    for ((kind, layer, laid) in listOf(Triple(Pipe.WATER, map.waterPipe, map.waterLaid), Triple(Pipe.SEWER, map.sewerPipe, map.sewerLaid), Triple(Pipe.STORM, map.stormPipe, map.stormLaid))) {
        val material = Material.of(kind, layer[i]) ?: continue
        val glyph = when (kind) {
            Pipe.WATER -> Glyph.Drop
            Pipe.SEWER -> Glyph.Manhole
            Pipe.STORM -> Glyph.Rain
        }
        stats += wearStat(glyph, stringResource(materialName(material)), laid[i].toInt(), material.life)
    }
    if (map.phone[i].toInt() != 0) {
        val name = when {
            map.phone[i] == Phone.FIBRE && map.duct(i) -> Res.string.fibre_duct
            map.phone[i] == Phone.FIBRE -> Res.string.fibre_line
            map.duct(i) -> Res.string.copper_duct
            else -> Res.string.copper_line
        }
        stats += wearStat(Glyph.Phone, stringResource(name), map.phoneLaid[i].toInt(), city.phoneLife(i))
    }
    if (map.cable(i)) stats += wearStat(Glyph.Cable, stringResource(Res.string.label_wear), map.powerLaid[i].toInt(), city.cableLife(i))
    // High voltage only feeds substations, so only ordinary lines carry a load to show.
    if (map.power[i] == Power.LINE) {
        val kw = city.lineLoad(i)
        val mw = tenths(kw / 100)
        stats += StatItem(
            Glyph.Bolt, stringResource(Res.string.label_load), stringResource(Res.string.megawatts, mw),
            min(1f, kw / Balance.LINE_RATING.toFloat()), if (kw > Balance.LINE_RATING) Tone.Bad else if (kw > Balance.LINE_RATING * 3 / 4) Tone.Warn else Tone.Good,
        )
    }
    // Transit on the street and under it, and whether anything runs.
    @Composable
    fun running(network: Int) = stringResource(if (network >= 0) Res.string.value_running else Res.string.no_service)
    if (map.tram[i].toInt() != 0) stats += StatItem(Glyph.Tram, stringResource(Res.string.tram_track), running(city.tramNetwork(i)))
    if (map.wire[i].toInt() != 0) stats += StatItem(Glyph.Bus, stringResource(Res.string.trolley_wire), running(city.trolleyNetwork(i)))
    if (map.subway[i].toInt() != 0) stats += wearStat(Glyph.Tunnel, stringResource(Res.string.subway), map.subwayLaid[i].toInt(), Balance.TUNNEL_LIFE)
    val stops = map.stop[i].toInt()
    if (stops != 0) {
        val name = listOfNotNull(
            if (stops and Stop.TRAM != 0) stringResource(Res.string.tram_stop) else null,
            if (stops and Stop.BUS != 0) stringResource(Res.string.bus_stop) else null,
        ).let { listText(it) }
        stats += StatItem(if (stops and Stop.TRAM != 0) Glyph.Tram else Glyph.Bus, name, pluralStringResource(Res.plurals.stop_riders, city.stopRiders(i), city.stopRiders(i)))
        val calling = city.linesAt(i)
        if (calling.isNotEmpty()) {
            val names = calling.map { line ->
                val number = city.lines.filter { it.tram == line.tram }.indexOf(line) + 1
                stringResource(Res.string.line_name, stringResource(if (line.tram) Res.string.tram_line else Res.string.bus_line), number)
            }
            stats += StatItem(Glyph.Route, stringResource(Res.string.label_lines), listText(names), wide = true)
        }
        actions += ActionItem(Glyph.List, stringResource(Res.string.action_lines), onClick = onLines)
    }
    when (map.resource[i]) {
        Resource.FERTILE -> stats += StatItem(Glyph.Tree, stringResource(Res.string.label_ground), stringResource(Res.string.inspect_fertile))
        Resource.ORE -> stats += StatItem(Glyph.Mountain, stringResource(Res.string.label_ground), stringResource(Res.string.inspect_ore))
        Resource.COAL -> stats += StatItem(Glyph.Mountain, stringResource(Res.string.label_ground), stringResource(Res.string.inspect_coal_seam))
        Resource.OIL -> stats += StatItem(Glyph.Drop, stringResource(Res.string.label_ground), stringResource(Res.string.inspect_oil))
    }

    // Relay what's worn.
    val renew = Action.RenewArea(x, y, x, y)
    val plan = city.plan(renew)
    if (plan.ok) actions.add(0, ActionItem(Glyph.Renew, stringResource(Res.string.action_renew), moneyText(plan.cost)) { onAction(renew) })

    return Card(tileIcon(map, i, road), title, subtitle, pills, stats, actions)
}

private fun needGlyph(need: Need): Glyph = when (need) {
    Need.POWER -> Glyph.Bolt
    Need.WATER -> Glyph.Drop
    Need.PHONE -> Glyph.Phone
    Need.BROADBAND -> Glyph.Mast
}

private fun needName(need: Need) = when (need) {
    Need.POWER -> Res.string.need_power
    Need.WATER -> Res.string.need_water
    Need.PHONE -> Res.string.need_phone
    Need.BROADBAND -> Res.string.need_internet
}

private fun needMissing(need: Need) = when (need) {
    Need.POWER -> Res.string.no_power
    Need.WATER -> Res.string.pill_no_water
    Need.PHONE -> Res.string.pill_no_phone
    Need.BROADBAND -> Res.string.pill_no_internet
}

/** A picture of what's on a tile with no building: its road, track, line or ground. */
private fun tileIcon(map: com.rm.infill.sim.CityMap, i: Int, road: RoadType?): ChoiceIcon {
    val across = 10
    val zone = map.zone[i]
    val back = if (zone != Zone.NONE) zoneColour(zone) else null
    return when {
        map.bank[i].toInt() != 0 -> ChoiceIcon(glyph = Glyph.Bank)
        road != null -> ChoiceIcon(
            intArrayOf(
                when (road) {
                    RoadType.DIRT -> Atlas.ROAD_DIRT
                    RoadType.GRAVEL -> Atlas.ROAD_GRAVEL
                    RoadType.LANE -> Atlas.ROAD_LANE
                    RoadType.STREET, RoadType.ONE_WAY_STREET -> Atlas.ROAD_STREET
                    else -> Atlas.ROAD_AVENUE
                } + across,
            ),
        )
        map.rail[i] != Rail.NONE -> ChoiceIcon(intArrayOf(Atlas.GRASS, Atlas.TRACK + across))
        map.cable(i) -> ChoiceIcon(glyph = Glyph.Cable, glyphColour = if (map.power[i] == Power.HIGH) Color(0xFFE0503A) else Color(0xFFE8A33A))
        map.power[i] == Power.HIGH -> ChoiceIcon(intArrayOf(Atlas.GRASS, Atlas.HV_LINE + across))
        map.power[i] != Power.NONE -> ChoiceIcon(intArrayOf(Atlas.GRASS, Atlas.POWER_LINE + across))
        map.terrain[i] == Terrain.WATER -> ChoiceIcon(intArrayOf(Atlas.WATER))
        map.terrain[i] == Terrain.TREES -> ChoiceIcon(intArrayOf(Atlas.GRASS, Atlas.FOREST), back = back)
        back != null -> ChoiceIcon(back = back, glyph = Glyph.Zone, glyphColour = Color.White)
        else -> ChoiceIcon(intArrayOf(Atlas.GRASS, Atlas.TREE))
    }
}

/** What's broken on a tile, or the works there, and whether it's works. */
@Composable
private fun brokenText(map: com.rm.infill.sim.CityMap, i: Int): Pair<String, Boolean>? {
    val bits = map.broken[i].toInt()
    if (bits == 0) return null
    val days = map.mendingDays(i)
    if (bits and Broken.WORKS != 0) {
        return (if (map.underRepair(i)) pluralStringResource(Res.plurals.works_on, days, days) else stringResource(Res.string.works_waiting)) to true
    }
    val which = when {
        bits and Broken.WATER != 0 -> Res.plurals.mend_main
        bits and Broken.SEWER != 0 -> Res.plurals.mend_sewer
        bits and Broken.STORM != 0 -> Res.plurals.mend_drain
        bits and Broken.RAIL != 0 -> Res.plurals.mend_track
        bits and Broken.TRAM != 0 -> Res.plurals.mend_tram
        bits and Broken.WIRE != 0 -> Res.plurals.mend_wire
        bits and Broken.SUBWAY != 0 -> Res.plurals.mend_tunnel
        bits and Broken.POWER != 0 -> Res.plurals.mend_power
        else -> Res.plurals.mend_road
    }
    return pluralStringResource(which, days, days) to false
}

/** Children, grown-ups and the old, in a residents bar. */
private val AGE_COLOURS = listOf(Color(0xFF7FC4E8), Color(0xFF4C8FD6), Color(0xFF9A7AC8))

/** Watts as megawatts, to a tenth below ten. */
internal fun megawatts(w: Int): String {
    val t = (w + 50_000) / 100_000
    return if (t < 100) tenths(t) else groupThousands((t / 10).toLong())
}

/** The year of a month counted from January 1900. */
internal fun yearOf(month: Int): Int = 1900 + month / 12

/** How much a tile has to remember of a flood for inspect to mention it. */
internal const val FLOODED_BEFORE = 32

/** Neighbours this upset by clearing nearby are told of in inspect. */
private const val UPSET_SHOWN = 20
