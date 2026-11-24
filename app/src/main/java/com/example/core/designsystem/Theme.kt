package com.example.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppLanguage
import com.example.domain.model.ThemeMode

object DolilSpacing {
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space5 = 20.dp
    val Space6 = 24.dp
    val Space8 = 32.dp
    val Space10 = 40.dp
    val Space12 = 48.dp
}

object DolilRadius {
    val Input = 14.dp
    val Button = 14.dp
    val Pill = 28.dp
    val Card = 18.dp
    val BottomSheet = 24.dp
    val Dialog = 24.dp
}

object DolilComponentTokens {
    val MinTouchTarget = 48.dp
    val CardBorderWidth = 1.dp
    val HairlineWidth = 0.75.dp
    val HighlightBorderWidth = 1.5.dp
    val FocusStrokeWidth = 2.dp
    val BadgeCorner = 8.dp
}

object DolilElevation {
    val Level0 = 0.dp
    val Level1 = 1.dp
    val Level2 = 3.dp
    val Level3 = 6.dp
    val Level4 = 8.dp
}

object DolilIconSize {
    val Xs = 14.dp
    val Sm = 18.dp
    val Md = 20.dp
    val Lg = 24.dp
    val Hero = 36.dp
}

object DolilMotion {
    const val FastMs = 150
    const val StandardMs = 240
    const val EmphasisMs = 320
}

@Immutable
data class DolilSemanticColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val archived: Color,
    val archivedContainer: Color,
    val onArchivedContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val accentGold: Color,
    val paperSurface: Color,
    val onPaperSurface: Color,
    val onPaperMuted: Color,
    val paperDivider: Color,
    val surfaceSubtle: Color,
    val divider: Color,
    val cardBorder: Color,
    val focusRing: Color
)

@Immutable
data class DolilHeroTokens(
    val gradientStart: Color,
    val gradientEnd: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val chipBackground: Color,
    val searchContainer: Color
)

private val LightSemanticColors = DolilSemanticColors(
    success = StatusApprovedLight,
    successContainer = StatusApprovedContainerLight,
    onSuccessContainer = StatusOnApprovedContainerLight,
    warning = StatusPendingLight,
    warningContainer = StatusPendingContainerLight,
    onWarningContainer = StatusOnPendingContainerLight,
    info = StatusDraftLight,
    infoContainer = StatusDraftContainerLight,
    onInfoContainer = StatusOnDraftContainerLight,
    archived = StatusArchivedLight,
    archivedContainer = StatusArchivedContainerLight,
    onArchivedContainer = StatusOnArchivedContainerLight,
    error = StatusErrorLight,
    onError = StatusOnErrorLight,
    errorContainer = StatusErrorContainerLight,
    onErrorContainer = StatusOnErrorContainerLight,
    accentGold = AccentAntiqueGold,
    paperSurface = PaperDocumentSurface,
    onPaperSurface = PaperDocumentOnSurface,
    onPaperMuted = PaperDocumentOnMuted,
    paperDivider = PaperDocumentDivider,
    surfaceSubtle = PaperLightSurfaceLow,
    divider = PaperOutlineVariantLight,
    cardBorder = PaperOutlineVariantLight.copy(alpha = 0.65f),
    focusRing = FocusRingLight
)

private val DarkSemanticColors = DolilSemanticColors(
    success = StatusApprovedDark,
    successContainer = StatusApprovedContainerDark,
    onSuccessContainer = StatusOnApprovedContainerDark,
    warning = StatusPendingDark,
    warningContainer = StatusPendingContainerDark,
    onWarningContainer = StatusOnPendingContainerDark,
    info = StatusDraftDark,
    infoContainer = StatusDraftContainerDark,
    onInfoContainer = StatusOnDraftContainerDark,
    archived = StatusArchivedDark,
    archivedContainer = StatusArchivedContainerDark,
    onArchivedContainer = StatusOnArchivedContainerDark,
    error = StatusErrorDark,
    onError = StatusOnErrorDark,
    errorContainer = StatusErrorContainerDark,
    onErrorContainer = StatusOnErrorContainerDark,
    accentGold = AccentAntiqueGold,
    paperSurface = PaperDocumentSurface,
    onPaperSurface = PaperDocumentOnSurface,
    onPaperMuted = PaperDocumentOnMuted,
    paperDivider = PaperDocumentDivider,
    surfaceSubtle = CharcoalDarkSurface,
    divider = CharcoalOutlineVariantDark,
    cardBorder = CharcoalOutlineVariantDark.copy(alpha = 0.72f),
    focusRing = FocusRingDark
)

private val LightHeroTokens = DolilHeroTokens(
    gradientStart = HeroGradientStartLight,
    gradientEnd = HeroGradientEndLight,
    onHero = Color.White,
    onHeroMuted = Color.White.copy(alpha = 0.88f),
    chipBackground = AccentAntiqueGold.copy(alpha = 0.22f),
    searchContainer = PaperLightSurfaceHigh
)

