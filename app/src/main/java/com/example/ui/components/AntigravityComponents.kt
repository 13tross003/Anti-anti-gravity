package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ApprovalStatus
import com.example.model.SessionStatus
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleGreen
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.GoogleYellow
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAntigravityColors

@Composable
fun AntigravityLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val outerPath = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.88f, h * 0.85f)
            lineTo(w * 0.5f, h * 0.66f)
            lineTo(w * 0.12f, h * 0.85f)
            close()
        }

        drawPath(
            path = outerPath,
            brush = Brush.linearGradient(
                colors = listOf(GoogleRed, GoogleYellow, GoogleGreen, GoogleBlue),
                start = Offset(w * 0.5f, 0f),
                end = Offset(w * 0.5f, h),
            ),
            style = stroke,
        )

        val innerChevron = Path().apply {
            moveTo(w * 0.28f, h * 0.68f)
            lineTo(w * 0.5f, h * 0.36f)
            lineTo(w * 0.72f, h * 0.68f)
        }
        drawPath(
            path = innerChevron,
            color = GoogleBlue,
            style = Stroke(width = w * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun SessionStatusChip(status: SessionStatus, modifier: Modifier = Modifier) {
    val ext = LocalAntigravityColors.current
    val (color, label) = when (status) {
        SessionStatus.RUNNING -> ext.statusRunning to "Running"
        SessionStatus.NEEDS_REVIEW -> ext.statusReview to "Needs Review"
        SessionStatus.COMPLETED -> ext.statusCompleted to "Completed"
        SessionStatus.ERROR -> ext.statusError to "Blocked"
        SessionStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant to "Idle"
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun ApprovalBadge(status: ApprovalStatus, modifier: Modifier = Modifier) {
    val ext = LocalAntigravityColors.current
    val color = when (status) {
        ApprovalStatus.APPROVED -> ext.statusCompleted
        ApprovalStatus.PENDING -> ext.statusReview
        ApprovalStatus.CHANGES_REQUESTED -> ext.statusError
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
    ) {
        Text(
            text = status.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SyntaxCodeViewer(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
) {
    val ext = LocalAntigravityColors.current
    val lines = remember(code) { code.lines() }
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ext.codeBackground)
            .border(1.dp, ext.codeBorder, RoundedCornerShape(12.dp))
            .horizontalScroll(horizontalScroll)
            .padding(12.dp)
    ) {
        lines.forEachIndexed { idx, line ->
            val annotated = remember(line, language, ext) {
                highlightSyntaxLine(
                    line = line,
                    keywordColor = ext.syntaxKeyword,
                    stringColor = ext.syntaxString,
                    commentColor = ext.syntaxComment,
                    typeColor = ext.syntaxType,
                    defaultColor = MaterialThemeColorFallback(ext.codeLineNumber) // replaced below
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${idx + 1}".padStart(3, ' '),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = ext.codeLineNumber,
                    modifier = Modifier.width(34.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = highlightSyntaxLine(
                        line = line,
                        keywordColor = ext.syntaxKeyword,
                        stringColor = ext.syntaxString,
                        commentColor = ext.syntaxComment,
                        typeColor = ext.syntaxType,
                        defaultColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

private fun MaterialThemeColorFallback(c: Color): Color = c

private val CODE_KEYWORDS = setOf(
    "import", "from", "async", "await", "def", "class", "return", "if", "else",
    "elif", "for", "in", "with", "as", "try", "except", "val", "var", "fun",
    "true", "false", "True", "False", "None", "null", "const", "let", "function"
)

private fun highlightSyntaxLine(
    line: String,
    keywordColor: Color,
    stringColor: Color,
    commentColor: Color,
    typeColor: Color,
    defaultColor: Color,
): AnnotatedString {
    val trimmed = line.trimStart()
    if (trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith("<!--")) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = commentColor)) { append(line) }
        }
    }
    if (trimmed.startsWith("@")) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = typeColor, fontWeight = FontWeight.Medium)) { append(line) }
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"' || ch == '\'') {
                val quote = ch
                var end = i + 1
                while (end < line.length && line[end] != quote) {
                    if (line[end] == '\\' && end + 1 < line.length) end += 2 else end++
                }
                if (end < line.length) end++
                withStyle(SpanStyle(color = stringColor)) {
                    append(line.substring(i, end))
                }
                i = end
            } else if (ch.isLetter() || ch == '_') {
                var end = i + 1
                while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) end++
                val word = line.substring(i, end)
                val style = when {
                    word in CODE_KEYWORDS -> SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium)
                    word.first().isUpperCase() -> SpanStyle(color = typeColor)
                    else -> SpanStyle(color = defaultColor)
                }
                withStyle(style) { append(word) }
                i = end
            } else {
                withStyle(SpanStyle(color = defaultColor)) { append(ch) }
                i++
            }
        }
    }
}

@Composable
fun UnifiedDiffView(
    diffText: String,
    modifier: Modifier = Modifier,
) {
    val ext = LocalAntigravityColors.current
    val horizontalScroll = rememberScrollState()
    val lines = remember(diffText) { diffText.lines() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ext.codeBackground)
            .border(1.dp, ext.codeBorder, RoundedCornerShape(12.dp))
            .horizontalScroll(horizontalScroll)
            .padding(vertical = 8.dp)
    ) {
        lines.forEach { line ->
            val (bg, fg) = when {
                line.startsWith("+") && !line.startsWith("+++") -> ext.diffAddedBg to ext.diffAddedText
                line.startsWith("-") && !line.startsWith("---") -> ext.diffRemovedBg to ext.diffRemovedText
                line.startsWith("@@") || line.startsWith("---") || line.startsWith("+++") ->
                    Color.Transparent to ext.syntaxKeyword
                else -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(
                    text = line,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = fg,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
