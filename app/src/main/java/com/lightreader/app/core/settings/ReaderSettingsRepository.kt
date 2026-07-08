package com.lightreader.app.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.AppSkin
import com.lightreader.app.core.model.PageTurnMode
import com.lightreader.app.core.model.ReaderLayoutPreset
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore("reader_preferences")

class ReaderSettingsRepository(private val context: Context) {
    val preferences: Flow<ReaderPreferences> = context.readerDataStore.data.map { values ->
        val usesModernLayoutDefaults = (values[LAYOUT_DEFAULTS_VERSION] ?: 0) >= CURRENT_LAYOUT_DEFAULTS_VERSION
        ReaderPreferences(
            appSkin = enumValue(values[APP_SKIN], AppSkin.MINT),
            layoutPreset = enumValue(values[LAYOUT_PRESET], ReaderLayoutPreset.COMFORT),
            fontSizeSp = values[FONT_SIZE] ?: DEFAULT_FONT_SIZE_SP,
            fontWeight = values[FONT_WEIGHT] ?: 400,
            lineSpacingMultiplier = values[LINE_SPACING] ?: DEFAULT_LINE_SPACING,
            paragraphSpacingDp = values[PARAGRAPH_SPACING] ?: DEFAULT_PARAGRAPH_SPACING_DP,
            firstLineIndent = values[FIRST_INDENT] ?: true,
            firstLineIndentEm = values[FIRST_INDENT_EM] ?: 2f,
            horizontalPaddingDp = migratedLayoutValue(
                raw = values[H_PADDING],
                usesModernDefaults = usesModernLayoutDefaults,
                historicalDefault = HISTORICAL_HORIZONTAL_PADDING_DP,
                newDefault = DEFAULT_HORIZONTAL_PADDING_DP,
            ),
            verticalPaddingTopDp = migratedLayoutValue(
                raw = values[V_PADDING_TOP] ?: values[LEGACY_V_PADDING]?.plus(44f),
                usesModernDefaults = usesModernLayoutDefaults,
                historicalDefaults = listOf(
                    HISTORICAL_VERTICAL_PADDING_TOP_DP,
                    COMPACT_VERTICAL_PADDING_TOP_DP,
                    TITLE_SPACING_VERTICAL_PADDING_TOP_DP,
                ),
                newDefault = DEFAULT_VERTICAL_PADDING_TOP_DP,
            ),
            verticalPaddingBottomDp = migratedLayoutValue(
                raw = values[V_PADDING_BOTTOM] ?: values[LEGACY_V_PADDING]?.plus(36f),
                usesModernDefaults = usesModernLayoutDefaults,
                historicalDefaults = listOf(
                    HISTORICAL_VERTICAL_PADDING_BOTTOM_DP,
                    COMPACT_VERTICAL_PADDING_BOTTOM_DP,
                    TITLE_SPACING_VERTICAL_PADDING_BOTTOM_DP,
                ),
                newDefault = DEFAULT_VERTICAL_PADDING_BOTTOM_DP,
            ),
            justified = migratedJustifiedValue(values[JUSTIFIED], usesModernLayoutDefaults),
            theme = readerTheme(values[THEME]),
            lastNonNightTheme = readerTheme(values[LAST_NON_NIGHT_THEME]).takeUnless { it == ReaderTheme.NIGHT }
                ?: ReaderTheme.EYE_CARE,
            customBackground = values[CUSTOM_BG] ?: 0xFFB8C9A7,
            customForeground = values[CUSTOM_FG] ?: 0xFF26301F,
            customSecondary = values[CUSTOM_SECONDARY] ?: 0xFF6F8063,
            brightness = values[BRIGHTNESS] ?: -1f,
            keepScreenOn = values[KEEP_SCREEN] ?: false,
            lockPortrait = values[LOCK_PORTRAIT] ?: true,
            showStatus = values[SHOW_STATUS] ?: true,
            showHeader = values[SHOW_HEADER] ?: true,
            showRightProgressBar = values[SHOW_RIGHT_PROGRESS] ?: false,
            minimalMode = values[MINIMAL_MODE] ?: false,
            autoReadIntervalSeconds = (values[AUTO_READ_INTERVAL] ?: 8).coerceIn(3, 60),
            volumeKeys = values[VOLUME_KEYS] ?: true,
            fontFamily = enumValue(values[FONT_FAMILY], FontFamilyOption.SERIF),
            pageTurnMode = pageTurnMode(values[PAGE_TURN_MODE] ?: values[PAGE_ANIMATION]),
            fullScreenTapNext = values[FULL_SCREEN_TAP_NEXT] ?: false,
        )
    }

    suspend fun save(value: ReaderPreferences) {
        context.readerDataStore.edit { values ->
            values[APP_SKIN] = value.appSkin.name
            values[LAYOUT_PRESET] = value.layoutPreset.name
            values[FONT_SIZE] = value.fontSizeSp
            values[FONT_WEIGHT] = value.fontWeight
            values[LINE_SPACING] = value.lineSpacingMultiplier
            values[PARAGRAPH_SPACING] = value.paragraphSpacingDp
            values[FIRST_INDENT] = value.firstLineIndent
            values[FIRST_INDENT_EM] = value.firstLineIndentEm
            values[H_PADDING] = value.horizontalPaddingDp
            values[V_PADDING_TOP] = value.verticalPaddingTopDp
            values[V_PADDING_BOTTOM] = value.verticalPaddingBottomDp
            values[LAYOUT_DEFAULTS_VERSION] = CURRENT_LAYOUT_DEFAULTS_VERSION
            values[JUSTIFIED] = value.justified
            values[THEME] = value.theme.name
            values[LAST_NON_NIGHT_THEME] = value.lastNonNightTheme.name
            values[CUSTOM_BG] = value.customBackground
            values[CUSTOM_FG] = value.customForeground
            values[CUSTOM_SECONDARY] = value.customSecondary
            values[BRIGHTNESS] = value.brightness
            values[KEEP_SCREEN] = value.keepScreenOn
            values[LOCK_PORTRAIT] = value.lockPortrait
            values[SHOW_STATUS] = value.showStatus
            values[SHOW_HEADER] = value.showHeader
            values[SHOW_RIGHT_PROGRESS] = value.showRightProgressBar
            values[MINIMAL_MODE] = value.minimalMode
            values[AUTO_READ_INTERVAL] = value.autoReadIntervalSeconds.coerceIn(3, 60)
            values[VOLUME_KEYS] = value.volumeKeys
            values[FONT_FAMILY] = value.fontFamily.name
            values[PAGE_TURN_MODE] = value.pageTurnMode.name
            values[FULL_SCREEN_TAP_NEXT] = value.fullScreenTapNext
        }
    }

