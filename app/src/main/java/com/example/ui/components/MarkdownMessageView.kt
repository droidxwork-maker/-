package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VazirmatnFontFamily
import kotlinx.coroutines.delay

private enum class TableAlign { START, CENTER, END }

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class UnorderedListItem(val indentLevel: Int, val text: String) : MarkdownBlock
    data class OrderedListItem(val indentLevel: Int, val numberLabel: String, val text: String) : MarkdownBlock
    data class Blockquote(val lines: List<String>) : MarkdownBlock
    data object Divider : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TableAlign>,
        val rows: List<List<String>>
    ) : MarkdownBlock
}

@Composable
fun MarkdownMessageView(
    text: String,
    textColor: Color,
    onCodeCopied: () -> Unit = {}
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    val accentColor = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.secondary

    val bodyTextStyle = TextStyle(
        fontFamily = VazirmatnFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 28.sp
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val (fontSize, lineHeight, topPad, headingColor) = when (block.level) {
                        1 -> HeadingStyleSpec(22.sp, 32.sp, 6.dp, textColor)
                        2 -> HeadingStyleSpec(19.5.sp, 29.sp, 5.dp, textColor)
                        3 -> HeadingStyleSpec(17.5.sp, 26.sp, 4.dp, accentColor)
                        else -> HeadingStyleSpec(16.5.sp, 25.sp, 2.dp, secondaryAccent)
                    }
                    SelectionContainer {
                        Text(
                            text = formatInlineMarkdown(block.text, inlineCodeBg, accentColor),
                            style = TextStyle(
                                fontFamily = VazirmatnFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSize,
                                lineHeight = lineHeight
                            ),
                            color = headingColor,
                            modifier = Modifier.padding(top = topPad)
                        )
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    SelectionContainer {
                        Text(
                            text = formatInlineMarkdown(block.text, inlineCodeBg, accentColor),
                            style = bodyTextStyle,
                            color = textColor
                        )
                    }
                }

                is MarkdownBlock.UnorderedListItem -> {
                    val indentDp = (block.indentLevel * 16).dp
                    SelectionContainer {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = indentDp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = if (block.indentLevel == 0) "•" else "◦",
                                style = bodyTextStyle.copy(fontWeight = FontWeight.Bold),
                                color = accentColor,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Text(
                                text = formatInlineMarkdown(block.text, inlineCodeBg, accentColor),
                                style = bodyTextStyle,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                is MarkdownBlock.OrderedListItem -> {
                    val indentDp = (block.indentLevel * 16).dp
                    SelectionContainer {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = indentDp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${block.numberLabel}.",
                                style = bodyTextStyle.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = accentColor,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Text(
                                text = formatInlineMarkdown(block.text, inlineCodeBg, accentColor),
                                style = bodyTextStyle,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                                    .background(accentColor)
                            )
                            SelectionContainer {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    block.lines.forEach { quoteLine ->
                                        Text(
                                            text = formatInlineMarkdown(quoteLine, inlineCodeBg, accentColor),
                                            style = bodyTextStyle.copy(fontSize = 15.5.sp, lineHeight = 26.sp),
                                            color = textColor.copy(alpha = 0.92f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(
                        language = block.language,
                        code = block.code,
                        onCopied = onCodeCopied
                    )
                }

                is MarkdownBlock.Table -> {
                    MarkdownTableView(
                        table = block,
                        textColor = textColor,
                        inlineCodeBg = inlineCodeBg,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

private data class HeadingStyleSpec(
    val fontSize: androidx.compose.ui.unit.TextUnit,
    val lineHeight: androidx.compose.ui.unit.TextUnit,
    val topPad: Dp,
    val color: Color
)

@Composable
private fun MarkdownTableView(
    table: MarkdownBlock.Table,
    textColor: Color,
    inlineCodeBg: Color,
    accentColor: Color
) {
    val colCount = maxOf(
        table.headers.size,
        table.rows.maxOfOrNull { it.size } ?: 0
    ).coerceAtLeast(1)

    val columnWidths = remember(table) {
        (0 until colCount).map { colIdx ->
            val headerLen = table.headers.getOrNull(colIdx)?.length ?: 0
            val maxRowLen = table.rows.maxOfOrNull { it.getOrNull(colIdx)?.length ?: 0 } ?: 0
            val longest = maxOf(headerLen, maxRowLen)
            (longest * 9 + 34).coerceIn(104, 240).dp
        }
    }

    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    val evenRowBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    val oddRowBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .background(headerBg)
                        .height(IntrinsicSize.Min)
                ) {
                    for (col in 0 until colCount) {
                        val cellText = table.headers.getOrNull(col).orEmpty()
                        val align = table.alignments.getOrNull(col) ?: TableAlign.START
                        TableCell(
                            text = formatInlineMarkdown(cellText, inlineCodeBg, accentColor),
                            width = columnWidths[col],
                            textAlign = when (align) {
                                TableAlign.CENTER -> TextAlign.Center
                                TableAlign.END -> TextAlign.End
                                TableAlign.START -> TextAlign.Start
                            },
                            isHeader = true,
                            textColor = textColor,
                            showEndDivider = col < colCount - 1,
                            borderColor = borderColor
                        )
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = borderColor)

                // Data Rows
                table.rows.forEachIndexed { rowIndex, row ->
                    val rowBg = if (rowIndex % 2 == 0) evenRowBg else oddRowBg
                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .height(IntrinsicSize.Min)
                    ) {
                        for (col in 0 until colCount) {
                            val cellText = row.getOrNull(col).orEmpty()
                            val align = table.alignments.getOrNull(col) ?: TableAlign.START
                            TableCell(
                                text = formatInlineMarkdown(cellText, inlineCodeBg, accentColor),
                                width = columnWidths[col],
                                textAlign = when (align) {
                                    TableAlign.CENTER -> TextAlign.Center
                                    TableAlign.END -> TextAlign.End
                                    TableAlign.START -> TextAlign.Start
                                },
                                isHeader = false,
                                textColor = textColor,
                                showEndDivider = col < colCount - 1,
                                borderColor = borderColor
                            )
                        }
                    }
                    if (rowIndex < table.rows.lastIndex) {
                        HorizontalDivider(thickness = 0.8.dp, color = borderColor.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: AnnotatedString,
    width: Dp,
    textAlign: TextAlign,
    isHeader: Boolean,
    textColor: Color,
    showEndDivider: Boolean,
    borderColor: Color
) {
    Row(modifier = Modifier.width(width).fillMaxHeight()) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = VazirmatnFontFamily,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isHeader) 14.5.sp else 14.sp,
                lineHeight = 22.sp,
                textAlign = textAlign
            ),
            color = textColor,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
        if (showEndDivider) {
            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .fillMaxHeight()
                    .background(borderColor.copy(alpha = 0.65f))
            )
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
    var justCopied by remember { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1600)
            justCopied = false
        }
    }

    val highlighted = remember(code, language) {
        highlightCodeSyntax(code = code, language = language)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0A0F1D),
        border = BorderStroke(1.dp, Color(0xFF23304F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111A2E))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = language.ifBlank { "code" }.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        justCopied = true
                        onCopied()
                    }
                ) {
                    Icon(
                        imageVector = if (justCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "کپی کد",
                        tint = if (justCopied) Color(0xFF34D399) else Color(0xFF22D3EE),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (justCopied) "کپی شد" else "کپی کد",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (justCopied) Color(0xFF34D399) else Color(0xFF22D3EE)
                    )
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                SelectionContainer {
                    Text(
                        text = highlighted,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}

private val CODE_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "enum", "sealed", "data",
    "return", "if", "else", "when", "for", "while", "do", "in", "is", "as", "by",
    "import", "package", "private", "public", "internal", "protected", "override",
    "suspend", "async", "await", "try", "catch", "finally", "throw", "true", "false",
    "null", "None", "True", "False", "def", "lambda", "with", "yield", "from",
    "function", "const", "let", "export", "default", "switch", "case", "break",
    "continue", "new", "this", "super", "extends", "implements", "typeof", "void",
    "struct", "impl", "fn", "mut", "pub", "use", "match", "type", "static", "final",
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
    "JOIN", "LEFT", "RIGHT", "INNER", "ON", "GROUP", "ORDER", "BY", "LIMIT", "CREATE",
    "TABLE", "AND", "OR", "NOT", "NULL", "AS", "select", "from", "where", "join"
)

private val SyntaxCommentColor = Color(0xFF64748B)
private val SyntaxStringColor = Color(0xFFA5E887)
private val SyntaxKeywordColor = Color(0xFFC792EA)
private val SyntaxNumberColor = Color(0xFFF78C6C)
private val SyntaxAnnotationColor = Color(0xFFFFCB6B)
private val SyntaxTypeColor = Color(0xFF82AAFF)
private val SyntaxFunctionColor = Color(0xFF7FDBCA)
private val SyntaxDefaultColor = Color(0xFFE2E8F0)

private fun highlightCodeSyntax(code: String, language: String): AnnotatedString {
    val lang = language.lowercase()
    val usesHashComments = lang in setOf("py", "python", "sh", "bash", "zsh", "yaml", "yml", "toml", "ruby", "rb")
    val usesSqlComments = lang in setOf("sql", "psql", "sqlite")

    return buildAnnotatedString {
        var i = 0
        val n = code.length

        while (i < n) {
            val ch = code[i]

            // Block comments /* ... */
            if (ch == '/' && i + 1 < n && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2).let { if (it == -1) n else it + 2 }
                withStyle(SpanStyle(color = SyntaxCommentColor, fontStyle = FontStyle.Italic)) {
                    append(code.substring(i, end))
                }
                i = end
                continue
            }

            // Line comments // or -- or #
            val isLineComment =
                (ch == '/' && i + 1 < n && code[i + 1] == '/') ||
                    (usesHashComments && ch == '#') ||
                    (usesSqlComments && ch == '-' && i + 1 < n && code[i + 1] == '-')
            if (isLineComment) {
                val end = code.indexOf('\n', i).let { if (it == -1) n else it }
                withStyle(SpanStyle(color = SyntaxCommentColor, fontStyle = FontStyle.Italic)) {
                    append(code.substring(i, end))
                }
                i = end
                continue
            }

            // Strings "...", '...', `...`
            if (ch == '"' || ch == '\'' || ch == '`') {
                val quote = ch
                var j = i + 1
                while (j < n) {
                    if (code[j] == '\\' && j + 1 < n) {
                        j += 2
                        continue
                    }
                    if (code[j] == quote) {
                        j++
                        break
                    }
                    if (code[j] == '\n' && quote != '`') break
                    j++
                }
                // Check if JSON property key ("key":)
                var k = j
                while (k < n && code[k] == ' ') k++
                val isJsonKey = quote == '"' && k < n && code[k] == ':'
                val color = if (isJsonKey) SyntaxAnnotationColor else SyntaxStringColor
                withStyle(SpanStyle(color = color)) {
                    append(code.substring(i, j))
                }
                i = j
                continue
            }

            // Annotations @Composable
            if (ch == '@' && i + 1 < n && (code[i + 1].isLetter() || code[i + 1] == '_')) {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                withStyle(SpanStyle(color = SyntaxAnnotationColor)) {
                    append(code.substring(i, j))
                }
                i = j
                continue
            }

            // Numbers
            if (ch.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit())) {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                withStyle(SpanStyle(color = SyntaxNumberColor)) {
                    append(code.substring(i, j))
                }
                i = j
                continue
            }

            // Identifiers, Keywords, Types, Functions
            if (ch.isLetter() || ch == '_') {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val token = code.substring(i, j)
                var k = j
                while (k < n && code[k] == ' ') k++
                val isCall = k < n && code[k] == '('

                val style = when {
                    token in CODE_KEYWORDS -> SpanStyle(color = SyntaxKeywordColor, fontWeight = FontWeight.SemiBold)
                    token.first().isUpperCase() -> SpanStyle(color = SyntaxTypeColor)
                    isCall -> SpanStyle(color = SyntaxFunctionColor)
                    else -> SpanStyle(color = SyntaxDefaultColor)
                }
                withStyle(style) {
                    append(token)
                }
                i = j
                continue
            }

            withStyle(SpanStyle(color = SyntaxDefaultColor)) {
                append(ch)
            }
            i++
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
        val n = text.length
        while (i < n) {
            // Markdown link [label](url)
            if (text[i] == '[') {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket != -1 && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val label = text.substring(i + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen).trim()
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            withLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = accentColor,
                                            textDecoration = TextDecoration.Underline,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                )
                            ) {
                                append(label)
                            }
                            i = closeParen + 1
                            continue
                        }
                    }
                }
            }

            // Bold + Italic ***text***
            if (text.startsWith("***", i)) {
                val end = text.indexOf("***", i + 3)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 3, end))
                    }
                    i = end + 3
                    continue
                }
            }

            // Bold **text** or __text__
            if (text.startsWith("**", i) || text.startsWith("__", i)) {
                val delim = text.substring(i, i + 2)
                val end = text.indexOf(delim, i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(formatInlineMarkdown(text.substring(i + 2, end), inlineCodeBg, accentColor))
                    }
                    i = end + 2
                    continue
                }
            }

            // Strikethrough ~~text~~
            if (text.startsWith("~~", i)) {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // Italic *text*
            if (text[i] == '*' && (i + 1 < n && text[i + 1] != ' ')) {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            // Inline code `code`
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            background = inlineCodeBg,
                            color = accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
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

private fun parseMarkdownBlocks(input: String): List<MarkdownBlock> {
    val lines = input.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 1. Fenced code block ```lang ... ```
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) {
                i++ // consume closing fence
            }
            blocks.add(MarkdownBlock.CodeBlock(language = lang, code = codeLines.joinToString("\n").trimEnd()))
            continue
        }

        // 2. Markdown Table: header line with '|', followed by separator line with '---' and '|'
        if (trimmed.contains("|") && i + 1 < lines.size && isTableSeparatorLine(lines[i + 1])) {
            val headers = splitTableRow(trimmed)
            val alignments = parseTableAlignments(lines[i + 1], headers.size)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size) {
                val rowTrimmed = lines[i].trim()
                if (rowTrimmed.isEmpty() || !rowTrimmed.contains("|") || rowTrimmed.startsWith("```")) {
                    break
                }
                rows.add(splitTableRow(rowTrimmed))
                i++
            }
            blocks.add(MarkdownBlock.Table(headers = headers, alignments = alignments, rows = rows))
            continue
        }

        // 3. Horizontal Divider
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 4. Headings # .. ####
        val headingMatch = Regex("^(#{1,4})\\s+(.*)$").find(trimmed)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val title = headingMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.Heading(level = level, text = title))
            i++
            continue
        }

        // 5. Blockquotes > ...
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                i++
            }
            blocks.add(MarkdownBlock.Blockquote(quoteLines))
            continue
        }

        // 6. Unordered List Item
        val ulMatch = Regex("^(\\s*)[-*+]\\s+(.*)$").find(raw)
        if (ulMatch != null) {
            val indent = (ulMatch.groupValues[1].length / 2).coerceAtMost(3)
            blocks.add(MarkdownBlock.UnorderedListItem(indentLevel = indent, text = ulMatch.groupValues[2].trim()))
            i++
            continue
        }

        // 7. Ordered List Item
        val olMatch = Regex("^(\\s*)(\\d+|[۰-۹]+)[.)]\\s+(.*)$").find(raw)
        if (olMatch != null) {
            val indent = (olMatch.groupValues[1].length / 2).coerceAtMost(3)
            blocks.add(
                MarkdownBlock.OrderedListItem(
                    indentLevel = indent,
                    numberLabel = olMatch.groupValues[2],
                    text = olMatch.groupValues[3].trim()
                )
            )
            i++
            continue
        }

        // 8. Paragraph (group consecutive plain lines until blank or special block)
        val paraLines = mutableListOf(trimmed)
        i++
        while (i < lines.size) {
            val nextRaw = lines[i]
            val nextTrimmed = nextRaw.trim()
            if (nextTrimmed.isEmpty() ||
                nextTrimmed.startsWith("```") ||
                nextTrimmed.startsWith("#") ||
                nextTrimmed.startsWith(">") ||
                nextTrimmed == "---" ||
                nextTrimmed == "***" ||
                Regex("^\\s*[-*+]\\s+").containsMatchIn(nextRaw) ||
                Regex("^\\s*(\\d+|[۰-۹]+)[.)]\\s+").containsMatchIn(nextRaw) ||
                (nextTrimmed.contains("|") && i + 1 < lines.size && isTableSeparatorLine(lines[i + 1]))
            ) {
                break
            }
            paraLines.add(nextTrimmed)
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString("\n")))
    }

    return blocks
}

