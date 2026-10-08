package com.rm.infill.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rm.infill.ui.theme.Infill
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

/**
 * A one-line text field that a controller or the arrow keys can pass over:
 * moving onto it doesn't bring up the keyboard, so the way on isn't blocked.
 * A tap, or Enter or A on it, starts typing, and Done stops. With
 * [another], a button at its end picks another value, said as [anotherLabel].
 */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    anotherLabel: String = "",
    another: (() -> Unit)? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var typing by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onValueChange, label = { Text(label) }, singleLine = true, readOnly = !typing,
        trailingIcon = another?.let { roll ->
            {
                GlyphIcon(
                    Glyph.Renew, Infill.colors.text,
                    Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = anotherLabel, role = Role.Button) {
                            // A tap here isn't a tap to type.
                            typing = false
                            roll()
                        }
                        .padding(11.dp)
                        .semantics { contentDescription = anotherLabel },
                )
            }
        },
        keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            typing = false
            keyboard?.hide()
        }),
        modifier = modifier
            .onFocusChanged { if (!it.isFocused) typing = false }
            .onPreviewKeyEvent { e ->
                val start = e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter || e.key == Key.ButtonA
                if (!typing && start && e.type == KeyEventType.KeyDown) {
                    typing = true
                    keyboard?.show()
                    true
                } else false
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    typing = true
                }
            },
    )
}
