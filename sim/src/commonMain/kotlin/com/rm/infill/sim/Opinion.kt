package com.rm.infill.sim

import kotlin.math.max

/** What people think about when they think about how the town's run. */
enum class Concern { TAXES, JOBS, CRIME, HEALTH, SERVICES, LEISURE, TRAFFIC, CLEARANCES, AIR, HOUSING }

/** What a petition asks for near where its people live. */
enum class Want { PARK, POLICE, FIRE, WATER, SEWER, POWER, TRANSIT }

/** People living around [x], [y] asking for [want], until [until] (a month number). */
class Petition(val want: Want, val x: Int, val y: Int, val until: Int)

/** Help from higher government, offered in its years for one kind of work. */
enum class GrantKind(val from: Int, val until: Int) {
    /** Sewering the town, in the depression's public works. */
    SEWERS(1930, 1950),

    /** Highways, with the money of the post-war road programmes. */
    HIGHWAYS(1950, 1975),

    /** Transit, once the highways have lost their shine. */
    TRANSIT(1970, 2000),

    /** Storm drains, as the floods get worse. */
    RESILIENCE(2010, 2060),
}

/** A grant on offer: [amount] paid once the town's [GrantKind] figure reaches [goal] by month [until]. */
class Grant(val kind: GrantKind, val amount: Long, val goal: Int, val until: Int)

/** Public opinion: what each concern weighs in each era and how a concern's figures make a score. */
object Opinion {
    /** A new town's approval, out of 100. */
    const val START = 60

    /**
     * What each [Concern] counts for, out of 100, in each era: power and
     * water and the services at first, jobs and traffic in the motor age,
     * the clearances in renewal, then housing, the air and leisure.
     */
    private val WEIGHTS = mapOf(
        Era.TOWNSHIP to intArrayOf(20, 15, 15, 20, 15, 5, 0, 5, 0, 5),
        Era.STREETCAR to intArrayOf(15, 15, 15, 15, 15, 5, 5, 5, 5, 5),
        Era.MOTOR to intArrayOf(15, 20, 10, 10, 10, 10, 15, 5, 0, 5),
        Era.RENEWAL to intArrayOf(15, 15, 10, 5, 5, 10, 10, 15, 10, 5),
        Era.INFILL to intArrayOf(10, 10, 10, 5, 5, 15, 10, 5, 15, 15),
        Era.FUTURE to intArrayOf(10, 10, 10, 5, 5, 15, 10, 5, 15, 15),
    )

    fun weights(era: Era): IntArray = WEIGHTS.getValue(era)

    /** The approval the [scores] come to in [era], each 0 to 100, less what people have come to expect by then. */
    fun target(era: Era, scores: IntArray): Int {
        val w = weights(era)
        var sum = 0
        for (k in scores.indices) sum += w[k] * scores[k]
        return max(0, sum / 100 - era.ordinal * EXPECT)
    }

    /** What people come to expect more of each era, in points of approval. */
    private const val EXPECT = 2

    /** The concern doing most harm: the biggest weight times what's missing from its score. */
    fun worst(era: Era, scores: IntArray): Concern {
        val w = weights(era)
        return Concern.entries.maxBy { w[it.ordinal] * (100 - scores[it.ordinal]) }
    }

    /** Taxes, from the three rates, homes counting double: 4% is welcome, 7% grudged, 12% hated. */
    fun taxes(homes: Int, shops: Int, industry: Int): Int = score(100 - ((homes * 2 + shops + industry) * 25 - 300) / 10)

    fun jobs(unemployment: Int): Int = score(100 - unemployment * 5)

    /** Crime, 0 to 255, where people are. */
    fun crime(crime: Int): Int = score(100 - crime * 100 / 120)

    /** Average health, 0 to 100: poorly at 35, as well as anyone can be at 85. */
    fun health(health: Int): Int = score((health - 35) * 2)

    /**
     * Power, mains water and the sewer, as percents of the town that has them.
     * Nobody misses what nobody has yet, so a township's people mind less.
     */
    fun services(powered: Int, onMains: Int, onSewer: Int, era: Era): Int {
        val has = (powered + onMains + onSewer) / 3
        return if (era == Era.TOWNSHIP) score(70 + has * 30 / 100) else score(has)
    }

    /** Leisure near home against what's expected this year. */
    fun leisure(leisure: Int, expected: Int): Int = score(50 + (leisure - expected) * 3 / 2)

    /** How freely traffic moves, in percent. */
    fun traffic(flow: Int): Int = score((flow - 40) * 100 / 60)

    /** The upset left by clearances, 0 to 250, and the share of the town forced out lately, in percent. */
    fun clearances(upset: Int, displaced: Int): Int = score(100 - upset * 100 / 150 - displaced * 4)

    /** Smog and pollution, each 0 to 255. */
    fun air(smog: Int, pollution: Int): Int = score(100 - maxOf(smog, pollution) * 100 / 160)

    /** People looking for a home the town hasn't got, as a percent of the town. */
    fun housing(seeking: Int): Int = score(100 - seeking * 3)

    private fun score(v: Int) = v.coerceIn(0, 100)
}
