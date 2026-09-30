package com.rm.infill.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TownNamesTest {
    @Test
    fun theSameNumberGivesTheSameName() {
        for (n in 1L..50L) assertEquals(TownNames.make(n), TownNames.make(n))
    }

    @Test
    fun namesVaryAndReadWell() {
        val names = (1L..400L).map { TownNames.make(it) }
        assertTrue(names.toSet().size > 300, "only ${names.toSet().size} different names in 400")
        for (name in names) {
            assertTrue(name.isNotBlank() && name.length <= 20, name)
            assertTrue(name[0].isUpperCase(), name)
        }
        println(names.take(40).joinToString(", "))
    }
}
