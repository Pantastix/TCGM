package de.pantastix.project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

sealed class MarkdownBlock {
    data class Text(val content: AnnotatedString) : MarkdownBlock()
    data class Image(val url: String, val alt: String) : MarkdownBlock()
    data class Table(val headers: List<AnnotatedString>, val rows: List<List<AnnotatedString>>) : MarkdownBlock()
    data class Header(val level: Int, val content: AnnotatedString) : MarkdownBlock()
    data object Divider : MarkdownBlock()
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val blocks = remember(markdown, color) { parseMarkdown(markdown, color) }
    
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Text -> {
                    Text(
                        text = block.content,
                        style = style,
                        color = color
                    )
                }
                is MarkdownBlock.Image -> {
                    MarkdownImage(url = block.url, alt = block.alt)
                }
                is MarkdownBlock.Table -> {
                    MarkdownTable(block, color)
                }
                is MarkdownBlock.Header -> {
                    val headerStyle = when (block.level) {
                        1 -> MaterialTheme.typography.headlineLarge
                        2 -> MaterialTheme.typography.headlineMedium
                        3 -> MaterialTheme.typography.headlineSmall
                        4 -> MaterialTheme.typography.titleLarge
                        5 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = block.content,
                        style = headerStyle,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = color.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.05f))
                .padding(8.dp)
        ) {
            table.headers.forEach { header ->
                Text(
                    text = header,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
        
        HorizontalDivider(color = color.copy(alpha = 0.2f))
        
        // Rows
        table.rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = color
                    )
                }
            }
            if (index < table.rows.size - 1) {
                HorizontalDivider(color = color.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
private fun HorizontalDivider(modifier: Modifier = Modifier, color: Color) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
private fun MarkdownImage(url: String, alt: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        AsyncImage(
            model = url,
            contentDescription = alt,
            modifier = Modifier
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
    }
}

private fun parseMarkdown(markdown: String, baseColor: Color): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var i = 0
    
    while (i < lines.size) {
        val line = lines[i]
        val trimmedLine = line.trim()
        
        // Header detection
        if (trimmedLine.startsWith("#")) {
            val headerMatch = Regex("^(#{1,6})\\s+(.*)$").find(trimmedLine)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val content = headerMatch.groupValues[2]
                blocks.add(MarkdownBlock.Header(level, buildAnnotatedMarkdown(content, baseColor)))
                i++
                continue
            }
        }

        // Divider detection
        if (trimmedLine.matches(Regex("^([-*_])\\1{2,}\$"))) {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }
        
        // Potential table detection
        if (trimmedLine.startsWith("|") && i + 1 < lines.size && lines[i+1].trim().matches(Regex("\\|[:\\s-]*(\\|[:\\s-]*)*\\|"))) {
            val tableLines = mutableListOf<String>()
            tableLines.add(line)
            i++
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                tableLines.add(lines[i])
                i++
            }
            
            if (tableLines.size >= 2) {
                blocks.add(parseTableBlock(tableLines, baseColor))
                continue
            }
        }
        
        // Normal text block (which can contain images)
        val textBlockLines = mutableListOf<String>()
        while (i < lines.size) {
            val currentLine = lines[i]
            val currentTrimmed = currentLine.trim()
            
            // Stop if a header, divider or table starts
            if (currentTrimmed.startsWith("#") && Regex("^(#{1,6})\\s+(.*)$").matches(currentTrimmed)) {
                break
            }
            if (currentTrimmed.matches(Regex("^([-*_])\\1{2,}\$"))) {
                break
            }
            if (currentTrimmed.startsWith("|") && i + 1 < lines.size && lines[i+1].trim().matches(Regex("\\|[:\\s-]*(\\|[:\\s-]*)*\\|"))) {
                break
            }
            
            textBlockLines.add(currentLine)
            i++
        }
        
        if (textBlockLines.isNotEmpty()) {
            val textContent = textBlockLines.joinToString("\n")
            blocks.addAll(parseTextBlockWithImages(textContent, baseColor))
        }
    }
    
    return blocks
}

private fun parseTableBlock(lines: List<String>, baseColor: Color): MarkdownBlock.Table {
    val rawHeaders = lines[0].trim().trim('|').split("|").map { it.trim() }
    val headers = rawHeaders.map { buildAnnotatedMarkdown(it, baseColor) }
    
    val rows = mutableListOf<List<AnnotatedString>>()
    // Skip header and separator (lines[1])
    for (j in 2 until lines.size) {
        val rawCells = lines[j].trim().trim('|').split("|").map { it.trim() }
        val cells = rawCells.map { buildAnnotatedMarkdown(it, baseColor) }
        rows.add(cells)
    }
    
    return MarkdownBlock.Table(headers, rows)
}

private fun parseTextBlockWithImages(text: String, baseColor: Color): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val imageRegex = Regex("!\\[(.*?)\\]\\((.*?)\\)")
    
    var lastIndex = 0
    imageRegex.findAll(text).forEach { match ->
        val textBefore = text.substring(lastIndex, match.range.first)
        if (textBefore.isNotBlank()) {
            blocks.add(MarkdownBlock.Text(buildAnnotatedMarkdown(textBefore, baseColor)))
        }
        
        val alt = match.groupValues[1]
        val url = match.groupValues[2]
        blocks.add(MarkdownBlock.Image(url, alt))
        
        lastIndex = match.range.last + 1
    }
    
    val remainingText = text.substring(lastIndex)
    if (remainingText.isNotBlank()) {
        blocks.add(MarkdownBlock.Text(buildAnnotatedMarkdown(remainingText, baseColor)))
    }
    
    return blocks
}

private fun buildAnnotatedMarkdown(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.lines()
        lines.forEachIndexed { index, line ->
            processLine(line, baseColor)
            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.processLine(line: String, baseColor: Color) {
    var remaining = line
    
    while (remaining.isNotEmpty()) {
        val boldMatch = Regex("\\*\\*(.*?)\\*\\*").find(remaining)
        val italicMatch = Regex("\\*(.*?)\\*").find(remaining)
        val codeMatch = Regex("`(.*?)`").find(remaining)
        
        val matches = listOfNotNull(boldMatch, italicMatch, codeMatch).sortedBy { it.range.first }
        
        if (matches.isEmpty()) {
            append(remaining)
            break
        }
        
        val firstMatch = matches.first()
        val matchStart = firstMatch.range.first
        val matchEnd = firstMatch.range.last + 1
        
        if (matchStart > 0) {
            append(remaining.substring(0, matchStart))
        }
        
        when (firstMatch) {
            boldMatch -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(firstMatch.groupValues[1])
                }
            }
            italicMatch -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(firstMatch.groupValues[1])
                }
            }
            codeMatch -> {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = baseColor.copy(alpha = 0.1f)
                )) {
                    append(firstMatch.groupValues[1])
                }
            }
        }
        
        remaining = remaining.substring(matchEnd)
    }
}