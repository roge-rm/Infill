package com.rm.infill.ui

import com.rm.infill.res.manual_language
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rm.infill.platform.BackButton
import com.rm.infill.platform.platform
import com.rm.infill.res.Res
import com.rm.infill.res.help
import com.rm.infill.ui.theme.Infill

/**
 * The manual, in the game. Its words come from manual/ by
 * tools/gen_manual.py, so don't write manual text here. The contents first,
 * then a section at a time; [start] opens straight at the section with that
 * id, and closing it then closes the help.
 */
@Composable
fun HelpWindow(onClose: () -> Unit, start: String? = null) {
    // The manual in the language the game's in.
    val sections = Manual.sections(stringResource(Res.string.manual_language))
    var reading by remember { mutableStateOf(start?.let { t -> sections.firstOrNull { it.id == t } }) }
    var page by remember { mutableStateOf<ManualSection?>(null) }
    val desktop = platform.onDesktop
    val open = page ?: reading
    val back = {
        when {
            page != null -> page = null
            reading != null && start == null -> reading = null
            else -> onClose()
        }
    }
    // Back steps out of a section to the contents, then closes the help.
    BackButton(enabled = true) { back() }
    if (open == null) {
        Window(Res.string.help, onClose, Glyph.Book) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (section in sections) ContentsRow(section.title, section.summary(desktop)) { reading = section }
            }
        }
        return
    }
    WindowFrame(open.title, back, Glyph.Book) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (block in open.blocks) ManualLine(block, desktop)
            for (child in open.children) ContentsRow(child.title, child.summary(desktop)) { page = child }
        }
    }
}

@Composable
private fun ContentsRow(title: String, summary: String, onClick: () -> Unit) {
    val c = Infill.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (summary.isNotEmpty()) Text(summary, color = c.textDim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text("›", color = c.textDim, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** One line of the manual, drawn the way its mark says. A line only for the other platform is empty here and not drawn. */
@Composable
private fun ManualLine(block: ManualBlock, desktop: Boolean) {
    val c = Infill.colors
    val words = block.text(desktop)
    if (words.isEmpty()) return
    when (block.kind) {
        ManualKind.Heading -> Text(
            words.uppercase(), color = c.textDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        ManualKind.Subheading -> Text(words, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        ManualKind.Para -> Text(inline(words), color = c.text, fontSize = 14.sp, lineHeight = 20.sp)
        ManualKind.Bullet -> Row(Modifier.fillMaxWidth()) {
            Text("•", color = c.accent, fontSize = 14.sp, modifier = Modifier.width(16.dp))
            Text(inline(words), color = c.text, fontSize = 14.sp, lineHeight = 20.sp)
        }
        ManualKind.Step -> Row(Modifier.fillMaxWidth()) {
            Text("›", color = c.accent, fontSize = 14.sp, modifier = Modifier.width(16.dp))
            Text(inline(words), color = c.text, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

/** Draws `**bold**` and `` `code` `` instead of printing the marks. */
private fun inline(source: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < source.length) {
        val bold = source.indexOf("**", i)
        val code = source.indexOf('`', i)
        val next = listOf(bold, code).filter { it >= 0 }.minOrNull()
        if (next == null) {
            append(source.substring(i))
            return@buildAnnotatedString
        }
        append(source.substring(i, next))
        val (mark, style) = if (next == bold) "**" to SpanStyle(fontWeight = FontWeight.Bold) else "`" to SpanStyle(fontFamily = FontFamily.Monospace)
        val end = source.indexOf(mark, next + mark.length)
        if (end < 0) {
            // An unclosed mark is just punctuation.
            append(source.substring(next))
            return@buildAnnotatedString
        }
        pushStyle(style)
        append(source.substring(next + mark.length, end))
        pop()
        i = end + mark.length
    }
}
