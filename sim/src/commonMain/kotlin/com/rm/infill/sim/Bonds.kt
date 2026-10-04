package com.rm.infill.sim

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * A bond the town has sold: what it [raised] in [year], its [rate] in
 * hundredths of a percent a year, the [payment] each month, and the
 * [monthsLeft] to pay it.
 */
class Bond(val raised: Long, val rate: Int, val payment: Long, var monthsLeft: Int, val year: Int)

/**
 * Borrowing by bonds: the rates of each era, a spread for the town's rating,
 * and what a bond costs a month to pay back over its term.
 */
object Bonds {
    /** Years to pay a bond back. */
    const val TERM_YEARS = 20

    /** The ratings, best first. Below the last nobody will buy the town's bonds. */
    val RATINGS = listOf("AAA", "AA", "A", "BBB", "BB", "B")

    /** A new town's rating. */
    const val START_RATING = 2

    /** What a step down the ratings adds to the rate, in hundredths of a percent. */
    const val SPREAD = 75

    /** The interest the safest towns paid, in hundredths of a percent, by year: cheap early on, dear in the early 1980s. */
    private val years = intArrayOf(1900, 1920, 1945, 1970, 1981, 1995, 2010, 2025)
    private val rates = intArrayOf(400, 450, 250, 650, 1300, 650, 350, 450)

    /** The rate for a town of [rating] in [year], in hundredths of a percent a year. */
    fun rate(year: Int, rating: Int): Int {
        var base = rates.last()
        if (year <= years.first()) base = rates.first()
        else for (k in 1 until years.size) if (year <= years[k]) {
            base = rates[k - 1] + (rates[k] - rates[k - 1]) * (year - years[k - 1]) / (years[k] - years[k - 1])
            break
        }
        return base + SPREAD * rating
    }

    /** What paying back [amount] at [rate] over [months] costs a month. */
    fun payment(amount: Long, rate: Int, months: Int): Long {
        val r = rate / 10_000.0 / 12
        if (r <= 0.0) return (amount + months - 1) / months
        return (amount * r / (1 - (1 + r).pow(-months))).roundToLong()
    }
}
