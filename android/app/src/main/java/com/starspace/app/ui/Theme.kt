package com.starspace.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val starBlue: Color,
    val ink: Color,
    val muted: Color,
    val pageBackground: Color,
    val cardBg: Color,
    val chatBubbleOther: Color,
    val divider: Color,
    val placeholderText: Color,
    val navBarBg: Color,
    val navItemSelectedBg: Color,
    val inputFieldBg: Color,
    val avatarBg: Color,
    val groupTagBg: Color,
    val groupTagText: Color,
    val activeDot: Color,
    val sendDisabledBg: Color,
    val reasoningBg: Color,
    val reasoningText: Color,
    val reasoningBorder: Color,
    val errorRed: Color,
    val successGreen: Color,
    val starredGold: Color
)

private val LightAppColors = AppColors(
    starBlue = Color(0xFF3478F6),
    ink = Color(0xFF182033),
    muted = Color(0xFF7B8497),
    pageBackground = Color(0xFFF5F7FB),
    cardBg = Color.White,
    chatBubbleOther = Color.White,
    divider = Color(0xFFEFF2F6),
    placeholderText = Color(0xFF8A92A3),
    navBarBg = Color.White,
    navItemSelectedBg = Color(0xFFEAF1FF),
    inputFieldBg = Color(0xFFF5F7FB),
    avatarBg = Color(0xFF5B8DEF),
    groupTagBg = Color(0xFFEAF1FF),
    groupTagText = Color(0xFF3478F6),
    activeDot = Color(0xFF63C590),
    sendDisabledBg = Color(0xFFE4E9F1),
    reasoningBg = Color(0xFFF0F2F6),
    reasoningText = Color(0xFF8A92A3),
    reasoningBorder = Color(0xFFE0E4EC),
    errorRed = Color(0xFFD85B64),
    successGreen = Color(0xFF43B581),
    starredGold = Color(0xFFFFB300)
)

private val DarkAppColors = AppColors(
    starBlue = Color(0xFF5B9BFF),
    ink = Color(0xFFE8ECF4),
    muted = Color(0xFF9CA5B7),
    pageBackground = Color(0xFF111418),
    cardBg = Color(0xFF1C2026),
    chatBubbleOther = Color(0xFF252A33),
    divider = Color(0xFF2A3038),
    placeholderText = Color(0xFF7A8294),
    navBarBg = Color(0xFF1A1E24),
    navItemSelectedBg = Color(0xFF1B2840),
    inputFieldBg = Color(0xFF1C2026),
    avatarBg = Color(0xFF4A6FA8),
    groupTagBg = Color(0xFF1B2840),
    groupTagText = Color(0xFF5B9BFF),
    activeDot = Color(0xFF4FAE7F),
    sendDisabledBg = Color(0xFF2A3038),
    reasoningBg = Color(0xFF1A1E2A),
    reasoningText = Color(0xFF8893A8),
    reasoningBorder = Color(0xFF2A3548),
    errorRed = Color(0xFFE66B73),
    successGreen = Color(0xFF4FBF8F),
    starredGold = Color(0xFFFFC947)
)

val LocalAppColors = compositionLocalOf { LightAppColors }

// Backward-compatible aliases (light theme values)
val StarBlue = LightAppColors.starBlue
val Ink = LightAppColors.ink
val Muted = LightAppColors.muted
val PageBackground = LightAppColors.pageBackground

private val LightColors = lightColorScheme(
    primary = LightAppColors.starBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF715DF2),
    background = LightAppColors.pageBackground,
    surface = LightAppColors.cardBg,
    onBackground = LightAppColors.ink,
    onSurface = LightAppColors.ink,
    onSurfaceVariant = LightAppColors.muted,
    outline = LightAppColors.divider
)

private val DarkColors = darkColorScheme(
    primary = DarkAppColors.starBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF8B7BF2),
    background = DarkAppColors.pageBackground,
    surface = DarkAppColors.cardBg,
    onBackground = DarkAppColors.ink,
    onSurface = DarkAppColors.ink,
    onSurfaceVariant = DarkAppColors.muted,
    outline = DarkAppColors.divider
)

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

@Composable
fun StarSpaceTheme(
    darkMode: String = "system",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (darkMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    val appColors = if (isDark) DarkAppColors else LightAppColors
    val colorScheme = if (isDark) DarkColors else LightColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}