    private companion object {
        val APP_SKIN = stringPreferencesKey("app_skin")
        val LAYOUT_PRESET = stringPreferencesKey("reader_layout_preset")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val FONT_WEIGHT = intPreferencesKey("font_weight")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val PARAGRAPH_SPACING = floatPreferencesKey("paragraph_spacing")
        val FIRST_INDENT = booleanPreferencesKey("first_indent")
        val FIRST_INDENT_EM = floatPreferencesKey("first_indent_em")
        val H_PADDING = floatPreferencesKey("horizontal_padding")
        val V_PADDING_TOP = floatPreferencesKey("vertical_padding_top")
        val V_PADDING_BOTTOM = floatPreferencesKey("vertical_padding_bottom")
        val LEGACY_V_PADDING = floatPreferencesKey("vertical_padding")
        val LAYOUT_DEFAULTS_VERSION = intPreferencesKey("layout_defaults_version")
        val JUSTIFIED = booleanPreferencesKey("justified")
        val THEME = stringPreferencesKey("theme")
        val LAST_NON_NIGHT_THEME = stringPreferencesKey("last_non_night_theme")
        val CUSTOM_BG = longPreferencesKey("custom_background")
        val CUSTOM_FG = longPreferencesKey("custom_foreground")
        val CUSTOM_SECONDARY = longPreferencesKey("custom_secondary")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val KEEP_SCREEN = booleanPreferencesKey("keep_screen")
        val LOCK_PORTRAIT = booleanPreferencesKey("lock_portrait")
        val SHOW_STATUS = booleanPreferencesKey("show_status")
        val SHOW_HEADER = booleanPreferencesKey("show_header")
        val SHOW_RIGHT_PROGRESS = booleanPreferencesKey("show_right_progress")
        val MINIMAL_MODE = booleanPreferencesKey("minimal_mode")
        val AUTO_READ_INTERVAL = intPreferencesKey("auto_read_interval")
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val PAGE_ANIMATION = stringPreferencesKey("page_animation")
        val PAGE_TURN_MODE = stringPreferencesKey("page_turn_mode")
        val FULL_SCREEN_TAP_NEXT = booleanPreferencesKey("full_screen_tap_next")
        const val CURRENT_LAYOUT_DEFAULTS_VERSION = 4
        const val DEFAULT_FONT_SIZE_SP = 17f
        const val DEFAULT_LINE_SPACING = 1.75f
        const val DEFAULT_PARAGRAPH_SPACING_DP = 10f
        const val DEFAULT_HORIZONTAL_PADDING_DP = 20f
        const val DEFAULT_VERTICAL_PADDING_TOP_DP = 46f
        const val DEFAULT_VERTICAL_PADDING_BOTTOM_DP = 46f
        const val HISTORICAL_HORIZONTAL_PADDING_DP = 28f
        const val HISTORICAL_VERTICAL_PADDING_TOP_DP = 64f
        const val HISTORICAL_VERTICAL_PADDING_BOTTOM_DP = 56f
        const val COMPACT_VERTICAL_PADDING_TOP_DP = 48f
        const val COMPACT_VERTICAL_PADDING_BOTTOM_DP = 48f
        const val TITLE_SPACING_VERTICAL_PADDING_TOP_DP = 52f
        const val TITLE_SPACING_VERTICAL_PADDING_BOTTOM_DP = 42f
    }
}

private fun readerTheme(raw: String?): ReaderTheme = when (raw) {
    "DAY" -> ReaderTheme.LIGHT_GRAY
    null -> ReaderTheme.EYE_CARE
    else -> enumValue(raw, ReaderTheme.EYE_CARE)
}

private fun pageTurnMode(raw: String?): PageTurnMode = when (raw) {
    "COVER" -> PageTurnMode.SLIDE
    null -> PageTurnMode.HORIZONTAL
    else -> enumValue(raw, PageTurnMode.HORIZONTAL)
}

private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
    raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

internal fun migratedLayoutValue(
    raw: Float?,
    usesModernDefaults: Boolean,
    historicalDefault: Float,
    newDefault: Float,
): Float = migratedLayoutValue(raw, usesModernDefaults, listOf(historicalDefault), newDefault)

internal fun migratedLayoutValue(
    raw: Float?,
    usesModernDefaults: Boolean,
    historicalDefaults: List<Float>,
    newDefault: Float,
): Float = when {
    raw == null -> newDefault
    !usesModernDefaults && historicalDefaults.any { kotlin.math.abs(raw - it) < 0.001f } -> newDefault
    else -> raw
}

internal fun migratedJustifiedValue(raw: Boolean?, usesModernDefaults: Boolean): Boolean = when {
    raw == null -> true
    !usesModernDefaults && !raw -> true
    else -> raw
}
