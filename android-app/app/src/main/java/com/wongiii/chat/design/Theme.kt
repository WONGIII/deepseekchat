package com.wongiii.chat.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wongiii.chat.data.model.AccentColor
import com.wongiii.chat.data.model.BubbleStyle
import com.wongiii.chat.data.model.FontSize
import com.wongiii.chat.data.model.ThemeMode

@Immutable
data class ChatColors(
    val dark: Boolean,
    val accent: Color,
    val accentSoft: Color,
    val accentBorder: Color,
    val page: Color,
    val settingsPage: Color,
    val card: Color,
    val input: Color,
    val inputFilled: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textCaption: Color,
    val border: Color,
    val borderStrong: Color,
    val divider: Color,
    val bubble: Color,
    val bubbleBorder: Color,
    val drawer: Color,
    val scrim: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warning: Color,
    val success: Color,
    val codeBg: Color,
    val codeBanner: Color,
    val tagBg: Color,
    val toastBg: Color,
)

private val LightColors = ChatColors(
    dark = false,
    accent = AccentColor.BLUE.compose,
    accentSoft = Color(0xFFEDF3FE),
    accentBorder = Color(0xFFB7C8FE),
    page = Color(0xFFFFFFFF),
    settingsPage = Color(0xFFF5F6F7),
    card = Color(0xFFFFFFFF),
    input = Color(0xFFFFFFFF),
    inputFilled = Color(0xFFF5F6F7),
    textPrimary = Color(0xFF0F1115),
    textSecondary = Color(0xFF61666B),
    textTertiary = Color(0xFF81858C),
    textCaption = Color(0xFFADB2B8),
    border = Color(0x1A000000),
    borderStrong = Color(0x26000000),
    divider = Color(0x14000000),
    bubble = Color(0xFFEDF3FE),
    bubbleBorder = Color(0x00000000),
    drawer = Color(0xFFFFFFFF),
    scrim = Color(0x52000000),
    danger = Color(0xFFEC1313),
    dangerSoft = Color(0x14EC1313),
    warning = Color(0xFFE98A15),
    success = Color(0xFF2E9E6B),
    codeBg = Color(0xFFF9FAFB),
    codeBanner = Color(0xFFF9FAFB),
    tagBg = Color(0xFFF1F3F5),
    toastBg = Color(0xFF353638),
)

private val DarkColors = ChatColors(
    dark = true,
    accent = Color(0xFF5686FE),
    accentSoft = Color(0xFF283142),
    accentBorder = Color(0xFF4868B2),
    page = Color(0xFF151517),
    settingsPage = Color(0xFF1B1B1C),
    card = Color(0xFF2C2C2E),
    input = Color(0xFF2C2C2E),
    inputFilled = Color(0xFF2C2C2E),
    textPrimary = Color(0xFFF9FAFB),
    textSecondary = Color(0xFFCFD3D6),
    textTertiary = Color(0xFFADB2B8),
    textCaption = Color(0xFF81858C),
    border = Color(0x1FFFFFFF),
    borderStrong = Color(0x2EFFFFFF),
    divider = Color(0x1FFFFFFF),
    bubble = Color(0xFF2C2C2E),
    bubbleBorder = Color(0x00000000),
    drawer = Color(0xFF1B1B1C),
    scrim = Color(0x66000000),
    danger = Color(0xFFF25A5A),
    dangerSoft = Color(0x26F25A5A),
    warning = Color(0xFFF0B35B),
    success = Color(0xFF6AC49A),
    codeBg = Color(0xFF1B1B1C),
    codeBanner = Color(0xFF2C2C2E),
    tagBg = Color(0xFF2C2C2E),
    toastBg = Color(0xFF43454A),
)

fun chatColors(
    dark: Boolean,
    accent: AccentColor,
    bubbleStyle: BubbleStyle,
): ChatColors {
    val base = if (dark) DarkColors else LightColors
    val soft = Color(if (dark) accent.softDark else accent.softLight)
    val bubble = when (bubbleStyle) {
        BubbleStyle.TINTED -> soft
        BubbleStyle.NEUTRAL -> if (dark) Color(0xFF353638) else Color(0xFFF1F3F5)
        BubbleStyle.OUTLINED -> base.card
    }
    return base.copy(
        accent = if (dark) accent.compose.copy(alpha = 1f) else accent.compose,
        accentSoft = soft,
        accentBorder = if (dark) accent.compose.copy(alpha = 0.55f) else accent.compose.copy(alpha = 0.35f),
        bubble = bubble,
        bubbleBorder = if (bubbleStyle == BubbleStyle.OUTLINED) base.borderStrong else Color.Transparent,
    )
}

