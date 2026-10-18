package com.rm.infill.platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopPlatformTest {
    @Test
    fun savesAndSettingsSurviveRestart() {
        val folder = Files.createTempDirectory("infill-platform").toFile()
        try {
            val first = DesktopPlatform(folder)
            first.writeSave("My town", byteArrayOf(1, 2, 3))
            first.writeSave("My town", byteArrayOf(4, 5))
            first.setSetting("language", "fr")
            val second = DesktopPlatform(folder)
            assertEquals(listOf("my-town"), second.saves())
            assertContentEquals(byteArrayOf(4, 5), second.readSave("my-town"))
            assertEquals("fr", second.setting("language"))
            second.setSetting("language", null)
            second.deleteSave("my-town")
            val third = DesktopPlatform(folder)
            assertEquals(emptyList(), third.saves())
            assertNull(third.readSave("missing"))
            assertNull(third.setting("language"))
            assertEquals(listOf("settings.properties"), folder.listFiles()!!.filter { it.isFile }.map { it.name }.sorted())
        } finally { folder.deleteRecursively() }
    }

    @Test
    fun saveNamesStayInsideSaveFolder() {
        val folder = Files.createTempDirectory("infill-platform").toFile()
        try {
            val platform = DesktopPlatform(folder)
            platform.writeSave("../../outside", byteArrayOf(7))
            assertEquals(listOf("outside"), platform.saves())
            assertContentEquals(byteArrayOf(7), platform.readSave("outside"))
        } finally { folder.deleteRecursively() }
    }
}
