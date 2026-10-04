package com.rm.infill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.GameState
import com.rm.infill.res.Res
import com.rm.infill.res.approval
import com.rm.infill.res.concern_air
import com.rm.infill.res.concern_clearances
import com.rm.infill.res.concern_crime
import com.rm.infill.res.concern_health
import com.rm.infill.res.concern_housing
import com.rm.infill.res.concern_jobs
import com.rm.infill.res.concern_leisure
import com.rm.infill.res.concern_services
import com.rm.infill.res.concern_taxes
import com.rm.infill.res.concern_traffic
import com.rm.infill.res.date
import com.rm.infill.res.elections
import com.rm.infill.res.grant
import com.rm.infill.res.grant_highways
import com.rm.infill.res.grant_none
import com.rm.infill.res.grant_noun_highways
import com.rm.infill.res.grant_noun_resilience
import com.rm.infill.res.grant_noun_sewers
import com.rm.infill.res.grant_noun_transit
import com.rm.infill.res.grant_resilience
import com.rm.infill.res.grant_sewers
import com.rm.infill.res.grant_transit
import com.rm.infill.res.grants_this_term
import com.rm.infill.res.hold_elections
import com.rm.infill.res.month_short
import com.rm.infill.res.next_election
import com.rm.infill.res.opinion
import com.rm.infill.res.percent
import com.rm.infill.res.petition_until
import com.rm.infill.res.petitions
import com.rm.infill.res.petitions_none
import com.rm.infill.res.taxes_capped
import com.rm.infill.res.want_fire
import com.rm.infill.res.want_park
import com.rm.infill.res.want_police
import com.rm.infill.res.want_power
import com.rm.infill.res.want_sewer
import com.rm.infill.res.want_transit
import com.rm.infill.res.want_water
import com.rm.infill.res.what_people_think
import com.rm.infill.sim.Concern
import com.rm.infill.sim.GrantKind
import com.rm.infill.sim.Opinion
import com.rm.infill.sim.Want
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

fun concernName(c: Concern): StringResource = when (c) {
    Concern.TAXES -> Res.string.concern_taxes
    Concern.JOBS -> Res.string.concern_jobs
    Concern.CRIME -> Res.string.concern_crime
    Concern.HEALTH -> Res.string.concern_health
    Concern.SERVICES -> Res.string.concern_services
    Concern.LEISURE -> Res.string.concern_leisure
    Concern.TRAFFIC -> Res.string.concern_traffic
    Concern.CLEARANCES -> Res.string.concern_clearances
    Concern.AIR -> Res.string.concern_air
    Concern.HOUSING -> Res.string.concern_housing
}

private fun concernGlyph(c: Concern): Glyph = when (c) {
    Concern.TAXES -> Glyph.Coins
    Concern.JOBS -> Glyph.Briefcase
    Concern.CRIME -> Glyph.Cuffs
    Concern.HEALTH -> Glyph.Cross
    Concern.SERVICES -> Glyph.Bolt
    Concern.LEISURE -> Glyph.Tree
    Concern.TRAFFIC -> Glyph.Car
    Concern.CLEARANCES -> Glyph.Bulldoze
    Concern.AIR -> Glyph.Smoke
    Concern.HOUSING -> Glyph.Building
}

/** What a petition asks for, as said in the middle of a sentence. */
fun wantName(w: Want): StringResource = when (w) {
    Want.PARK -> Res.string.want_park
    Want.POLICE -> Res.string.want_police
    Want.FIRE -> Res.string.want_fire
    Want.WATER -> Res.string.want_water
    Want.SEWER -> Res.string.want_sewer
    Want.POWER -> Res.string.want_power
    Want.TRANSIT -> Res.string.want_transit
}

/** What a grant is toward, in the middle of a sentence. */
fun grantNoun(k: GrantKind): StringResource = when (k) {
    GrantKind.SEWERS -> Res.string.grant_noun_sewers
    GrantKind.HIGHWAYS -> Res.string.grant_noun_highways
    GrantKind.TRANSIT -> Res.string.grant_noun_transit
    GrantKind.RESILIENCE -> Res.string.grant_noun_resilience
}

/**
 * What the town thinks of how it's run: approval, each concern's score in
 * the order the era weighs them, the petitions waiting, any grant on offer,
 * and elections.
 */
@Composable
fun OpinionWindow(game: GameState, onGo: (Int, Int) -> Unit, onClose: () -> Unit) {
    val c = Infill.colors
    game.revision
    val city = game.city
    val months = stringArrayResource(Res.array.month_short)
    // A month number counts from January 1900.
    @Composable
    fun date(month: Int) = stringResource(Res.string.date, months.getOrElse(month % 12) { "" }, 1900 + month / 12)
    Window(Res.string.opinion, onClose, Glyph.Person, help = "public-opinion") {
        StatGrid(listOf(StatItem(Glyph.Person, stringResource(Res.string.approval), stringResource(Res.string.percent, city.approval), city.approval / 100f, toneOf(city.approval, 60, Opinion.START - 20), wide = true)))
        Section(stringResource(Res.string.what_people_think), Glyph.List) {
            val weights = Opinion.weights(city.era)
            StatGrid(Concern.entries.filter { weights[it.ordinal] > 0 }.sortedByDescending { weights[it.ordinal] }.map { k ->
                val score = city.concerns[k.ordinal]
                StatItem(concernGlyph(k), stringResource(concernName(k)), stringResource(Res.string.percent, score), score / 100f, toneOf(score, 60, 35))
            })
        }
        Section(stringResource(Res.string.petitions), Glyph.Book) {
            if (city.petitions.isEmpty()) Text(stringResource(Res.string.petitions_none), color = c.textDim, fontSize = 13.sp)
            for (p in city.petitions.toList()) {
                Text(
                    stringResource(Res.string.petition_until, stringResource(wantName(p.want)).replaceFirstChar { it.uppercase() }, date(p.until)),
                    color = c.text, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.button)
                        .clickable(role = Role.Button) { onGo(p.x, p.y) }.padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
        Section(stringResource(Res.string.grant), Glyph.Coin) {
            val g = city.grant
            if (g == null) Text(stringResource(Res.string.grant_none), color = c.textDim, fontSize = 13.sp)
            else {
                val text = when (g.kind) {
                    GrantKind.SEWERS -> Res.string.grant_sewers
                    GrantKind.HIGHWAYS -> Res.string.grant_highways
                    GrantKind.TRANSIT -> Res.string.grant_transit
                    GrantKind.RESILIENCE -> Res.string.grant_resilience
                }
                Text(stringResource(text, moneyText(g.amount), g.goal, date(g.until), city.grantFigure(g.kind)), color = c.text, fontSize = 14.sp)
            }
        }
        Section(stringResource(Res.string.elections), Glyph.Check) {
            val on = city.elections
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (on) c.accent else c.button)
                    .semantics(mergeDescendants = true) {}
                    .toggleable(value = on, role = Role.Switch) { game.setElections(it) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(Res.string.hold_elections), color = if (on) c.onAccent else c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (on) Text(stringResource(Res.string.next_election, city.nextElection()), color = c.onAccent, fontSize = 12.sp, maxLines = 1)
            }
            if (on && city.grantsThisTerm > 0) Text(stringResource(Res.string.grants_this_term, city.grantsThisTerm), color = c.textDim, fontSize = 13.sp)
            if (city.taxCapUntil > city.monthNow) {
                Text(stringResource(Res.string.taxes_capped, city.maxTax(), date(city.taxCapUntil)), color = c.warn, fontSize = 13.sp)
            }
        }
    }
}
