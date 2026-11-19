package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Official Google Antigravity Palette Tokens
val AntigravityObsidian = Color(0xFF121317)
val AntigravitySurfaceElevated = Color(0xFF191B22)
val AntigravitySurfaceContainer = Color(0xFF20232C)
val AntigravitySurfaceHighest = Color(0xFF282C37)
val AntigravityBorderDark = Color(0xFF2F3441)

val AntigravityWhite = Color(0xFFFFFFFF)
val AntigravityLightContainer = Color(0xFFF4F6FA)
val AntigravityLightContainerHigh = Color(0xFFE9ECF2)
val AntigravityBorderLight = Color(0xFFDCE0E8)
val AntigravityTextCharcoal = Color(0xFF45474D)

// Google Ecosystem Accents
val GoogleBlueDark = Color(0xFF8AB4F8)
val GoogleBlue = Color(0xFF4285F4)
val GoogleGreenDark = Color(0xFF81C995)
val GoogleGreen = Color(0xFF34A853)
val GoogleYellowDark = Color(0xFFFDD663)
val GoogleYellow = Color(0xFFFBBC04)
val GoogleRedDark = Color(0xFFF28B82)
val GoogleRed = Color(0xFFEA4335)

@Immutable
data class AntigravityExtendedColors(
    val codeBackground: Color,
    val codeLineNumber: Color,
    val codeBorder: Color,
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxComment: Color,
    val syntaxType: Color,
    val diffAddedBg: Color,
    val diffAddedText: Color,
    val diffRemovedBg: Color,
    val diffRemovedText: Color,
    val statusRunning: Color,
    val statusReview: Color,
    val statusCompleted: Color,
    val statusError: Color,
)

val DarkExtendedColors = AntigravityExtendedColors(
    codeBackground = Color(0xFF0E1015),
    codeLineNumber = Color(0xFF5D6372),
    codeBorder = Color(0xFF282C37),
    syntaxKeyword = Color(0xFF8AB4F8),
    syntaxString = Color(0xFF81C995),
    syntaxComment = Color(0xFF7B8190),
    syntaxType = Color(0xFFFDD663),
    diffAddedBg = Color(0xFF14291D),
    diffAddedText = Color(0xFF81C995),
    diffRemovedBg = Color(0xFF2F1718),
    diffRemovedText = Color(0xFFF28B82),
    statusRunning = GoogleBlueDark,
    statusReview = GoogleYellowDark,
    statusCompleted = GoogleGreenDark,
    statusError = GoogleRedDark,
)

val LightExtendedColors = AntigravityExtendedColors(
    codeBackground = Color(0xFFF8F9FC),
    codeLineNumber = Color(0xFF8B909A),
    codeBorder = Color(0xFFDCE0E8),
    syntaxKeyword = Color(0xFF1A73E8),
    syntaxString = Color(0xFF188038),
    syntaxComment = Color(0xFF6B707B),
    syntaxType = Color(0xFFB06000),
    diffAddedBg = Color(0xFFE6F4EA),
    diffAddedText = Color(0xFF137333),
    diffRemovedBg = Color(0xFFFCE8E6),
    diffRemovedText = Color(0xFFC5221F),
    statusRunning = GoogleBlue,
    statusReview = Color(0xFFB06000),
    statusCompleted = GoogleGreen,
    statusError = GoogleRed,
)

val LocalAntigravityColors = staticCompositionLocalOf { DarkExtendedColors }
