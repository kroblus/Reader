package com.lightreader.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightreader.app.R
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.PageTurnMode
import com.lightreader.app.core.model.ReaderLayoutPreset
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import com.lightreader.app.core.reader.effectiveLayout
import com.lightreader.app.core.reader.palette
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsPanel(
    value: ReaderPreferences,
    onChange: (ReaderPreferences) -> Unit,
    onOpenMoreSettings: () -> Unit,
    bottomPadding: Dp = 24.dp,
) {
    val palette = value.palette()
    val foreground = Color(palette.foreground)
    val secondary = Color(palette.secondary)
    val layout = value.effectiveLayout()
    val compactAccessibilityLayout = LocalDensity.current.fontScale >= 1.2f

    CompositionLocalProvider(LocalContentColor provides foreground) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag(ReaderTestTags.READER_SETTINGS_PANEL)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomPadding + 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsPanelSection(stringResource(R.string.settings_layout)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ReaderLayoutPreset.COMFORT,
                        ReaderLayoutPreset.COMPACT,
                        ReaderLayoutPreset.IMMERSIVE,
                        ReaderLayoutPreset.CUSTOM,
                    ).forEach { preset ->
                        val label = preset.label()
                        PresetQuickCard(
                            preset = preset,
                            label = label,
                            description = preset.description(),
                            selected = value.layoutPreset == preset,
                            primary = secondary,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onChange(if (preset == ReaderLayoutPreset.CUSTOM) value.customLayout() else value.withPreset(preset))
                            },
                        )
                    }
                }
            }

            SettingsPanelSection(stringResource(R.string.settings_quick_text)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SettingsPill("A-", false, secondary, modifier = Modifier.width(42.dp)) {
                        onChange(value.customLayout().copy(fontSizeSp = (layout.fontSizeSp - 1f).coerceAtLeast(14f)))
                    }
                    Box(
                        modifier = Modifier.width(36.dp).height(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${layout.fontSizeSp.roundToInt()}",
                            color = foreground,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    SettingsPill("A+", false, secondary, modifier = Modifier.width(42.dp)) {
                        onChange(value.customLayout().copy(fontSizeSp = (layout.fontSizeSp + 1f).coerceAtMost(36f)))
                    }
                    val spacingOptions = listOf(1.48f, 1.72f, 1.92f)
                    val spacingIndex = spacingOptions.indices.minByOrNull {
                        kotlin.math.abs(layout.lineSpacingMultiplier - spacingOptions[it])
                    } ?: 1
                    SettingsPill(
                        stringResource(R.string.settings_line_spacing_short, spacingIndex.spacingLabel()),
                        false,
                        secondary,
                        modifier = Modifier.weight(1f),
                    ) {
                        onChange(value.customLayout().copy(lineSpacingMultiplier = spacingOptions[(spacingIndex + 1) % spacingOptions.size]))
                    }
                    SettingsPill(
                        stringResource(R.string.settings_font_value, value.fontFamily.label()),
                        false,
                        secondary,
                        modifier = Modifier.weight(1f),
                    ) {
                        val entries = FontFamilyOption.entries
                        val next = entries[(entries.indexOf(value.fontFamily) + 1) % entries.size]
                        onChange(value.copy(fontFamily = next))
                    }
                }
            }

            SettingsPanelSection(stringResource(R.string.settings_background)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(
                        ReaderTheme.LIGHT_GRAY,
                        ReaderTheme.EYE_CARE,
                        ReaderTheme.SEPIA,
                        ReaderTheme.WARM_BROWN,
                        ReaderTheme.FROST_BLUE,
                        ReaderTheme.SAKURA_PINK,
                    ).forEach { theme ->
                        BackgroundDot(theme, value, onChange, Modifier.weight(1f))
                    }
                    SettingsPill(
                        stringResource(
                            if (compactAccessibilityLayout) R.string.settings_more_short else R.string.settings_more,
                        ),
                        false,
                        secondary,
                        modifier = Modifier.width(104.dp).testTag(ReaderTestTags.READER_SETTINGS_MORE),
                        onClick = onOpenMoreSettings,
                    )
                }
            }

        }
    }
}

