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
import com.lightreader.app.core.model.PageAnimation
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore("reader_preferences")

class ReaderSettingsRepository(private val context: Context) {
    val preferences: Flow<ReaderPreferences> = context.readerDataStore.data.map { values ->
        ReaderPreferences(
            fontSizeSp = values[FONT_SIZE] ?: 20f,
            fontWeight = values[FONT_WEIGHT] ?: 400,
            lineSpacingMultiplier = values[LINE_SPACING] ?: 1.55f,
            paragraphSpacingSp = values[PARAGRAPH_SPACING] ?: 8f,
            firstLineIndent = values[FIRST_INDENT] ?: true,
            horizontalPaddingDp = values[H_PADDING] ?: 24f,
            verticalPaddingDp = values[V_PADDING] ?: 20f,
            justified = values[JUSTIFIED] ?: false,
            theme = enumValue(values[THEME], ReaderTheme.DAY),
            customBackground = values[CUSTOM_BG] ?: 0xFFF8F5EE,
            customForeground = values[CUSTOM_FG] ?: 0xFF24211D,
            brightness = values[BRIGHTNESS] ?: -1f,
            keepScreenOn = values[KEEP_SCREEN] ?: false,
            lockPortrait = values[LOCK_PORTRAIT] ?: true,
            showStatus = values[SHOW_STATUS] ?: true,
            volumeKeys = values[VOLUME_KEYS] ?: true,
            fontFamily = enumValue(values[FONT_FAMILY], FontFamilyOption.SERIF),
            pageAnimation = enumValue(values[PAGE_ANIMATION], PageAnimation.SLIDE),
        )
    }

    suspend fun save(value: ReaderPreferences) {
        context.readerDataStore.edit { values ->
            values[FONT_SIZE] = value.fontSizeSp
            values[FONT_WEIGHT] = value.fontWeight
            values[LINE_SPACING] = value.lineSpacingMultiplier
            values[PARAGRAPH_SPACING] = value.paragraphSpacingSp
            values[FIRST_INDENT] = value.firstLineIndent
            values[H_PADDING] = value.horizontalPaddingDp
            values[V_PADDING] = value.verticalPaddingDp
            values[JUSTIFIED] = value.justified
            values[THEME] = value.theme.name
            values[CUSTOM_BG] = value.customBackground
            values[CUSTOM_FG] = value.customForeground
            values[BRIGHTNESS] = value.brightness
            values[KEEP_SCREEN] = value.keepScreenOn
            values[LOCK_PORTRAIT] = value.lockPortrait
            values[SHOW_STATUS] = value.showStatus
            values[VOLUME_KEYS] = value.volumeKeys
            values[FONT_FAMILY] = value.fontFamily.name
            values[PAGE_ANIMATION] = value.pageAnimation.name
        }
    }

    private companion object {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val FONT_WEIGHT = intPreferencesKey("font_weight")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val PARAGRAPH_SPACING = floatPreferencesKey("paragraph_spacing")
        val FIRST_INDENT = booleanPreferencesKey("first_indent")
        val H_PADDING = floatPreferencesKey("horizontal_padding")
        val V_PADDING = floatPreferencesKey("vertical_padding")
        val JUSTIFIED = booleanPreferencesKey("justified")
        val THEME = stringPreferencesKey("theme")
        val CUSTOM_BG = longPreferencesKey("custom_background")
        val CUSTOM_FG = longPreferencesKey("custom_foreground")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val KEEP_SCREEN = booleanPreferencesKey("keep_screen")
        val LOCK_PORTRAIT = booleanPreferencesKey("lock_portrait")
        val SHOW_STATUS = booleanPreferencesKey("show_status")
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val PAGE_ANIMATION = stringPreferencesKey("page_animation")
    }
}

private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
    raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
