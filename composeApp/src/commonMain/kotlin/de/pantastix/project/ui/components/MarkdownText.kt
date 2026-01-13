package de.pantastix.project.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val annotatedString = rememberMarkdown(markdown, color)
    Text(
        text = annotatedString,
        modifier = modifier,
        style = style,
        color = color
    )
}

@Composable
private fun rememberMarkdown(markdown: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = markdown.lines()
        lines.forEachIndexed { index, line ->
            if (line.isBlank()) {
                append("\n")
                return@forEachIndexed
            }
            
            // Basic Parsing
            var currentIndex = 0
            val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
            val italicRegex = Regex("\\*(.*?)\\*")
            val codeRegex = Regex("`(.*?)`")
            
            // This is a very simplified parser. Real markdown parsing is complex.
            // We'll iterate through the string and apply styles based on regex matches.
            // Note: This naive approach fails with nested or overlapping styles.
            
            // Improving the approach: Tokenize? 
            // For now, let's just handle Bold, Italic and Code by splitting. 
            
            processLine(line, baseColor)
            
            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.processLine(line: String, baseColor: Color) {
    // Regex to find tokens: **bold**, *italic*, `code`
    // We match the earliest occurrence
    
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
        
        // Append text before match
        if (matchStart > 0) {
            append(remaining.substring(0, matchStart))
        }
        
        // Apply style
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
