package com.vknxworks.wrtctrl.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.data.settings.ThemeChoice

/** One Signal palette (dark or light). The accent fill is the same in both; see [CtrlColors.Accent]. */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val raised: Color,
    /** Card borders. */
    val outline: Color,
    /** Borders on controls (buttons, avatar, text fields, the tab bar). */
    val controlOutline: Color,
    val divider: Color,
    val text: Color,
    val muted: Color,
    /** Disabled and stopped. */
    val stopped: Color,
    /** The accent as text or icon colour on the background (readable on white in light mode). */
    val accentText: Color,
    val warn: Color,
    val error: Color,
    val onError: Color,
    val switchTrackOff: Color,
    val progressTrack: Color,
    val pinEmpty: Color,
)

/**
 * Ctrl / Signal: the look shared by every Ctrl app. Near-black (dark) or warm off-white (light)
 * base, following the phone's dark theme setting, and one accent. Change [Accent] to recolour the
 * whole app; [Dark] and [Light] hold the rest. The other properties read the palette in use, so they
 * only work inside composables.
 */
object CtrlColors {
    /** The one accent colour, as a fill (hero cards, main buttons, selected tab, switches, PIN dots). */
    val Accent = Color(0xFFB48CFF)
    /** Content on an accent fill, in both themes. */
    val OnAccent = Color(0xFF08080A)

    val Dark = CtrlPalette(
        isDark = true,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        outline = Color(0xFF232328),
        controlOutline = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        stopped = Color(0xFF8C8C94),
        accentText = Accent,
        warn = Color(0xFFF2C94C),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        switchTrackOff = Color(0xFF2A2A30),
        progressTrack = Color(0xFF26262C),
        pinEmpty = Color(0xFF3A3A41),
    )

    val Light = CtrlPalette(
        isDark = false,
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        outline = Color(0xFFDEDED7),
        controlOutline = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        text = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        stopped = Color(0xFF74747C),
        accentText = Color(0xFF6A3BDB),
        warn = Color(0xFFA86F00),
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        switchTrackOff = Color(0xFFDCDCD5),
        progressTrack = Color(0xFFE2E2DC),
        pinEmpty = Color(0xFFC9C9C1),
    )

    val Palette: CtrlPalette @Composable @ReadOnlyComposable get() = LocalCtrlColors.current
    val Background: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.raised
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.outline
    val ControlOutline: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.controlOutline
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.divider
    val Text: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.muted
    val Stopped: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.stopped
    val AccentText: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.accentText
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.warn
    val Error: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.error
    val SwitchTrackOff: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.switchTrackOff
    val ProgressTrack: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.progressTrack
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.pinEmpty
}

/** The Signal palette in use (dark or light, following the phone). */
val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

private fun schemeFor(p: CtrlPalette): ColorScheme = if (p.isDark) {
    darkColorScheme(
        primary = CtrlColors.Accent,
        onPrimary = CtrlColors.OnAccent,
        primaryContainer = CtrlColors.Accent,
        onPrimaryContainer = CtrlColors.OnAccent,
        inversePrimary = CtrlColors.Accent,
        secondary = p.text,
        onSecondary = CtrlColors.OnAccent,
        secondaryContainer = p.raised,
        onSecondaryContainer = p.text,
        tertiary = p.warn,
        onTertiary = CtrlColors.OnAccent,
        tertiaryContainer = p.raised,
        onTertiaryContainer = p.text,
        background = p.background,
        onBackground = p.text,
        surface = p.background,
        onSurface = p.text,
        surfaceVariant = p.raised,
        onSurfaceVariant = p.muted,
        surfaceTint = Color.Transparent,
        inverseSurface = p.text,
        inverseOnSurface = p.background,
        surfaceBright = p.raised,
        surfaceDim = p.background,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.raised,
        outline = p.controlOutline,
        outlineVariant = p.divider,
        error = p.error,
        onError = p.onError,
        errorContainer = Color(0xFF2A1416),
        onErrorContainer = Color(0xFFFFD6D6),
        scrim = Color.Black,
    )
} else {
    lightColorScheme(
        // Material uses primary mostly for text and icons (text buttons, checkboxes, spinners), so in
        // light mode it's the darker accent; fills use primaryContainer, the accent itself.
        primary = p.accentText,
        onPrimary = Color.White,
        primaryContainer = CtrlColors.Accent,
        onPrimaryContainer = CtrlColors.OnAccent,
        inversePrimary = CtrlColors.Accent,
        secondary = p.text,
        onSecondary = Color.White,
        secondaryContainer = p.raised,
        onSecondaryContainer = p.text,
        tertiary = p.warn,
        onTertiary = Color.White,
        tertiaryContainer = p.raised,
        onTertiaryContainer = p.text,
        background = p.background,
        onBackground = p.text,
        surface = p.background,
        onSurface = p.text,
        surfaceVariant = p.raised,
        onSurfaceVariant = p.muted,
        surfaceTint = Color.Transparent,
        inverseSurface = p.text,
        inverseOnSurface = p.background,
        surfaceBright = p.surface,
        surfaceDim = p.raised,
        surfaceContainerLowest = p.surface,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.raised,
        outline = p.controlOutline,
        outlineVariant = p.divider,
        error = p.error,
        onError = p.onError,
        errorContainer = Color(0xFFFBE4E4),
        onErrorContainer = Color(0xFF5C1010),
        scrim = Color.Black,
    )
}