@Composable
private fun PresetQuickCard(
    preset: ReaderLayoutPreset,
    label: String,
    description: String,
    selected: Boolean,
    primary: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val stateText = stringResource(if (selected) R.string.state_selected else R.string.state_not_selected)
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(LightReaderShapeTokens.medium))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) primary.copy(alpha = .82f) else primary.copy(alpha = .16f),
                RoundedCornerShape(LightReaderShapeTokens.medium),
            )
            .background(if (selected) primary.copy(alpha = .24f) else primary.copy(alpha = .07f))
            .semantics {
                contentDescription = "$label. $description"
                stateDescription = stateText
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 9.sp,
            ),
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val lineFractions = when (preset) {
                ReaderLayoutPreset.COMPACT -> listOf(.96f, .88f, .92f)
                ReaderLayoutPreset.IMMERSIVE -> listOf(.72f, .64f, .68f)
                ReaderLayoutPreset.CUSTOM -> listOf(.84f, .68f, .78f)
                ReaderLayoutPreset.COMFORT -> listOf(.86f, .78f, .82f)
            }
            lineFractions.forEach { fraction ->
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = if (selected) .72f else .38f)),
                )
            }
        }
    }
}

@Composable
private fun SettingsPanelSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            title,
            color = LocalContentColor.current.copy(alpha = .88f),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
        )
        content()
    }
}

