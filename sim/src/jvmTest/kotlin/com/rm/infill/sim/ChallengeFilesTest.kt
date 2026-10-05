package com.rm.infill.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The challenge towns the app carries, saved by older versions, still load and run. */
class ChallengeFilesTest {
    @Test
    fun theBundledTownsLoadAndRun() {
        val dir = listOf("../shared/src/commonMain/composeResources/files/challenges", "shared/src/commonMain/composeResources/files/challenges")
            .map { File(it) }.first { it.isDirectory }
        val files = dir.listFiles { f -> f.name.endsWith(".infill") }.orEmpty()
        assertTrue(files.size == Challenge.entries.size, "${files.size} files")
        for (f in files) {
            val c = SaveGame.read(f.readBytes())
            repeat(40) { c.tick() }
            assertTrue(c.stats.population > 1000, "${f.name}: ${c.stats.population}")
        }
    }
}