@Immutable
data class ChatTypography(
    val body: TextStyle,
    val bodyLarge: TextStyle,
    val label: TextStyle,
    val labelSmall: TextStyle,
    val caption: TextStyle,
    val title: TextStyle,
    val greeting: TextStyle,
    val markdown: TextStyle,
    val code: TextStyle,
)

fun chatTypography(scale: Float): ChatTypography {
    fun size(sp: Float) = (sp * scale).sp
    return ChatTypography(
        body = TextStyle(fontSize = size(16f), lineHeight = size(26f), color = Color.Unspecified),
        bodyLarge = TextStyle(fontSize = size(17f), lineHeight = size(27f)),
        label = TextStyle(fontSize = size(14f), lineHeight = size(22f), fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = size(13f), lineHeight = size(20f), fontWeight = FontWeight.Medium),
        caption = TextStyle(fontSize = size(12f), lineHeight = size(18f)),
        title = TextStyle(fontSize = size(17f), lineHeight = size(24f), fontWeight = FontWeight.Medium),
        greeting = TextStyle(fontSize = size(24f), lineHeight = size(34f), fontWeight = FontWeight.Bold),
        markdown = TextStyle(fontSize = size(16f), lineHeight = size(27f)),
        code = TextStyle(fontSize = size(13f), lineHeight = size(22f), fontFamily = FontFamily.Monospace),
    )
}

val LocalChatColors = staticCompositionLocalOf { LightColors }
val LocalChatTypography = staticCompositionLocalOf { chatTypography(1f) }

object Motion {
    const val FAST = 160
    const val NORMAL = 240
    const val SLOW = 340
    const val PAGE = 500
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val OutSoft: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val InOutSoft: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    fun <T> sharedSpring() = spring<T>(dampingRatio = 0.86f, stiffness = 320f)
}

val PageHorizontalPadding = 16.dp
val ScreenHorizontalPadding = 20.dp
val ComposerRadius = 28.dp
val BubbleRadius = 22.dp
val CardRadius = 16.dp
val RowRadius = 12.dp

@Composable
fun ChatTheme(
    themeMode: ThemeMode,
    accent: AccentColor,
    bubbleStyle: BubbleStyle,
    fontSize: FontSize,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = chatColors(dark, accent, bubbleStyle)
    val typography = chatTypography(fontSize.scale)
    val scheme = ColorScheme(
        primary = colors.accent,
        onPrimary = Color.White,
        primaryContainer = colors.accentSoft,
        onPrimaryContainer = colors.textPrimary,
        inversePrimary = colors.accent,
        secondary = colors.textSecondary,
        onSecondary = Color.White,
        secondaryContainer = colors.tagBg,
        onSecondaryContainer = colors.textPrimary,
        tertiary = colors.accent,
        onTertiary = Color.White,
        tertiaryContainer = colors.accentSoft,
        onTertiaryContainer = colors.textPrimary,
        background = colors.page,
        onBackground = colors.textPrimary,
        surface = colors.card,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.inputFilled,
        onSurfaceVariant = colors.textSecondary,
        surfaceTint = colors.accent,
        inverseSurface = colors.toastBg,
        inverseOnSurface = Color.White,
        error = colors.danger,
        onError = Color.White,
        errorContainer = colors.dangerSoft,
        onErrorContainer = colors.danger,
        outline = colors.borderStrong,
        outlineVariant = colors.border,
        scrim = colors.scrim,
        surfaceBright = colors.card,
        surfaceDim = colors.settingsPage,
        surfaceContainer = colors.settingsPage,
        surfaceContainerHigh = colors.card,
        surfaceContainerHighest = colors.card,
        surfaceContainerLow = colors.card,
        surfaceContainerLowest = colors.page,
    )
    val materialType = Typography(
        bodyLarge = typography.bodyLarge,
        bodyMedium = typography.body,
        bodySmall = typography.caption,
        labelLarge = typography.label,
        labelMedium = typography.labelSmall,
        titleMedium = typography.title,
        headlineSmall = typography.greeting,
    )
    CompositionLocalProvider(
        LocalChatColors provides colors,
        LocalChatTypography provides typography,
    ) {
        MaterialTheme(colorScheme = scheme, typography = materialType, content = content)
    }
}

@Composable
fun chatColors() = LocalChatColors.current

@Composable
fun chatTypography() = LocalChatTypography.current
