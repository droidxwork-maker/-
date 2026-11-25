package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily

private sealed interface MessageSegment {
    data class Prose(val markdown: String) : MessageSegment
    data class CodeBlock(val language: String, val code: String) : MessageSegment
}

@Composable
fun MarkdownMessageView(
    text: String,
    textColor: Color,
    onCodeCopied: () -> Unit = {}
) {
    val segments = remember(text) { parseMarkdownSegments(text) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.Prose -> {
                    ProseBlock(markdown = segment.markdown, textColor = textColor)
                }
                is MessageSegment.CodeBlock -> {
                    CodeBlockCard(
                        language = segment.language,
                        code = segment.code,
                        onCopied = onCodeCopied
                    )
                }
            }
        }
    }
}

@Composable
private fun ProseBlock(markdown: String, textColor: Color) {
    val lines = markdown.lines()
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { rawLine ->
                val line = rawLine.trimEnd()
                when {
                    line.startsWith("### ") -> {
                        Text(
                            text = line.removePrefix("### ").trim(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    line.startsWith("## ") -> {
                        Text(
                            text = line.removePrefix("## ").trim(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    line.startsWith("# ") -> {
                        Text(
                            text = line.removePrefix("# ").trim(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    line.startsWith("- ") || line.startsWith("* ") -> {
                        val content = line.drop(2).trim()
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = formatInlineMarkdown(content, inlineCodeBg, accentColor),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    line.isNotBlank() -> {
                        Text(
                            text = formatInlineMarkdown(line, inlineCodeBg, accentColor),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                    else -> {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    onCopied: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF070B14),
        border = BorderStroke(1.dp, Color(0xFF23304F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = language.ifBlank { "code" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        onCopied()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "کپی کد",
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "کپی کد",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF22D3EE)
                    )
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                SelectionContainer {
                    Text(
                        text = code,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatInlineMarkdown(
    text: String,
    inlineCodeBg: Color,
    accentColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            background = inlineCodeBg,
                            color = accentColor,
                            fontSize = 13.sp
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}

private fun parseMarkdownSegments(input: String): List<MessageSegment> {
    val result = mutableListOf<MessageSegment>()
    val regex = Regex("```([a-zA-Z0-9_+.-]*)\\n([\\s\\S]*?)```")
    var lastIndex = 0
    for (match in regex.findAll(input)) {
        if (match.range.first > lastIndex) {
            val prose = input.substring(lastIndex, match.range.first).trim()
            if (prose.isNotEmpty()) {
                result.add(MessageSegment.Prose(prose))
            }
        }
        val lang = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        result.add(MessageSegment.CodeBlock(lang, code))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < input.length) {
        val tail = input.substring(lastIndex).trim()
        if (tail.isNotEmpty()) {
            result.add(MessageSegment.Prose(tail))
        }
    }
    if (result.isEmpty() && input.isNotBlank()) {
        result.add(MessageSegment.Prose(input))
    }
    return result
}
