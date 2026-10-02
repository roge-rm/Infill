package com.rm.infill.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.map.imageBitmapOf
import com.rm.infill.res.*
import com.rm.infill.sim.Region
import com.rm.infill.sim.Terrain
import com.rm.infill.sim.TownNames
import com.rm.infill.sim.Zone
import com.rm.infill.ui.theme.Infill
import org.jetbrains.compose.resources.stringResource

/**
 * A region: its nine squares as one map, the towns as they were left and the
 * open land of the rest. Tap a square to pick it, then play its town or found
 * one there.
 */
@Composable
fun RegionScreen(region: Region, onPlay: (Int) -> Unit, onFound: (Int, String) -> Unit, onBack: () -> Unit) {
    val c = Infill.colors
    var chosen by remember { mutableIntStateOf(region.towns.indexOfFirst { it != null }.let { if (it < 0) (region.size / 2) * (region.size + 1) else it }) }
    val picture = remember(region) { regionImage(region) }
    var typed by remember(chosen) { mutableStateOf<String?>(null) }
    Page {
        Text(region.name, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(c.button)
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        val x = (at.x / size.width * region.size).toInt().coerceIn(0, region.size - 1)
                        val y = (at.y / size.height * region.size).toInt().coerceIn(0, region.size - 1)
                        chosen = y * region.size + x
                    }
                },
        ) {
            Image(picture, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
            // The squares, the chosen one picked out.
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / region.size
                for (k in 1 until region.size) {
                    drawLine(GRID, androidx.compose.ui.geometry.Offset(k * cell, 0f), androidx.compose.ui.geometry.Offset(k * cell, size.height), 2.dp.toPx())
                    drawLine(GRID, androidx.compose.ui.geometry.Offset(0f, k * cell), androidx.compose.ui.geometry.Offset(size.width, k * cell), 2.dp.toPx())
                }
                val x = chosen % region.size
                val y = chosen / region.size
                drawRect(
                    PICKED, androidx.compose.ui.geometry.Offset(x * cell, y * cell), androidx.compose.ui.geometry.Size(cell, cell),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()),
                )
            }
        }
        val town = region.towns[chosen]
        if (town != null) {
            Text(town.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(summaryLine(com.rm.infill.sim.SaveSummary(town.name, town.year, town.month, town.population, 0)), color = c.textDim, fontSize = 14.sp)
            BigButton(stringResource(Res.string.play_town, town.name), primary = true) { onPlay(chosen) }
        } else {
            Text(stringResource(Res.string.no_town_here), color = c.textDim, fontSize = 15.sp)
            val name = typed ?: TownNames.make(region.seed + chosen * 31L)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { typed = it.take(30) }, label = { Text(stringResource(Res.string.city_name)) }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.widthIn(max = 160.dp)) {
                    BigButton(stringResource(Res.string.another_name)) { typed = TownNames.make(kotlin.random.Random.nextLong(1, Long.MAX_VALUE)) }
                }
            }
            BigButton(stringResource(Res.string.found_town), primary = true) { onFound(chosen, name.ifBlank { TownNames.make(region.seed + chosen) }) }
        }
        BigButton(stringResource(Res.string.back), onClick = onBack)
    }
}

/** The whole region one pixel a tile: open land as it lies, and each town's roads, zones and buildings. */
fun regionImage(region: Region): ImageBitmap {
    val side = region.side
    val width = side * region.size
    val land = region.whole()
    val pixels = IntArray(width * width) { i ->
        val x = i % width
        val y = i / width
        val town = region.towns[(y / side) * region.size + x / side]
        if (town != null) {
            val look = town.picture[(y % side) * side + x % side]
            lookColour(look)
        } else {
            // Open land, a little greyed so the towns stand out.
            when (land.terrain[i]) {
                Terrain.WATER -> 0xFF4A76AE.toInt()
                Terrain.TREES -> 0xFF46684A.toInt()
                else -> 0xFF7C9466.toInt()
            }
        }
    }
    return imageBitmapOf(pixels, width, width)
}

private fun lookColour(look: Byte): Int = when (look) {
    Region.LOOK_WATER -> 0xFF3A6FB0.toInt()
    Region.LOOK_TREES -> 0xFF2F6B2A.toInt()
    Region.LOOK_ROAD -> 0xFF4A4A4E.toInt()
    Region.LOOK_RAIL -> 0xFF6A5A48.toInt()
    Region.LOOK_LAND -> 0xFF5A9A3C.toInt()
    else -> {
        val built = look >= Region.LOOK_BUILT
        val zone = (look - if (built) Region.LOOK_BUILT else Region.LOOK_ZONED).toByte()
        val base = when (zone) {
            Zone.RESIDENTIAL -> 0x4CC23A
            Zone.COMMERCIAL -> 0x3C78D7
            Zone.INDUSTRIAL -> 0xDCAA28
            Zone.FARMLAND -> 0xA6703C
            Zone.OFFICE -> 0x9B5CC8
            Zone.MIXED -> 0x1FA89A
            else -> 0xB8B2A6
        }
        // Built on shows strong; only zoned shows pale.
        if (built) 0xFF000000.toInt() or base else 0xFF000000.toInt() or blend(base, 0x5A9A3C)
    }
}

private fun blend(a: Int, b: Int): Int {
    fun ch(v: Int, s: Int) = (v shr s) and 0xff
    return (((ch(a, 16) + ch(b, 16)) / 2) shl 16) or (((ch(a, 8) + ch(b, 8)) / 2) shl 8) or ((ch(a, 0) + ch(b, 0)) / 2)
}

private val GRID = androidx.compose.ui.graphics.Color(0xCCF2EEE4)
private val PICKED = androidx.compose.ui.graphics.Color(0xFFE0A000)