@Composable
private fun CompactSettingRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 34.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.width(72.dp),
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsDetailScreen(
    preferences: ReaderPreferences,
    autoReading: Boolean,
    onChange: (ReaderPreferences) -> Unit,
    onToggleAutoReading: () -> Unit,
    onBack: () -> Unit,
) {
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val palette = preferences.palette()
    val background = Color(palette.background)
    val foreground = Color(palette.foreground)
    val secondary = Color(palette.secondary)
    val chipColors = settingsChipColors(background, foreground, secondary)
    val layout = preferences.effectiveLayout()

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = foreground,
                    navigationIconContentColor = foreground,
                ),
                title = {
                    Text(
                        stringResource(R.string.settings_more),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        CompositionLocalProvider(LocalContentColor provides foreground) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val wide = maxWidth >= 720.dp
                val contentModifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 1040.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                if (wide) {
                    Row(contentModifier, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                        ReaderLayoutDetail(
                            preferences, layout, secondary, chipColors, onChange,
                            Modifier.weight(1f),
                        )
                        Column(Modifier.weight(1f)) {
                            ReaderPageTurnDetail(preferences, secondary, chipColors, onChange)
                            ReaderBehaviorDetail(
                                preferences, autoReading, secondary, chipColors, onChange,
                                onToggleAutoReading, advancedExpanded,
                                onAdvancedExpandedChange = { advancedExpanded = it },
                            )
                        }
                    }
                } else {
                    Column(contentModifier) {
                        ReaderPageTurnDetail(preferences, secondary, chipColors, onChange)
                        ReaderLayoutDetail(preferences, layout, secondary, chipColors, onChange)
                        ReaderBehaviorDetail(
                            preferences, autoReading, secondary, chipColors, onChange, onToggleAutoReading,
                            advancedExpanded,
                            onAdvancedExpandedChange = { advancedExpanded = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ReaderLayoutDetail(
    preferences: ReaderPreferences,
    layout: com.lightreader.app.core.reader.ReaderLayoutValues,
    secondary: Color,
    chipColors: androidx.compose.material3.SelectableChipColors,
    onChange: (ReaderPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        DetailSectionTitle(stringResource(R.string.settings_layout))
        Column(
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                ReaderLayoutPreset.COMFORT to ReaderLayoutPreset.COMPACT,
                ReaderLayoutPreset.IMMERSIVE to ReaderLayoutPreset.CUSTOM,
            ).forEach { (left, right) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(left, right).forEach { preset ->
                        PresetDetailCard(
                            preset = preset,
                            selected = preferences.layoutPreset == preset,
                            primary = secondary,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onChange(if (preset == ReaderLayoutPreset.CUSTOM) preferences.customLayout() else preferences.withPreset(preset))
                            },
                        )
                    }
                }
            }
        }
        SettingSlider(stringResource(R.string.settings_font_size), "${layout.fontSizeSp.roundToInt()} sp", layout.fontSizeSp, 14f..36f, secondary) {
            onChange(preferences.customLayout().copy(fontSizeSp = it))
        }
        SettingSlider(stringResource(R.string.settings_line_spacing), "%.2f".format(layout.lineSpacingMultiplier), layout.lineSpacingMultiplier, 1.1f..2f, secondary) {
            onChange(preferences.customLayout().copy(lineSpacingMultiplier = it))
        }
        SettingSlider(stringResource(R.string.settings_paragraph_spacing), "${layout.paragraphSpacingDp.roundToInt()} dp", layout.paragraphSpacingDp, 0f..20f, secondary) {
            onChange(preferences.customLayout().copy(paragraphSpacingDp = it))
        }
        SettingSlider(stringResource(R.string.settings_horizontal_padding), "${layout.horizontalPaddingDp.roundToInt()} dp", layout.horizontalPaddingDp, 12f..44f, secondary) {
            onChange(preferences.customLayout().copy(horizontalPaddingDp = it))
        }

        val whitespace = PageWhitespace.from(layout.verticalPaddingTopDp, layout.verticalPaddingBottomDp)
        RowSetting(stringResource(R.string.settings_page_whitespace)) {
            PageWhitespace.entries.forEach { option ->
                ReaderChoiceChip(
                    selected = whitespace == option,
                    onClick = {
                        val custom = preferences.customLayout()
                        onChange(
                            custom.copy(
                                verticalPaddingTopDp = option.topDp,
                                verticalPaddingBottomDp = option.bottomDp,
                            ),
                        )
                    },
                    label = option.label(),
                    primary = secondary,
                    colors = chipColors,
                )
            }
        }

        ToggleSetting(stringResource(R.string.settings_first_line_indent), preferences.firstLineIndent, secondary) {
            onChange(preferences.copy(firstLineIndent = it))
        }

        DetailSectionTitle(stringResource(R.string.settings_font))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FontFamilyOption.entries.forEach { family ->
                ReaderChoiceChip(
                    selected = preferences.fontFamily == family,
                    onClick = { onChange(preferences.copy(fontFamily = family)) },
                    label = family.label(),
                    primary = secondary,
                    colors = chipColors,
                )
            }
            ReaderChoiceChip(
                selected = preferences.fontWeight == 400,
                onClick = { onChange(preferences.copy(fontWeight = 400)) },
                label = stringResource(R.string.settings_standard_weight),
                primary = secondary,
                colors = chipColors,
            )
            ReaderChoiceChip(
                selected = preferences.fontWeight >= 500,
                onClick = { onChange(preferences.copy(fontWeight = 600)) },
                label = stringResource(R.string.settings_bold),
                primary = secondary,
                colors = chipColors,
            )
        }

        TextButton(
            onClick = { onChange(preferences.resetReadingDefaults()) },
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag(ReaderTestTags.READER_RESTORE_DEFAULTS),
        ) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.settings_restore_defaults), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun PresetDetailCard(
    preset: ReaderLayoutPreset,
    selected: Boolean,
    primary: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val label = preset.label()
    val description = preset.description()
    val stateText = stringResource(if (selected) R.string.state_selected else R.string.state_not_selected)
    Column(
        modifier
            .widthIn(min = 142.dp)
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(LightReaderShapeTokens.medium))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) primary.copy(alpha = .85f) else primary.copy(alpha = .18f),
                RoundedCornerShape(LightReaderShapeTokens.medium),
            )
            .background(if (selected) primary.copy(alpha = .20f) else primary.copy(alpha = .06f))
            .semantics {
                contentDescription = "$label. $description"
                stateDescription = stateText
            }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
        )
        Text(
            description,
            color = LocalContentColor.current.copy(alpha = .72f),
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ReaderPageTurnDetail(
    preferences: ReaderPreferences,
    primary: Color,
    chipColors: androidx.compose.material3.SelectableChipColors,
    onChange: (ReaderPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        DetailSectionTitle(stringResource(R.string.settings_reading_controls))
        DetailSectionTitle(stringResource(R.string.settings_page_turn))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        ) {
            listOf(
                PageTurnMode.SIMULATION,
                PageTurnMode.HORIZONTAL,
                PageTurnMode.VERTICAL,
                PageTurnMode.SCROLL,
                PageTurnMode.SLIDE,
                PageTurnMode.NONE,
            ).forEach { mode ->
                ReaderChoiceChip(
                    selected = preferences.pageTurnMode == mode,
                    onClick = { onChange(preferences.copy(pageTurnMode = mode)) },
                    label = mode.label(),
                    primary = primary,
                    colors = chipColors,
                )
            }
        }
    }
}

@Composable
private fun ReaderBehaviorDetail(
    preferences: ReaderPreferences,
    autoReading: Boolean,
    secondary: Color,
    chipColors: androidx.compose.material3.SelectableChipColors,
    onChange: (ReaderPreferences) -> Unit,
    onToggleAutoReading: () -> Unit,
    advancedExpanded: Boolean,
    onAdvancedExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TextButton(
            onClick = { onAdvancedExpandedChange(!advancedExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .testTag(ReaderTestTags.READER_ADVANCED_SETTINGS),
        ) {
            Text(
                stringResource(R.string.settings_advanced),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Icon(
                if (advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = stringResource(
                    if (advancedExpanded) R.string.settings_hide_advanced else R.string.settings_show_advanced,
                ),
            )
        }

        AnimatedVisibility(visible = advancedExpanded) {
            Column(Modifier.fillMaxWidth().testTag(ReaderTestTags.READER_ADVANCED_CONTENT)) {
                RowSetting(stringResource(R.string.settings_brightness)) {
                    val brightnessState = if (preferences.brightness < 0f) {
                        stringResource(R.string.settings_follow_system)
                    } else {
                        stringResource(R.string.settings_brightness_percent, (preferences.brightness * 100).roundToInt())
                    }
                    Slider(
                        value = preferences.brightness.takeIf { it >= 0f } ?: .5f,
                        onValueChange = { onChange(preferences.copy(brightness = it)) },
                        enabled = preferences.brightness >= 0f,
                        valueRange = .05f..1f,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { stateDescription = brightnessState },
                        colors = readerSliderColors(secondary),
                    )
                    SettingsPill(
                        text = stringResource(R.string.settings_follow_system),
                        selected = preferences.brightness < 0f,
                        primary = secondary,
                        modifier = Modifier.width(112.dp),
                        onClick = {
                            onChange(preferences.copy(brightness = if (preferences.brightness < 0f) .5f else -1f))
                        },
                    )
                }
                val layout = preferences.effectiveLayout()
                SettingSlider(stringResource(R.string.settings_top_padding), "${layout.verticalPaddingTopDp.roundToInt()} dp", layout.verticalPaddingTopDp, 24f..92f, secondary) {
                    onChange(preferences.customLayout().copy(verticalPaddingTopDp = it))
                }
                SettingSlider(stringResource(R.string.settings_bottom_padding), "${layout.verticalPaddingBottomDp.roundToInt()} dp", layout.verticalPaddingBottomDp, 24f..84f, secondary) {
                    onChange(preferences.customLayout().copy(verticalPaddingBottomDp = it))
                }
                if (preferences.firstLineIndent) {
                    SettingSlider(stringResource(R.string.settings_indent_width), "%.1f em".format(preferences.firstLineIndentEm), preferences.firstLineIndentEm, 1f..3f, secondary) {
                        onChange(preferences.copy(firstLineIndentEm = it))
                    }
                }
                ToggleSetting(stringResource(R.string.settings_justified), preferences.justified, secondary) { onChange(preferences.copy(justified = it)) }
                ToggleSetting(stringResource(R.string.settings_show_progress), preferences.showStatus, secondary) { onChange(preferences.copy(showStatus = it)) }
                ToggleSetting(stringResource(R.string.settings_show_chapter_title), preferences.showHeader, secondary) { onChange(preferences.copy(showHeader = it)) }
                ToggleSetting(stringResource(R.string.settings_show_right_progress), preferences.showRightProgressBar, secondary) { onChange(preferences.copy(showRightProgressBar = it)) }
                ToggleSetting(stringResource(R.string.settings_fullscreen_tap_next), preferences.fullScreenTapNext, secondary) { onChange(preferences.copy(fullScreenTapNext = it)) }
                ToggleSetting(stringResource(R.string.settings_volume_keys), preferences.volumeKeys, secondary) { onChange(preferences.copy(volumeKeys = it)) }
                ToggleSetting(stringResource(R.string.settings_keep_screen_on), preferences.keepScreenOn, secondary) { onChange(preferences.copy(keepScreenOn = it)) }
                ToggleSetting(stringResource(R.string.settings_lock_portrait), preferences.lockPortrait, secondary) { onChange(preferences.copy(lockPortrait = it)) }
                SettingSlider(
                    stringResource(R.string.settings_auto_read_speed),
                    stringResource(R.string.settings_seconds_per_page, preferences.autoReadIntervalSeconds),
                    preferences.autoReadIntervalSeconds.toFloat(),
                    3f..30f,
                    secondary,
                ) { onChange(preferences.copy(autoReadIntervalSeconds = it.roundToInt())) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_auto_read), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif))
                    SettingsPill(
                        if (autoReading) stringResource(R.string.settings_stop_auto) else stringResource(R.string.settings_start_auto),
                        autoReading,
                        secondary,
                        onClick = onToggleAutoReading,
                    )
                }

                DetailSectionTitle(stringResource(R.string.settings_all_backgrounds))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 28.dp),
                ) {
                    ReaderTheme.entries.forEach { theme ->
                        ReaderChoiceChip(
                            selected = preferences.theme == theme,
                            onClick = {
                                onChange(
                                    preferences.copy(
                                        theme = theme,
                                        lastNonNightTheme = if (theme == ReaderTheme.NIGHT) preferences.lastNonNightTheme else theme,
                                    ),
                                )
                            },
                            label = theme.label(),
                            primary = secondary,
                            colors = chipColors,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowSetting(label: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            ),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsPill(
    text: String,
    selected: Boolean,
    primary: Color,
    modifier: Modifier = Modifier,
    contentDescription: String = text,
    onClick: () -> Unit,
) {
    val stateText = stringResource(if (selected) R.string.state_selected else R.string.state_not_selected)
    Box(
        modifier
            .heightIn(min = ReaderUiTokens.iconTouchTarget)
            .clip(RoundedCornerShape(LightReaderShapeTokens.small))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) primary.copy(alpha = .78f) else primary.copy(alpha = .12f),
                RoundedCornerShape(LightReaderShapeTokens.small),
            )
            .background(if (selected) primary.copy(alpha = .28f) else primary.copy(alpha = .10f))
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = stateText
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            maxLines = 2,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 12.sp,
            ),
        )
    }
}

@Composable
private fun BackgroundDot(
    theme: ReaderTheme,
    value: ReaderPreferences,
    onChange: (ReaderPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swatch = value.copy(theme = theme).palette()
    val selected = value.theme == theme
    val label = theme.label()
    val contentDescription = stringResource(R.string.settings_reader_background, label)
    Box(
        modifier
            .height(ReaderUiTokens.iconTouchTarget)
            .semantics { this.contentDescription = contentDescription }
            .clickable { onChange(value.copy(theme = theme)) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(swatch.background))
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) Color(swatch.secondary) else Color(swatch.secondary).copy(alpha = .25f),
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun DetailSectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun DividerLine(color: Color) {
    HorizontalDivider(color = color.copy(alpha = .075f))
}

@Composable
private fun readerSliderColors(primary: Color) = SliderDefaults.colors(
    thumbColor = primary,
    activeTrackColor = primary,
    inactiveTrackColor = primary.copy(alpha = .18f),
    disabledThumbColor = primary.copy(alpha = .35f),
    disabledActiveTrackColor = primary.copy(alpha = .18f),
    disabledInactiveTrackColor = primary.copy(alpha = .1f),
)

@Composable
private fun settingsChipColors(background: Color, foreground: Color, primary: Color) =
    FilterChipDefaults.filterChipColors(
        containerColor = background.copy(alpha = .38f),
        labelColor = foreground,
        selectedContainerColor = primary.copy(alpha = .22f),
        selectedLabelColor = foreground,
    )

@Composable
private fun ReaderChoiceChip(
    label: String,
    selected: Boolean,
    primary: Color,
    colors: androidx.compose.material3.SelectableChipColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(LightReaderShapeTokens.medium)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        modifier = modifier.border(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) primary.copy(alpha = .82f) else primary.copy(alpha = .18f),
            shape = shape,
        ),
        shape = shape,
        colors = colors,
        border = null,
    )
}

@Composable
private fun SettingSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    primary: Color,
    onValue: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif))
            Text(valueText, color = primary, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif))
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            modifier = Modifier.semantics { stateDescription = "$label $valueText" },
            colors = readerSliderColors(primary),
        )
    }
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, primary: Color, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = primary,
                uncheckedThumbColor = primary.copy(alpha = .58f),
                uncheckedTrackColor = primary.copy(alpha = .12f),
                uncheckedBorderColor = primary.copy(alpha = .42f),
            ),
        )
    }
}

