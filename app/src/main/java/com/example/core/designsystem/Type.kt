package com.example.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.example.R

// Bundled local Google Fonts with multi-weight variation settings so Medium/SemiBold/Bold
// render true typographic weights across both Bengali and Latin/numeric scripts.
@OptIn(ExperimentalTextApi::class)
val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

@OptIn(ExperimentalTextApi::class)
val HindSiliguriFontFamily = FontFamily(
    Font(R.font.hind_siliguri, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.hind_siliguri, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.hind_siliguri, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.hind_siliguri, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

@OptIn(ExperimentalTextApi::class)
val NotoSansBengaliFontFamily = FontFamily(
    Font(R.font.noto_sans_bengali, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_sans_bengali, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_sans_bengali, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.noto_sans_bengali, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

fun TextStyle.tabularNumerals(): TextStyle = copy(fontFeatureSettings = "tnum")

fun TextStyle.editorialNumeric(): TextStyle = copy(
    fontFamily = PlusJakartaSansFontFamily,
    fontFeatureSettings = "tnum"
)

private val BengaliLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private val BengaliPlatformStyle = PlatformTextStyle(
    includeFontPadding = false
)

// Complete Material 3 15-role Typography Scale.
// Generous line heights (>=1.36x display/headline, >=1.45x body/label) protect Bengali
// conjuncts, matras (ি, ী, ু, ূ), and diacritics (ঁ, ং) from clipping at 1.0x–1.5x font scale.
val DolilTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.25).sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    displayMedium = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.2).sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    displaySmall = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.15).sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    headlineLarge = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    headlineMedium = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    headlineSmall = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    titleLarge = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    titleMedium = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    titleSmall = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    bodyLarge = TextStyle(
        fontFamily = NotoSansBengaliFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.15.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = NotoSansBengaliFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    bodySmall = TextStyle(
        fontFamily = NotoSansBengaliFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    labelLarge = TextStyle(
        fontFamily = NotoSansBengaliFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    labelMedium = TextStyle(
        fontFamily = NotoSansBengaliFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    ),
    labelSmall = TextStyle(
        fontFamily = NotoSansBengaliFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
        platformStyle = BengaliPlatformStyle,
        lineHeightStyle = BengaliLineHeightStyle
    )
)
