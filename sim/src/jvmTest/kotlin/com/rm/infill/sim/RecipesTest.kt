package com.rm.infill.sim

import com.rm.infill.sound.Buses
import com.rm.infill.sound.Materials
import com.rm.infill.sound.Recipes
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipesTest {
    /** Every `constexpr int NAME = n;` in a namespace of recipes.h. */
    private fun header(namespace: String): Map<String, Int> {
        val text = File("../app/src/main/cpp/synth/recipes.h").readText()
        val body = text.substringAfter("namespace $namespace {").substringBefore("}  // namespace $namespace")
        return Regex("""constexpr int ([A-Z_]+) = (\d+);""").findAll(body).associate { it.groupValues[1] to it.groupValues[2].toInt() }
    }

    private fun kotlin(o: Any): Map<String, Int> =
        o.javaClass.declaredFields.filter { it.type == Int::class.javaPrimitiveType && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .associate { it.isAccessible = true; it.name to it.getInt(null) }

    @Test
    fun theSynthAndTheGameAgreeOnTheSounds() {
        assertEquals(header("recipe") - "LAST_CONTINUOUS", kotlin(Recipes))
        assertEquals(header("material"), kotlin(Materials))
        assertEquals(header("bus"), kotlin(Buses))
    }
}