@Composable
private fun FontFamilyOption.label(): String = when (this) {
    FontFamilyOption.SYSTEM -> stringResource(R.string.font_system)
    FontFamilyOption.SANS -> stringResource(R.string.font_sans)
    FontFamilyOption.SERIF -> stringResource(R.string.font_serif)
    FontFamilyOption.MONOSPACE -> stringResource(R.string.font_monospace)
}

@Composable
private fun ReaderTheme.label(): String = when (this) {
    ReaderTheme.EYE_CARE -> stringResource(R.string.theme_eye_care)
    ReaderTheme.SEPIA -> stringResource(R.string.theme_sepia)
    ReaderTheme.LIGHT_GRAY -> stringResource(R.string.theme_light_gray)
    ReaderTheme.WARM_BROWN -> stringResource(R.string.theme_warm_brown)
    ReaderTheme.FROST_BLUE -> stringResource(R.string.theme_frost_blue)
    ReaderTheme.SAKURA_PINK -> stringResource(R.string.theme_sakura_pink)
    ReaderTheme.NIGHT -> stringResource(R.string.theme_night)
    ReaderTheme.CUSTOM -> stringResource(R.string.theme_custom)
}

@Composable
private fun ReaderLayoutPreset.label(): String = when (this) {
    ReaderLayoutPreset.COMFORT -> stringResource(R.string.layout_comfort)
    ReaderLayoutPreset.COMPACT -> stringResource(R.string.layout_compact)
    ReaderLayoutPreset.IMMERSIVE -> stringResource(R.string.layout_immersive)
    ReaderLayoutPreset.CUSTOM -> stringResource(R.string.layout_custom)
}

