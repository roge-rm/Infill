package com.rm.infill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.res.Res
import com.rm.infill.res.challenge
import com.rm.infill.res.challenge_by
import com.rm.infill.res.challenge_green_city
import com.rm.infill.res.challenge_green_city_line
import com.rm.infill.res.challenge_into_the_red
import com.rm.infill.res.challenge_into_the_red_line
import com.rm.infill.res.challenge_lost
import com.rm.infill.res.challenge_renewal
import com.rm.infill.res.challenge_renewal_line
import com.rm.infill.res.challenge_river_rises
import com.rm.infill.res.challenge_river_rises_line
import com.rm.infill.res.challenge_streetcar_suburb
import com.rm.infill.res.challenge_streetcar_suburb_line
import com.rm.infill.res.challenge_won
import com.rm.infill.res.cgoal_approval
import com.rm.infill.res.cgoal_crime
import com.rm.infill.res.cgoal_flow
import com.rm.infill.res.cgoal_green_trips
import com.rm.infill.res.cgoal_no_floods
import com.rm.infill.res.cgoal_on_mains
import com.rm.infill.res.cgoal_out_of_debt
import com.rm.infill.res.cgoal_population
import com.rm.infill.sim.Challenge
import com.rm.infill.sim.ChallengeGoal
import com.rm.infill.sim.ChallengeMeasure
import com.rm.infill.sim.ChallengeResult
import com.rm.infill.sim.City
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun challengeTitle(c: Challenge): StringResource = when (c) {
    Challenge.STREETCAR_SUBURB -> Res.string.challenge_streetcar_suburb
    Challenge.THE_RIVER_RISES -> Res.string.challenge_river_rises
    Challenge.INTO_THE_RED -> Res.string.challenge_into_the_red
    Challenge.RENEWAL -> Res.string.challenge_renewal
    Challenge.GREEN_CITY -> Res.string.challenge_green_city
}

private fun challengeLine(c: Challenge): StringResource = when (c) {
    Challenge.STREETCAR_SUBURB -> Res.string.challenge_streetcar_suburb_line
    Challenge.THE_RIVER_RISES -> Res.string.challenge_river_rises_line
    Challenge.INTO_THE_RED -> Res.string.challenge_into_the_red_line
    Challenge.RENEWAL -> Res.string.challenge_renewal_line
    Challenge.GREEN_CITY -> Res.string.challenge_green_city_line
}

/** What a goal asks for, as a line. */
@Composable
private fun challengeGoalText(g: ChallengeGoal): String = when (g.measure) {
    ChallengeMeasure.POPULATION -> stringResource(Res.string.cgoal_population, groupThousands(g.need.toLong()))
    ChallengeMeasure.ON_MAINS -> stringResource(Res.string.cgoal_on_mains, g.need)
    ChallengeMeasure.NO_FLOODS -> stringResource(Res.string.cgoal_no_floods)
    ChallengeMeasure.OUT_OF_DEBT -> stringResource(Res.string.cgoal_out_of_debt)
    ChallengeMeasure.APPROVAL -> stringResource(Res.string.cgoal_approval, g.need)
    ChallengeMeasure.FLOW -> stringResource(Res.string.cgoal_flow, g.need)
    ChallengeMeasure.GREEN_TRIPS -> stringResource(Res.string.cgoal_green_trips, g.need)
    ChallengeMeasure.CRIME -> stringResource(Res.string.cgoal_crime, level(g.need))
}

/** The challenges to pick from on the New city screen, each with what it asks for and by when. */
@Composable
fun ChallengeList(onPick: (Challenge) -> Unit) {
    val c = Infill.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (ch in Challenge.entries) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.button)
                    .semantics(mergeDescendants = true) {}
                    .clickable(role = Role.Button) { onPick(ch) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(challengeTitle(ch)), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(challengeLine(ch)), color = c.text, fontSize = 14.sp)
                for (g in ch.goals) Text("• " + challengeGoalText(g), color = c.textDim, fontSize = 13.sp)
                Text(stringResource(Res.string.challenge_by, ch.until), color = c.textDim, fontSize = 13.sp)
            }
        }
    }
}

/** The challenge's goals on the era card: each met or not, and how it stands. */
@Composable
fun ChallengeSection(city: City) {
    val ch = city.challenge ?: return
    val c = Infill.colors
    Section(stringResource(Res.string.challenge) + ": " + stringResource(challengeTitle(ch)), Glyph.Target) {
        when (city.challengeResult) {
            ChallengeResult.WON -> Text(stringResource(Res.string.challenge_won), color = c.good, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            ChallengeResult.LOST -> Text(stringResource(Res.string.challenge_lost, ch.until), color = c.warn, fontSize = 14.sp)
            ChallengeResult.GOING -> Text(stringResource(Res.string.challenge_by, ch.until), color = c.textDim, fontSize = 13.sp)
        }
        for (g in ch.goals) {
            val met = city.challengeMet(g)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(if (met) c.good else c.button),
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(if (met) Glyph.Check else Glyph.Hourglass, if (met) Color.White else c.textDim, Modifier.size(16.dp)) }
                Text(challengeGoalText(g), color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
