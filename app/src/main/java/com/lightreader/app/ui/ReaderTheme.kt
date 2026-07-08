package com.lightreader.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

object ReaderUiTokens {
    val appBarHeight = 56.dp
    val iconTouchTarget = 48.dp
    val pagePadding = 24.dp
    val compactPagePadding = 20.dp
    val cardRadius = 18.dp
    val dialogRadius = 18.dp
    val bottomSheetRadius = 28.dp
    val listItemMinHeight = 58.dp
    val primaryButtonHeight = 46.dp
    const val overlayScrimAlpha = .24f
    const val weakTextAlpha = .72f
}

val AppSkinPalette.cardBackground: Color get() = soft.copy(alpha = .74f)
val AppSkinPalette.dialogBackground: Color get() = surface.copy(alpha = .98f)
val AppSkinPalette.divider: Color get() = primary.copy(alpha = .18f)
val AppSkinPalette.weakInk: Color get() = mutedInk.copy(alpha = .82f)

fun AppSkin.palette(): AppSkinPalette = when (this) {
    AppSkin.MINT -> AppSkinPalette(
        primary = Color(0xFF62866F), onPrimary = Color.White,
        background = Color(0xFFF7FBF7), surface = Color(0xFFFCFEFC),
        soft = Color(0xFFE3EFE6), accent = Color(0xFFB9D5C0),
        ink = Color(0xFF26332B), mutedInk = Color(0xFF66756B),
    )
    AppSkin.OCEAN -> AppSkinPalette(
        primary = Color(0xFF668EAC), onPrimary = Color.White,
        background = Color(0xFFF7FAFD), surface = Color(0xFFFCFDFE),
        soft = Color(0xFFE1ECF5), accent = Color(0xFFB8D2E4),
        ink = Color(0xFF25333D), mutedInk = Color(0xFF657581),
    )
    AppSkin.APRICOT -> AppSkinPalette(
        primary = Color(0xFFB98260), onPrimary = Color.White,
        background = Color(0xFFFFFAF4), surface = Color(0xFFFFFDF9),
        soft = Color(0xFFF6E6D7), accent = Color(0xFFEBC8AA),
        ink = Color(0xFF3A302A), mutedInk = Color(0xFF7A6B61),
    )
    AppSkin.SAKURA -> AppSkinPalette(
        primary = Color(0xFFB97F8B), onPrimary = Color.White,
        background = Color(0xFFFFF9FA), surface = Color(0xFFFFFDFD),
        soft = Color(0xFFF5E4E8), accent = Color(0xFFE7BEC7),
        ink = Color(0xFF392D30), mutedInk = Color(0xFF79696D),
    )
}

private fun colors(skin: AppSkin) = skin.palette().let { palette ->
    lightColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.soft,
        onPrimaryContainer = palette.ink,
        secondary = palette.mutedInk,
        onSecondary = Color.White,
        secondaryContainer = palette.accent.copy(alpha = .48f),
        onSecondaryContainer = palette.ink,
        tertiary = palette.accent,
        onTertiary = palette.ink,
        tertiaryContainer = palette.accent.copy(alpha = .35f),
        onTertiaryContainer = palette.ink,
        background = palette.background,
        onBackground = palette.ink,
        surface = palette.surface,
        onSurface = palette.ink,
        surfaceVariant = palette.soft,
        onSurfaceVariant = palette.mutedInk,
        outline = palette.primary.copy(alpha = .38f),
        outlineVariant = palette.primary.copy(alpha = .16f),
    )
}

private val ReaderTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

private val ReaderShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
)

@Composable
fun LightReaderTheme(skin: AppSkin, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors(skin),
        typography = ReaderTypography,
        shapes = ReaderShapes,
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
