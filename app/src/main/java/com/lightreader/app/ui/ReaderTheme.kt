package com.lightreader.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.lightreader.app.core.model.AppSkin

data class AppSkinPalette(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val soft: Color,
    val accent: Color,
    val ink: Color,
    val mutedInk: Color,
)

data class LightReaderColorTokens(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val selectedSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val disabledContent: Color,
    val selectedBorder: Color,
    val divider: Color,
)

object LightReaderShapeTokens {
    val small = 8.dp
    val medium = 12.dp
    val large = 20.dp
    val extraLarge = 24.dp
}

object LightReaderSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object ReaderUiTokens {
    val appBarHeight = 56.dp
    val iconTouchTarget = 48.dp
    val pagePadding = LightReaderSpacing.lg
    val compactPagePadding = 20.dp
    val cardRadius = LightReaderShapeTokens.large
    val dialogRadius = LightReaderShapeTokens.large
    val bottomSheetRadius = LightReaderShapeTokens.extraLarge
    val listItemMinHeight = 58.dp
    val primaryButtonHeight = 46.dp
    const val overlayScrimAlpha = .20f
    const val weakTextAlpha = .72f
}

val AppSkinPalette.uiColors: LightReaderColorTokens
    get() = LightReaderColorTokens(
        background = background,
        surface = surface,
        surfaceVariant = soft.copy(alpha = .78f).compositeOver(surface),
        selectedSurface = primary.copy(alpha = .18f).compositeOver(surface),
        primaryText = ink,
        secondaryText = mutedInk,
        tertiaryText = mutedInk.copy(alpha = .76f),
        disabledContent = mutedInk.copy(alpha = .38f),
        selectedBorder = primary.copy(alpha = .82f),
        divider = primary.copy(alpha = .18f),
    )

val AppSkinPalette.cardBackground: Color get() = uiColors.surfaceVariant
val AppSkinPalette.dialogBackground: Color get() = uiColors.surface
val AppSkinPalette.divider: Color get() = uiColors.divider
val AppSkinPalette.weakInk: Color get() = uiColors.tertiaryText

fun AppSkin.palette(): AppSkinPalette = when (this) {
    AppSkin.MINT -> AppSkinPalette(
        primary = Color(0xFF4F755E), onPrimary = Color.White,
        background = Color(0xFFF7FBF7), surface = Color(0xFFFCFEFC),
        soft = Color(0xFFE3EFE6), accent = Color(0xFFB9D5C0),
        ink = Color(0xFF26332B), mutedInk = Color(0xFF55655B),
    )
    AppSkin.OCEAN -> AppSkinPalette(
        primary = Color(0xFF4F7693), onPrimary = Color.White,
        background = Color(0xFFF7FAFD), surface = Color(0xFFFCFDFE),
        soft = Color(0xFFE1ECF5), accent = Color(0xFFB8D2E4),
        ink = Color(0xFF25333D), mutedInk = Color(0xFF536571),
    )
    AppSkin.APRICOT -> AppSkinPalette(
        primary = Color(0xFF965F43), onPrimary = Color.White,
        background = Color(0xFFFFFAF4), surface = Color(0xFFFFFDF9),
        soft = Color(0xFFF6E6D7), accent = Color(0xFFEBC8AA),
        ink = Color(0xFF3A302A), mutedInk = Color(0xFF68584F),
    )
    AppSkin.SAKURA -> AppSkinPalette(
        primary = Color(0xFF915D69), onPrimary = Color.White,
        background = Color(0xFFFFF9FA), surface = Color(0xFFFFFDFD),
        soft = Color(0xFFF5E4E8), accent = Color(0xFFE7BEC7),
        ink = Color(0xFF392D30), mutedInk = Color(0xFF67575B),
    )
}

internal fun appColorScheme(skin: AppSkin) = skin.palette().let { palette ->
    val tokens = palette.uiColors
    lightColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = tokens.selectedSurface,
        onPrimaryContainer = tokens.primaryText,
        secondary = tokens.secondaryText,
        onSecondary = Color.White,
        secondaryContainer = palette.accent.copy(alpha = .48f),
        onSecondaryContainer = palette.ink,
        tertiary = palette.accent,
        onTertiary = palette.ink,
        tertiaryContainer = palette.accent.copy(alpha = .35f),
        onTertiaryContainer = palette.ink,
        background = tokens.background,
        onBackground = tokens.primaryText,
        surface = tokens.surface,
        onSurface = tokens.primaryText,
        surfaceVariant = tokens.surfaceVariant,
        onSurfaceVariant = tokens.secondaryText,
        surfaceTint = palette.primary,
        inverseSurface = palette.ink,
        inverseOnSurface = palette.surface,
        inversePrimary = palette.accent,
        surfaceDim = palette.soft,
        surfaceBright = palette.surface,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.soft.copy(alpha = .42f).compositeOver(palette.surface),
        surfaceContainerHigh = palette.soft.copy(alpha = .62f).compositeOver(palette.surface),
        surfaceContainerHighest = palette.soft.copy(alpha = .82f).compositeOver(palette.surface),
        error = Color(0xFF9F3F3F),
        onError = Color.White,
        errorContainer = Color(0xFFF6DEDC),
        onErrorContainer = Color(0xFF521C1C),
        outline = tokens.selectedBorder,
        outlineVariant = tokens.divider,
        scrim = Color.Black,
    )
}

val LightReaderTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

val LightReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(LightReaderShapeTokens.small),
    small = RoundedCornerShape(LightReaderShapeTokens.medium),
    medium = RoundedCornerShape(LightReaderShapeTokens.large),
    large = RoundedCornerShape(LightReaderShapeTokens.extraLarge),
    extraLarge = RoundedCornerShape(LightReaderShapeTokens.extraLarge),
)

@Composable
fun LightReaderTheme(skin: AppSkin, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = appColorScheme(skin),
        typography = LightReaderTypography,
        shapes = LightReaderShapes,
        content = content,
    )
}

@Composable
fun FreshBackdrop(
    skin: AppSkin,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = skin.palette()
    Box(modifier.background(palette.background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(palette.soft.copy(alpha = .9f), Color.Transparent)),
                radius = size.minDimension * .55f,
                center = Offset(size.width * .9f, size.height * .08f),
            )
            drawCircle(
                color = palette.accent.copy(alpha = .11f),
                radius = size.minDimension * .34f,
                center = Offset(size.width * .04f, size.height * .84f),
            )
        }
        content()
    }
}

@Composable
fun ApplyAppSystemBars(skin: AppSkin, readerVisible: Boolean) {
    val activity = LocalActivity.current ?: return
    val palette = skin.palette()
    SideEffect {
        if (!readerVisible) {
            @Suppress("DEPRECATION")
            activity.window.statusBarColor = palette.background.toArgb()
            @Suppress("DEPRECATION")
            activity.window.navigationBarColor = palette.background.toArgb()
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
