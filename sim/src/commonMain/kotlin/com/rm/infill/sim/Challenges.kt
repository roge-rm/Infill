package com.rm.infill.sim

/** What a challenge goal measures. */
enum class ChallengeMeasure {
    /** People in the town. */
    POPULATION,

    /** Percent of people on mains water. */
    ON_MAINS,

    /** No river flood or storm surge for the last whole year. */
    NO_FLOODS,

    /** Out of debt, and the overseer gone. */
    OUT_OF_DEBT,

    /** Approval, 0 to 100. */
    APPROVAL,

    /** How freely traffic moves, in percent. */
    FLOW,

    /** Percent of trips made other than by car. */
    GREEN_TRIPS,

    /** Crime among homes, 0 to 255; met at or under the goal. */
    CRIME,
}

/** One thing a challenge asks for: [measure] reaching [need], or for [ChallengeMeasure.CRIME] staying at or under it. */
class ChallengeGoal(val measure: ChallengeMeasure, val need: Int)

/**
 * A town already under way, from files/challenges/<id>.infill, with goals
 * to meet by the end of [until]. The town's own era goals carry on as well.
 */
enum class Challenge(val id: String, val until: Int, val goals: List<ChallengeGoal>) {
    /** A township of 1905 that has to become a streetcar town on mains water. */
    STREETCAR_SUBURB("streetcar_suburb", 1920, listOf(ChallengeGoal(ChallengeMeasure.POPULATION, 15_000), ChallengeGoal(ChallengeMeasure.ON_MAINS, 70))),

    /** A river town that floods every year or two, and has to stop it without losing its people. */
    THE_RIVER_RISES("the_river_rises", 1935, listOf(ChallengeGoal(ChallengeMeasure.NO_FLOODS, 1), ChallengeGoal(ChallengeMeasure.POPULATION, 20_000))),

    /** A town of 1950 deep in debt with the overseer at the books. */
    INTO_THE_RED("into_the_red", 1960, listOf(ChallengeGoal(ChallengeMeasure.OUT_OF_DEBT, 1), ChallengeGoal(ChallengeMeasure.APPROVAL, 55))),

    /** A jammed, unhappy town of 1970 that has to move again and win its people back. */
    RENEWAL("renewal", 1985, listOf(ChallengeGoal(ChallengeMeasure.FLOW, 65), ChallengeGoal(ChallengeMeasure.APPROVAL, 65), ChallengeGoal(ChallengeMeasure.CRIME, 20))),

    /** A car town of 2005 that has to get half its trips out of cars. */
    GREEN_CITY("green_city", 2025, listOf(ChallengeGoal(ChallengeMeasure.GREEN_TRIPS, 50), ChallengeGoal(ChallengeMeasure.APPROVAL, 60))),
    ;

    companion object {
        fun of(id: String): Challenge? = entries.firstOrNull { it.id == id }
    }
}

/** How a challenge stands: going, met in time, or past its year unmet. */
enum class ChallengeResult { GOING, WON, LOST }