private fun isTableSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("-") || !trimmed.contains("|")) return false
    val cells = splitTableRow(trimmed)
    return cells.isNotEmpty() && cells.all { cell ->
        val c = cell.trim()
        c.matches(Regex("^:?-{2,}:?$"))
    }
}

private fun parseTableAlignments(separatorLine: String, headerCount: Int): List<TableAlign> {
    val cells = splitTableRow(separatorLine)
    return (0 until headerCount).map { idx ->
        val cell = cells.getOrNull(idx)?.trim().orEmpty()
        when {
            cell.startsWith(":") && cell.endsWith(":") -> TableAlign.CENTER
            cell.endsWith(":") -> TableAlign.END
            else -> TableAlign.START
        }
    }
}

private fun splitTableRow(row: String): List<String> {
    var trimmed = row.trim()
    if (trimmed.startsWith("|")) trimmed = trimmed.drop(1)
    if (trimmed.endsWith("|")) trimmed = trimmed.dropLast(1)
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < trimmed.length) {
        if (trimmed[i] == '\\' && i + 1 < trimmed.length && trimmed[i + 1] == '|') {
            current.append('|')
            i += 2
        } else if (trimmed[i] == '|') {
            cells.add(current.toString().trim())
            current.clear()
            i++
        } else {
            current.append(trimmed[i])
            i++
        }
    }
    cells.add(current.toString().trim())
    return cells
}