@Composable
private fun ReaderLayoutPreset.description(): String = when (this) {
    ReaderLayoutPreset.COMFORT -> stringResource(R.string.settings_preset_comfort_desc)
    ReaderLayoutPreset.COMPACT -> stringResource(R.string.settings_preset_compact_desc)
    ReaderLayoutPreset.IMMERSIVE -> stringResource(R.string.settings_preset_immersive_desc)
    ReaderLayoutPreset.CUSTOM -> stringResource(R.string.settings_preset_custom_desc)
}

@Composable
private fun Int.spacingLabel(): String = when (this) {
    0 -> stringResource(R.string.settings_spacing_compact)
    2 -> stringResource(R.string.settings_spacing_relaxed)
    else -> stringResource(R.string.settings_spacing_standard)
}

private enum class PageWhitespace(val topDp: Float, val bottomDp: Float) {
    COMPACT(34f, 30f),
    STANDARD(58f, 52f),
    WIDE(76f, 68f),
    ;

    companion object {
        fun from(topDp: Float, bottomDp: Float): PageWhitespace {
            val average = (topDp + bottomDp) / 2f
            return when {
                average <= 38f -> COMPACT
                average <= 62f -> STANDARD
                else -> WIDE
            }
        }
    }
}

@Composable
private fun PageWhitespace.label(): String = when (this) {
    PageWhitespace.COMPACT -> stringResource(R.string.settings_whitespace_compact)
    PageWhitespace.STANDARD -> stringResource(R.string.settings_whitespace_standard)
    PageWhitespace.WIDE -> stringResource(R.string.settings_whitespace_wide)
}

