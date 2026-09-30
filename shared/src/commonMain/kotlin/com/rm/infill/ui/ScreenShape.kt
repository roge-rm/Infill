package com.rm.infill.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ScreenShape { Tall, Wide, Square }

/** How the screen is laid out, from the window's size. */
data class ScreenLayout(val shape: ScreenShape, val large: Boolean, val short: Boolean, val narrow: Boolean) {
    /** Square phones get smaller buttons and tighter bars. */
    val compact get() = shape == ScreenShape.Square && !large
}

fun screenLayout(width: Dp, height: Dp): ScreenLayout {
    val longSide = maxOf(width, height)
    val shortSide = minOf(width, height)
    val shape = when {
        shortSide <= 0.dp -> ScreenShape.Tall
        longSide / shortSide < SQUARE_RATIO && shortSide < LARGE_SHORT -> ScreenShape.Square
        width > height -> ScreenShape.Wide
        else -> ScreenShape.Tall
    }
    return ScreenLayout(shape, large = shortSide >= LARGE_SHORT, short = height < SHORT_HEIGHT, narrow = width < NARROW_WIDTH)
}

/** The squarest ordinary phone is past 1.9 and the square ones are under 1.2. */
private const val SQUARE_RATIO = 1.4f

/** A phone on its side: the tool rail needs its smaller buttons to fit. */
private val SHORT_HEIGHT = 480.dp

/** An upright phone too narrow for the full size toolbar with undo and redo on it. */
private val NARROW_WIDTH = 420.dp

/** A tablet, the same line as Android's sw600dp. */
private val LARGE_SHORT = 600.dp