private val DarkHeroTokens = DolilHeroTokens(
    gradientStart = HeroGradientStartDark,
    gradientEnd = HeroGradientEndDark,
    onHero = DarkOnHero,
    onHeroMuted = DarkOnHeroMuted,
    chipBackground = InkPrimaryContainerDark.copy(alpha = 0.72f),
    searchContainer = CharcoalDarkSurfaceHigh
)

val LocalDolilSemanticColors = staticCompositionLocalOf { LightSemanticColors }
val LocalDolilHeroTokens = staticCompositionLocalOf { LightHeroTokens }

val MaterialTheme.dolilColors: DolilSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalDolilSemanticColors.current

val MaterialTheme.dolilHero: DolilHeroTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalDolilHeroTokens.current

val DolilShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(DolilRadius.Input),
    medium = RoundedCornerShape(DolilRadius.Card),
    large = RoundedCornerShape(DolilRadius.BottomSheet),
    extraLarge = RoundedCornerShape(DolilRadius.Dialog)
)

private val DolilLightColorScheme = lightColorScheme(
    primary = InkPrimaryLight,
    onPrimary = InkOnPrimaryLight,
    primaryContainer = InkPrimaryContainerLight,
    onPrimaryContainer = InkOnPrimaryContainerLight,
    secondary = UmberSecondaryLight,
    onSecondary = UmberOnSecondaryLight,
    secondaryContainer = UmberSecondaryContainerLight,
    onSecondaryContainer = UmberOnSecondaryContainerLight,
    tertiary = GoldTertiaryLight,
    onTertiary = GoldOnTertiaryLight,
    tertiaryContainer = GoldTertiaryContainerLight,
    onTertiaryContainer = GoldOnTertiaryContainerLight,
    background = PaperLightBackground,
    onBackground = InkTextPrimaryLight,
    surface = PaperLightBackground,
    onSurface = InkTextPrimaryLight,
    surfaceVariant = PaperLightSurface,
    onSurfaceVariant = InkTextSecondaryLight,
    surfaceContainerLowest = PaperLightSurfaceLowest,
    surfaceContainerLow = PaperLightSurfaceLow,
    surfaceContainer = PaperLightSurface,
    surfaceContainerHigh = PaperLightSurfaceHigh,
    surfaceContainerHighest = PaperLightSurfaceHighest,
    outline = PaperOutlineLight,
    outlineVariant = PaperOutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DolilDarkColorScheme = darkColorScheme(
    primary = InkPrimaryDark,
    onPrimary = InkOnPrimaryDark,
    primaryContainer = InkPrimaryContainerDark,
    onPrimaryContainer = InkOnPrimaryContainerDark,
    secondary = UmberSecondaryDark,
    onSecondary = UmberOnSecondaryDark,
    secondaryContainer = UmberSecondaryContainerDark,
    onSecondaryContainer = UmberOnSecondaryContainerDark,
    tertiary = GoldTertiaryDark,
    onTertiary = GoldOnTertiaryDark,
    tertiaryContainer = GoldTertiaryContainerDark,
    onTertiaryContainer = GoldOnTertiaryContainerDark,
    background = CharcoalDarkBackground,
    onBackground = InkTextPrimaryDark,
    surface = CharcoalDarkBackground,
    onSurface = InkTextPrimaryDark,
    surfaceVariant = CharcoalDarkSurface,
    onSurfaceVariant = InkTextSecondaryDark,
    surfaceContainerLowest = CharcoalDarkSurfaceLowest,
    surfaceContainerLow = CharcoalDarkSurfaceLow,
    surfaceContainer = CharcoalDarkSurface,
    surfaceContainerHigh = CharcoalDarkSurfaceHigh,
    surfaceContainerHighest = CharcoalDarkSurfaceHighest,
    outline = CharcoalOutlineDark,
    outlineVariant = CharcoalOutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

@Composable
fun DolilTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = false,
    language: AppLanguage = AppLanguage.BN,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamicColor && supportsDynamicColor && useDark -> dynamicDarkColorScheme(context)
        useDynamicColor && supportsDynamicColor -> dynamicLightColorScheme(context)
        useDark -> DolilDarkColorScheme
        else -> DolilLightColorScheme
    }
    val semanticColors = if (useDark) DarkSemanticColors else LightSemanticColors
    val heroTokens = if (useDark) DarkHeroTokens else LightHeroTokens

    ProvideLocalizedContext(language = language) {
        CompositionLocalProvider(
            LocalDolilSemanticColors provides semanticColors,
            LocalDolilHeroTokens provides heroTokens
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = DolilTypography,
                shapes = DolilShapes,
                content = content
            )
        }
    }
}
