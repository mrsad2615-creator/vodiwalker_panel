package ai.dana.ui

import ai.dana.R
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private data class Block(val content: String, val language: String? = null)

/** Render a bounded Markdown subset locally. No HTML, executable code, or automatic link opening. */
@Composable
internal fun RichAnswer(raw: String) {
    val blocks = remember(raw) { parse(raw) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            if (block.language == null) {
                val styled = remember(block.content) { buildAnnotatedString {
                    for ((i, line) in block.content.lines().withIndex()) {
                        if (i > 0) append('\n')
                        val part = line.trimStart()
                        val heading = part.startsWith("# ") || part.startsWith("## ") || part.startsWith("### ")
                        if (heading) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part.substringAfter(' ')) }
                        else {
                            val text = if (part.startsWith("- ") || part.startsWith("* ")) "• " + part.drop(2) else line
                            var cursor = 0
                            for (match in emphasis.findAll(text)) {
                                append(text.substring(cursor, match.range.first))
                                if (match.value.startsWith("**")) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.value.drop(2).dropLast(2)) }
                                else withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Ice)) { append(match.value.drop(1).dropLast(1)) }
                                cursor = match.range.last + 1
                            }
                            append(text.substring(cursor))
                        }
                    }
                } }
                SelectionContainer { Text(styled, style = MaterialTheme.typography.bodyLarge) }
            } else {
                Column(Modifier.fillMaxWidth().background(Obsidian, RoundedCornerShape(10.dp)).padding(10.dp)) {
                    if (block.language.isNotBlank()) TechnicalText(block.language)
                    SelectionContainer {
                        Text(block.content, modifier = Modifier.horizontalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr,
                        ))
                    }
                    CopyReply(block.content)
                }
            }
        }
    }
}

private val emphasis = Regex("\\*\\*[^*\\n]{1,180}\\*\\*|`[^`\\n]{1,180}`")
private fun parse(raw: String): List<Block> {
    val out = mutableListOf<Block>()
    val buffer = StringBuilder()
    var language: String? = null
    for (line in raw.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (buffer.isNotEmpty()) out.add(Block(buffer.toString().trimEnd('\n'), language))
            buffer.setLength(0)
            language = if (language != null) null else line.trimStart().drop(3).take(32).filter { !it.isISOControl() }
        } else buffer.appendLine(line)
    }
    if (buffer.isNotEmpty()) out.add(Block(buffer.toString().trimEnd('\n'), language))
    return out
}

@Composable
internal fun CopyReply(text: String) {
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val label = stringResource(R.string.app_name)
    var copied by remember(text) { mutableStateOf(false) }
    TextButton(enabled = clipboard != null && text.isNotEmpty(), onClick = {
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard?.setPrimaryClip(clip)
        copied = true
    }) { Text(stringResource(if (copied) R.string.studio_copied else R.string.studio_copy)) }
}