@Composable
private fun PageTurnMode.label(): String = when (this) {
    PageTurnMode.NONE -> stringResource(R.string.page_turn_none)
    PageTurnMode.HORIZONTAL -> stringResource(R.string.page_turn_horizontal)
    PageTurnMode.SLIDE -> stringResource(R.string.page_turn_slide)
    PageTurnMode.VERTICAL -> stringResource(R.string.page_turn_vertical)
    PageTurnMode.SCROLL -> stringResource(R.string.page_turn_scroll)
    PageTurnMode.SIMULATION -> stringResource(R.string.page_turn_simulation)
}

private fun ReaderPreferences.withPreset(preset: ReaderLayoutPreset): ReaderPreferences {
    val values = copy(layoutPreset = preset).effectiveLayout()
    return copy(
        layoutPreset = preset,
        fontSizeSp = values.fontSizeSp,
        lineSpacingMultiplier = values.lineSpacingMultiplier,
        paragraphSpacingDp = values.paragraphSpacingDp,
        horizontalPaddingDp = values.horizontalPaddingDp,
        verticalPaddingTopDp = values.verticalPaddingTopDp,
        verticalPaddingBottomDp = values.verticalPaddingBottomDp,
    )
}

private fun ReaderPreferences.customLayout(): ReaderPreferences {
    if (layoutPreset == ReaderLayoutPreset.CUSTOM) return this
    val values = effectiveLayout()
    return copy(
        layoutPreset = ReaderLayoutPreset.CUSTOM,
        fontSizeSp = values.fontSizeSp,
        lineSpacingMultiplier = values.lineSpacingMultiplier,
        paragraphSpacingDp = values.paragraphSpacingDp,
        horizontalPaddingDp = values.horizontalPaddingDp,
        verticalPaddingTopDp = values.verticalPaddingTopDp,
        verticalPaddingBottomDp = values.verticalPaddingBottomDp,
    )
}

