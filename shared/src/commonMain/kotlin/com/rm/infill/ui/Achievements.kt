package com.rm.infill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.platform.platform
import com.rm.infill.res.*
import com.rm.infill.sim.Challenge
import com.rm.infill.sim.ChallengeResult
import com.rm.infill.sim.City
import com.rm.infill.sim.CityEvent
import com.rm.infill.sim.Era
import com.rm.infill.sim.EventKind
import com.rm.infill.sim.LANDMARKS
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What a player can earn across all their towns, each with its name and what it asks. */
enum class Achievement(val title: StringResource, val detail: StringResource) {
    PEOPLE_1K(Res.string.ach_people_1k, Res.string.ach_people_1k_detail),
    PEOPLE_10K(Res.string.ach_people_10k, Res.string.ach_people_10k_detail),
    PEOPLE_50K(Res.string.ach_people_50k, Res.string.ach_people_50k_detail),
    PEOPLE_100K(Res.string.ach_people_100k, Res.string.ach_people_100k_detail),
    STREETCAR(Res.string.ach_streetcar, Res.string.ach_streetcar_detail),
    MOTOR(Res.string.ach_motor, Res.string.ach_motor_detail),
    RENEWAL(Res.string.ach_renewal, Res.string.ach_renewal_detail),
    INFILL(Res.string.ach_infill, Res.string.ach_infill_detail),
    FUTURE(Res.string.ach_future, Res.string.ach_future_detail),
    CENTURY(Res.string.ach_century, Res.string.ach_century_detail),
    LANDMARK(Res.string.ach_landmark, Res.string.ach_landmark_detail),
    ALL_LANDMARKS(Res.string.ach_all_landmarks, Res.string.ach_all_landmarks_detail),
    CHALLENGE(Res.string.ach_challenge, Res.string.ach_challenge_detail),
    ALL_CHALLENGES(Res.string.ach_all_challenges, Res.string.ach_all_challenges_detail),
    LEGACY(Res.string.ach_legacy, Res.string.ach_legacy_detail),
    ALL_LEGACY(Res.string.ach_all_legacy, Res.string.ach_all_legacy_detail),
    OVERSEER_OUT(Res.string.ach_overseer_out, Res.string.ach_overseer_out_detail),
}

/** The achievements earned, kept with the settings, so they go with the player and not a town. */
object Achievements {
    private const val KEY = "achievements"
    private const val WON = "challengesWon"

    fun earned(): Set<Achievement> =
        platform.setting(KEY).orEmpty().split(',').mapNotNull { name -> Achievement.entries.firstOrNull { it.name == name } }.toSet()

    private fun won(): Set<String> = platform.setting(WON).orEmpty().split(',').filter { it.isNotEmpty() }.toSet()

    /**
     * What [city] has newly earned, as of now or with [e], which are then
     * kept. A sandbox earns nothing.
     */
    fun check(city: City, e: CityEvent? = null): List<Achievement> {
        if (city.sandbox) return emptyList()
        val have = earned()
        val due = ArrayList<Achievement>()
        val people = city.stats.population
        if (people >= 1_000) due += Achievement.PEOPLE_1K
        if (people >= 10_000) due += Achievement.PEOPLE_10K
        if (people >= 50_000) due += Achievement.PEOPLE_50K
        if (people >= 100_000) due += Achievement.PEOPLE_100K
        // Eras only count in a town played from the start; a challenge begins partway.
        if (city.challenge == null) {
            if (city.era >= Era.STREETCAR) due += Achievement.STREETCAR
            if (city.era >= Era.MOTOR) due += Achievement.MOTOR
            if (city.era >= Era.RENEWAL) due += Achievement.RENEWAL
            if (city.era >= Era.INFILL) due += Achievement.INFILL
            if (city.era >= Era.FUTURE) due += Achievement.FUTURE
            if (city.year >= 2000) due += Achievement.CENTURY
        }
        val built = city.allBuildings.filter { it.type in LANDMARKS && it.underway == 0 }.map { it.type }.toSet()
        if (built.isNotEmpty()) due += Achievement.LANDMARK
        if (built.size == LANDMARKS.size) due += Achievement.ALL_LANDMARKS
        if (city.legacyMet != 0) due += Achievement.LEGACY
        if (city.legacyMet == (1 shl city.legacyGoals().size) - 1) due += Achievement.ALL_LEGACY
        if (e?.kind == EventKind.OverseerOut) due += Achievement.OVERSEER_OUT
        val c = city.challenge
        if (c != null && city.challengeResult == ChallengeResult.WON) {
            val wins = won() + c.id
            platform.setSetting(WON, wins.joinToString(","))
            due += Achievement.CHALLENGE
            if (Challenge.entries.all { it.id in wins }) due += Achievement.ALL_CHALLENGES
        }
        val fresh = due.filter { it !in have }.distinct()
        if (fresh.isNotEmpty()) platform.setSetting(KEY, (have + fresh).joinToString(",") { it.name })
        return fresh
    }
}

/** The achievements, earned and still to come. */
@Composable
fun AchievementsWindow(onClose: () -> Unit) {
    val c = Infill.colors
    val earned = Achievements.earned()
    Window(Res.string.achievements, onClose, Glyph.Star) {
        Text(stringResource(Res.string.achievements_count, earned.size, Achievement.entries.size), color = c.textDim, fontSize = 13.sp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (a in Achievement.entries) {
                val got = a in earned
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(if (got) c.good else c.button),
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(if (got) Glyph.Star else Glyph.Hourglass, if (got) Color.White else c.textDim, Modifier.size(16.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(a.title), color = if (got) c.text else c.textDim, fontSize = 14.sp)
                        Text(stringResource(a.detail), color = c.textDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