// Space Grotesk ships as one variable font (300-700); each weight is a variation of it.
private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** All UI text and headings. */
val SpaceGrotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** Numbers, data readouts, section labels and the wordmark. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(56, 56, 700, -0.04),
    displayMedium = style(48, 50, 700, -0.04),
    displaySmall = style(36, 40, 700, -0.035),
    headlineLarge = style(40, 42, 700, -0.035),
    headlineMedium = style(30, 34, 700, -0.03),
    headlineSmall = style(24, 30, 700, -0.02),
    titleLarge = style(20, 26, 600, -0.01),
    titleMedium = style(18, 24, 600),
    titleSmall = style(15, 20, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 21, 400),
    bodySmall = style(12, 17, 400),
    labelLarge = style(14, 20, 600),
    labelMedium = style(12, 16, 500),
    labelSmall = style(11, 14, 500, 0.01),
)

/** The Signal type roles that Material's scale doesn't have. */
object CtrlType {
    /** Section labels: mono, upper case, wide tracking (use with [CtrlColors.Muted]). */
    val Label = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.16.em)
    /** The "PROX/CTRL" wordmark. */
    val Wordmark = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em)
    /** Small data readouts: IDs, states, sizes. */
    val Mono = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    /** Status words in list rows ("RUN", "STOP"). */
    val MonoStatus = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.em)
    /** Hero / display title (node name). */
    val Display = TextStyle(fontFamily = SpaceGrotesk, fontSize = 56.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.04).em)
    /** Page headline. */
    val Headline = TextStyle(fontFamily = SpaceGrotesk, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.035).em)
    /** The giant number on a hero card. */
    val BigNumber = TextStyle(fontFamily = SpaceGrotesk, fontSize = 64.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.05).em)
    /** A medium readout on a secondary card ("38.4 / 64 GB"). */
    val Readout = TextStyle(fontFamily = SpaceGrotesk, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em)
    /** PIN pad digits. */
    val Key = TextStyle(fontFamily = JetBrainsMono, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
}

/** Corner radii of the Signal look. */
object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Control = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(16.dp)
    val Field = RoundedCornerShape(16.dp)
    val Badge = RoundedCornerShape(9.dp)
    val Dot = RoundedCornerShape(2.dp)
    val Dialog = RoundedCornerShape(26.dp)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Horizontal page margin used everywhere. */
val Gutter = 20.dp

/**
 * WrtCtrl's theme: Signal dark or Signal light, following the phone's dark theme setting. There is no
 * theme setting and no wallpaper colours; [choice] and [dynamic] are only kept so saved settings still
 * load. [darkTheme] can force dark (the consoles stay dark in both).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun WrtCtrlTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    dynamic: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) CtrlColors.Dark else CtrlColors.Light
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(colorScheme = schemeFor(palette), typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/**
 * Health colours for usage bars, status dots and the attention list. Running and healthy use the
 * accent; stopped is grey; warnings and failures keep their own colours so they always stand out.
 */
object Health {
    val ok: Color @Composable @ReadOnlyComposable get() = CtrlColors.AccentText
    val warn: Color @Composable @ReadOnlyComposable get() = CtrlColors.Warn
    val critical: Color @Composable @ReadOnlyComposable get() = CtrlColors.Error
    val idle: Color @Composable @ReadOnlyComposable get() = CtrlColors.Stopped
}

/**
 * Status and navigation bars in the background colour, with light icons in dark mode and dark icons
 * in light mode. Follows the phone's dark theme setting as it changes.
 */
@Composable
fun SignalSystemBars(activity: ComponentActivity) {
    val dark = isSystemInDarkTheme()
    DisposableEffect(dark) {
        activity.enableEdgeToEdge(statusBarStyle = signalBarStyle(dark), navigationBarStyle = signalBarStyle(dark))
        onDispose { }
    }
}

/** The bar style for [dark]; used before the first frame too. */
fun signalBarStyle(dark: Boolean): SystemBarStyle =
    if (dark) SystemBarStyle.dark(CtrlColors.Dark.background.toArgb())
    else SystemBarStyle.light(CtrlColors.Light.background.toArgb(), CtrlColors.Light.background.toArgb())

/** Whether the phone is in dark mode, outside of composition. */
fun Context.isSystemDark(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