private fun ReaderPreferences.resetReadingDefaults(): ReaderPreferences {
    val defaults = ReaderPreferences()
    return copy(
        layoutPreset = defaults.layoutPreset,
        fontSizeSp = defaults.fontSizeSp,
        fontWeight = defaults.fontWeight,
        lineSpacingMultiplier = defaults.lineSpacingMultiplier,
        paragraphSpacingDp = defaults.paragraphSpacingDp,
        firstLineIndent = defaults.firstLineIndent,
        firstLineIndentEm = defaults.firstLineIndentEm,
        horizontalPaddingDp = defaults.horizontalPaddingDp,
        verticalPaddingTopDp = defaults.verticalPaddingTopDp,
        verticalPaddingBottomDp = defaults.verticalPaddingBottomDp,
        justified = defaults.justified,
        theme = defaults.theme,
        lastNonNightTheme = defaults.lastNonNightTheme,
        customBackground = defaults.customBackground,
        customForeground = defaults.customForeground,
        customSecondary = defaults.customSecondary,
        brightness = defaults.brightness,
        keepScreenOn = defaults.keepScreenOn,
        lockPortrait = defaults.lockPortrait,
        showStatus = defaults.showStatus,
        showHeader = defaults.showHeader,
        showRightProgressBar = defaults.showRightProgressBar,
        minimalMode = defaults.minimalMode,
        autoReadIntervalSeconds = defaults.autoReadIntervalSeconds,
        volumeKeys = defaults.volumeKeys,
        fontFamily = defaults.fontFamily,
        pageTurnMode = defaults.pageTurnMode,
        fullScreenTapNext = defaults.fullScreenTapNext,
    )
}
