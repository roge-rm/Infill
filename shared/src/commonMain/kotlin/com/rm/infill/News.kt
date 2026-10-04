package com.rm.infill

import androidx.compose.runtime.Composable
import com.rm.infill.res.Res
import com.rm.infill.res.event_milestone
import com.rm.infill.res.event_first_built
import com.rm.infill.res.event_era_began
import com.rm.infill.res.event_blizzard
import com.rm.infill.res.event_bridge_shut
import com.rm.infill.res.event_broke_down
import com.rm.infill.res.event_dump_full
import com.rm.infill.res.event_earthquake
import com.rm.infill.res.event_epidemic
import com.rm.infill.res.event_epidemic_over
import com.rm.infill.res.event_fire
import com.rm.infill.res.event_fire_damage
import com.rm.infill.res.event_flooding
import com.rm.infill.res.event_forced_out
import com.rm.infill.res.event_gale
import com.rm.infill.res.event_heat_wave
import com.rm.infill.res.event_industrial_accident
import com.rm.infill.res.event_jobs_lost
import com.rm.infill.res.event_lost
import com.rm.infill.res.event_main_burst
import com.rm.infill.res.event_nuclear_accident
import com.rm.infill.res.event_ordinance_available
import com.rm.infill.res.event_ordinance_ended
import com.rm.infill.res.event_overseer_in
import com.rm.infill.res.event_overseer_out
import com.rm.infill.res.event_rating_down
import com.rm.infill.res.event_rating_up
import com.rm.infill.res.event_river_flood
import com.rm.infill.res.event_saved
import com.rm.infill.res.event_sewer_collapsed
import com.rm.infill.res.event_sickness
import com.rm.infill.res.event_smog
import com.rm.infill.res.event_track_broken
import com.rm.infill.res.event_tram_track_broken
import com.rm.infill.res.event_tunnel_flooded
import com.rm.infill.res.event_tunnel_shut
import com.rm.infill.res.event_wire_down
import com.rm.infill.res.event_protest
import com.rm.infill.res.event_petition
import com.rm.infill.res.event_petition_met
import com.rm.infill.res.event_petition_lapsed
import com.rm.infill.res.event_grant_offered
import com.rm.infill.res.event_grant_paid
import com.rm.infill.res.event_grant_lapsed
import com.rm.infill.res.event_election_won
import com.rm.infill.res.event_election_lost
import com.rm.infill.sim.CityEvent
import com.rm.infill.sim.EventKind
import com.rm.infill.ui.buildingName
import com.rm.infill.ui.groupThousands
import com.rm.infill.ui.ordinanceName
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A line of news or a message for the player, and where it happened if anywhere. */
internal data class Message(
    /** Null when it's [counted] instead. */
    val text: StringResource?,
    val arg: StringResource? = null,
    val x: Int = -1,
    val y: Int = -1,
    val name: String? = null,
    /** Said with a count instead, as the language says that many. */
    val counted: PluralStringResource? = null,
    val count: Int = 0,
)

