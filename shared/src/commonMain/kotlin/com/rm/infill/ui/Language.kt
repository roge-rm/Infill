package com.rm.infill.ui

import androidx.compose.runtime.Composable
import com.rm.infill.res.Res
import com.rm.infill.res.number_decimal
import com.rm.infill.res.number_group
import com.rm.infill.res.town_after
import com.rm.infill.res.town_after_pattern
import com.rm.infill.res.town_before
import com.rm.infill.res.town_before_pattern
import com.rm.infill.res.town_endings
import com.rm.infill.res.town_first
import com.rm.infill.res.town_places
import com.rm.infill.sim.NameParts
import com.rm.infill.sim.TownNames
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * Everything in [content] in [language] ("en"), or the phone's own language
 * when it's empty. The numbers and the names of new towns follow it too.
 */
@Composable
fun InLanguage(language: String, content: @Composable () -> Unit) {
    InPlatformLanguage(language) {
        Numbers.group = stringResource(Res.string.number_group)
        Numbers.decimal = stringResource(Res.string.number_decimal)
        TownNames.parts = NameParts(
            first = stringArrayResource(Res.array.town_first),
            endings = stringArrayResource(Res.array.town_endings),
            after = stringArrayResource(Res.array.town_after),
            before = stringArrayResource(Res.array.town_before),
            places = stringArrayResource(Res.array.town_places),
            afterPattern = stringResource(Res.string.town_after_pattern),
            beforePattern = stringResource(Res.string.town_before_pattern),
        ).takeIf { it.first.isNotEmpty() && it.endings.isNotEmpty() } ?: NameParts.ENGLISH
        content()
    }
}

/**
 * [content] with the platform set to [language], or its own when empty, so
 * the strings are looked up in it. The app's density changes with it, which
 * has every string looked up again.
 */
@Composable
expect fun InPlatformLanguage(language: String, content: @Composable () -> Unit)
