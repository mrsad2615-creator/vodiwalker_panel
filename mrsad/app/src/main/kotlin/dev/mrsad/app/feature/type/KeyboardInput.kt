package dev.mrsad.app.feature.type

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Bridges Android IME input into the typing engine.
 *
 * Why not read the text field directly? Soft keyboards (especially Persian
 * IMEs) compose and replace text; diffing raw field values would double-count
 * or lose keystrokes. Instead we diff the *new value against the previous one*
 * and forward only genuinely added characters, in order. The field stays empty
 * from the app's point of view (matched characters are consumed immediately),
 * which also means the OS never shows a stale composition.
 */
@Stable
class KeyboardInputState {

    private val eventsChannel = Channel<Char>(
        capacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: Flow<Char> = eventsChannel.receiveAsFlow()

    /** The field value is reset after every capture so only *new* input remains. */
    var buffer: TextFieldValue = TextFieldValue("")
        private set

    fun capture(newValue: TextFieldValue) {
        val oldLength = buffer.text.length
        val newText = newValue.text
        if (newText.length > oldLength) {
            newText.substring(oldLength).forEach { char ->
                if (!char.isISOControl()) eventsChannel.trySend(char)
            }
        }
        // Always start from an empty field: the engine owns the visible state.
        buffer = TextFieldValue(text = "", selection = TextRange(0))
    }
}
