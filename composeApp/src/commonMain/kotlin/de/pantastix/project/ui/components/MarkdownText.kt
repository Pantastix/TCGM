package de.pantastix.project.ui.components

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
            }
        }
    }
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
    val imageRegex = Regex("!\\[(.*?)\\]\\((.*?)\\)")
    
    var lastIndex = 0
    imageRegex.findAll(markdown).forEach { match ->
        // Text before image
        val textBefore = markdown.substring(lastIndex, match.range.first)
        if (textBefore.isNotBlank()) {
            blocks.add(MarkdownBlock.Text(buildAnnotatedMarkdown(textBefore, baseColor)))
        }
        
        // Image block
        val alt = match.groupValues[1]
        val url = match.groupValues[2]
        blocks.add(MarkdownBlock.Image(url, alt))
        
        lastIndex = match.range.last + 1
    }
    
    // Remaining text
    val remainingText = markdown.substring(lastIndex)
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