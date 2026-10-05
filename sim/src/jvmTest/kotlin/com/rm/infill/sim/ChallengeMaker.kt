package com.rm.infill.sim

import java.io.File
import kotlin.test.Test

/**
 * Makes the challenges' starting towns: each grown by the playtest's player
 * to its year, then set up for the challenge, and saved as
 * files/challenges/<id>.infill. Only runs with CHALLENGES_OUT set, to the
 * folder to write them to:
 *   CHALLENGES_OUT=shared/src/commonMain/composeResources/files/challenges ./gradlew :sim:jvmTest --tests '*ChallengeMaker*'
 */
class ChallengeMaker {
    private val out = System.getenv("CHALLENGES_OUT")?.let { File(it) }

    /** CHALLENGES_ONLY picks some by id, comma separated; otherwise all are made. */
    private val only = System.getenv("CHALLENGES_ONLY")?.split(",")?.toSet()

    @Test
    fun make() {
        val dir = out ?: return
        dir.mkdirs()
        val grid = TerrainOptions(water = 10, trees = 30, river = false)
        val river = TerrainOptions(water = 30, trees = 40, river = true)
        // The wettest climate, for a river town that floods.
        val wet = TerrainOptions(water = 30, trees = 40, river = true, climate = Climate.COASTAL)
        val threads = listOf(
            Thread { town(dir, Challenge.STREETCAR_SUBURB, 41, grid, 1905) },
            // Drains and banks the player built are taken out again, so the river floods as it would have.
            Thread { town(dir, Challenge.THE_RIVER_RISES, 23, wet, 1925) { c -> c.map.bank.fill(0); c.map.stormPipe.fill(0) } },
            Thread {
                town(dir, Challenge.INTO_THE_RED, 11, grid, 1950) { c ->
                    // Deep in debt, past the limit, so the overseer comes in at the turn of the month.
                    City::class.java.getDeclaredField("funds").apply { isAccessible = true }.setLong(c, -c.debtLimit() * 3 / 2)
                    City::class.java.getDeclaredField("rating").apply { isAccessible = true }.setInt(c, Bonds.RATINGS.size - 2)
                }
            },
            Thread {
                town(dir, Challenge.RENEWAL, 37, grid, 1970, rail = true) { c ->
                    // Taxes up and the people sour on it.
                    c.residentialTax = 11; c.commercialTax = 11; c.industrialTax = 11
                    c.approval = 45
                }
            },
            Thread { town(dir, Challenge.GREEN_CITY, 13, grid, 2005) },
        )
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    /** Grows a town from [seed] on [land] to July of [year], sets it up with [setUp], and saves it for [challenge]. */
    private fun town(dir: File, challenge: Challenge, seed: Long, land: TerrainOptions, year: Int, rail: Boolean = false, setUp: (City) -> Unit = {}) {
        if (only != null && challenge.id !in only) return
        val c = City(seed, 128, 128, land)
        val p = Player(c, rail) {}
        p.start()
        while (c.year < year || c.month < 6) {
            repeat(Balance.DEMAND_DAYS) { c.tick() }
            p.week()
            c.takeEvents { }
        }
        setUp(c)
        c.takeEvents { }
        File(dir, "${challenge.id}.infill").writeBytes(SaveGame.write(c))
        println("${challenge.id}: ${c.name}, ${c.year}, ${c.stats.population} people, funds ${c.funds}")
    }
}
