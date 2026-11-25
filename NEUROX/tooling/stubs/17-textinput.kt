// NEUROX offline compile stubs - text input.
package androidx.compose.ui.text.input

import androidx.compose.ui.text.TextStyle

class KeyboardOptions(
    val capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    val autoCorrect: Boolean = true,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val imeAction: ImeAction = ImeAction.Default,
)

enum class KeyboardType {
    Text, Ascii, Number, Phone, Uri, Email, Password, NumberPassword, Decimal,
}

enum class ImeAction { Default, None, Go, Search, Send, Previous, Next, Done }

enum class KeyboardCapitalization { None, Characters, Words, Sentences }

interface VisualTransformation {
    companion object {
        val None: VisualTransformation = object : VisualTransformation {}
    }
}

class PasswordVisualTransformation(val mask: Char = '\u2022') : VisualTransformation

class TextFieldValue(
    val text: String = "",
    val selection: TextRange = TextRange.Zero,
)

class TextRange(val start: Int, val end: Int) {
    companion object {
        val Zero = TextRange(0, 0)
    }
}