/** What [e] says, as a line of news. */
internal fun messageOf(e: CityEvent): Message? =
    when (e.kind) {
        // History ends a law, or brings one the town can pass.
        EventKind.OrdinanceEnded -> Message(Res.string.event_ordinance_ended, ordinanceName(com.rm.infill.sim.Ordinance.entries[e.count]))
        EventKind.OrdinanceAvailable -> Message(Res.string.event_ordinance_available, ordinanceName(com.rm.infill.sim.Ordinance.entries[e.count]))
        EventKind.OverseerIn -> Message(Res.string.event_overseer_in)
        EventKind.OverseerOut -> Message(Res.string.event_overseer_out)
        EventKind.RatingDown -> Message(Res.string.event_rating_down, name = com.rm.infill.sim.Bonds.RATINGS[e.count])
        EventKind.RatingUp -> Message(Res.string.event_rating_up, name = com.rm.infill.sim.Bonds.RATINGS[e.count])
        EventKind.FireStarted -> Message(Res.string.event_fire, e.type?.let { buildingName(it) }, e.x, e.y)
        EventKind.BuildingLost -> Message(Res.string.event_lost, e.type?.let { buildingName(it) }, e.x, e.y)
        EventKind.FireSaved -> Message(Res.string.event_saved, e.type?.let { buildingName(it) }, e.x, e.y)
        EventKind.FireDamaged -> Message(Res.string.event_fire_damage, e.type?.let { buildingName(it) }, e.x, e.y)
        EventKind.ForcedOut -> Message(null, x = e.x, y = e.y, counted = Res.plurals.event_forced_out, count = e.count)
        EventKind.JobsLost -> Message(null, x = e.x, y = e.y, counted = Res.plurals.event_jobs_lost, count = e.count)
        EventKind.Flooding -> Message(Res.string.event_flooding, x = e.x, y = e.y)
        EventKind.RiverFlood -> Message(Res.string.event_river_flood, x = e.x, y = e.y)
        EventKind.Sickness -> Message(Res.string.event_sickness, x = e.x, y = e.y)
        EventKind.MainBurst -> Message(Res.string.event_main_burst, x = e.x, y = e.y)
        EventKind.SewerCollapsed -> Message(Res.string.event_sewer_collapsed, x = e.x, y = e.y)
        EventKind.TrackBroken -> Message(Res.string.event_track_broken, x = e.x, y = e.y)
        EventKind.BrokeDown -> Message(Res.string.event_broke_down, e.type?.let { buildingName(it) }, e.x, e.y)
        EventKind.TramTrackBroken -> Message(Res.string.event_tram_track_broken, x = e.x, y = e.y)
        EventKind.WireDown -> Message(Res.string.event_wire_down, x = e.x, y = e.y)
        EventKind.TunnelShut -> Message(Res.string.event_tunnel_shut, x = e.x, y = e.y)
        EventKind.TunnelFlooded -> Message(Res.string.event_tunnel_flooded, x = e.x, y = e.y)
        EventKind.BridgeShut -> Message(Res.string.event_bridge_shut, x = e.x, y = e.y)
        EventKind.Smog -> Message(Res.string.event_smog)
        EventKind.DumpFull -> Message(Res.string.event_dump_full, x = e.x, y = e.y)
        EventKind.Gale -> Message(Res.string.event_gale, x = e.x, y = e.y)
        EventKind.Blizzard -> Message(Res.string.event_blizzard)
        EventKind.HeatWave -> Message(Res.string.event_heat_wave)
        EventKind.IndustrialAccident -> Message(Res.string.event_industrial_accident, e.type?.let { buildingName(it) }, e.x, e.y)
        EventKind.NuclearAccident -> Message(Res.string.event_nuclear_accident, x = e.x, y = e.y)
        EventKind.Earthquake -> Message(Res.string.event_earthquake, x = e.x, y = e.y)
        EventKind.Epidemic -> Message(Res.string.event_epidemic)
        EventKind.EpidemicOver -> Message(Res.string.event_epidemic_over)
        EventKind.EraArrived -> e.era?.let { Message(Res.string.event_era_began, com.rm.infill.ui.eraName(it)) }
        EventKind.FirstBuilt -> Message(Res.string.event_first_built, e.type?.let { buildingName(it) }, e.x, e.y)
        EventKind.Milestone -> Message(Res.string.event_milestone, name = groupThousands(e.count.toLong()))
        EventKind.Protest -> Message(Res.string.event_protest, x = e.x, y = e.y)
        EventKind.Petition -> Message(Res.string.event_petition, com.rm.infill.ui.wantName(com.rm.infill.sim.Want.entries[e.count]), e.x, e.y)
        EventKind.PetitionMet -> Message(Res.string.event_petition_met, com.rm.infill.ui.wantName(com.rm.infill.sim.Want.entries[e.count]), e.x, e.y)
        EventKind.PetitionLapsed -> Message(Res.string.event_petition_lapsed, com.rm.infill.ui.wantName(com.rm.infill.sim.Want.entries[e.count]), e.x, e.y)
        EventKind.GrantOffered -> Message(Res.string.event_grant_offered, com.rm.infill.ui.grantNoun(com.rm.infill.sim.GrantKind.entries[e.count]))
        EventKind.GrantPaid -> Message(Res.string.event_grant_paid, com.rm.infill.ui.grantNoun(com.rm.infill.sim.GrantKind.entries[e.count]))
        EventKind.GrantLapsed -> Message(Res.string.event_grant_lapsed, com.rm.infill.ui.grantNoun(com.rm.infill.sim.GrantKind.entries[e.count]))
        EventKind.ElectionWon -> Message(Res.string.event_election_won)
        EventKind.ElectionLost -> Message(Res.string.event_election_lost)
    }

/** [m] as text. */
@Composable
internal fun messageText(m: Message): String = when {
    m.counted != null -> pluralStringResource(m.counted, m.count, groupThousands(m.count.toLong()))
    m.text == null -> ""
    m.arg != null -> stringResource(m.text, stringResource(m.arg))
    m.name != null -> stringResource(m.text, m.name)
    else -> stringResource(m.text)
}
