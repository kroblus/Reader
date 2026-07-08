package com.lightreader.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    autoReading: Boolean = false,
    onToggleAutoReading: (() -> Unit)? = null,
    onOpenMoreSettings: () -> Unit,
    bottomPadding: Dp = 24.dp,
) {
    val palette = value.palette()
    val foreground = Color(palette.foreground)
    val secondary = Color(palette.secondary)
    val layout = value.effectiveLayout()

    CompositionLocalProvider(LocalContentColor provides foreground) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = bottomPadding),
        ) {
            RowSetting(stringResource(R.string.settings_layout)) {
                listOf(
                    ReaderLayoutPreset.COMFORT,
                    ReaderLayoutPreset.COMPACT,
                    ReaderLayoutPreset.IMMERSIVE,
                    ReaderLayoutPreset.CUSTOM,
                ).forEach { preset ->
                    val label = preset.label()
                    SettingsPill(
                        text = label,
                        selected = value.layoutPreset == preset,
                        primary = secondary,
                        modifier = Modifier.weight(1f),
                        contentDescription = stringResource(R.string.settings_layout_preset, label),
                        onClick = {
                            onChange(if (preset == ReaderLayoutPreset.CUSTOM) value.customLayout() else value.withPreset(preset))
                        },
                    )
                }
            }
            DividerLine(secondary)

            RowSetting(stringResource(R.string.settings_brightness)) {
                val brightnessState = if (value.brightness < 0f) {
                    stringResource(R.string.settings_follow_system)
                } else {
                    stringResource(R.string.settings_brightness_percent, (value.brightness * 100).roundToInt())
                }
                Slider(
                    value = value.brightness.takeIf { it >= 0f } ?: .5f,
                    onValueChange = { onChange(value.copy(brightness = it)) },
                    enabled = value.brightness >= 0f,
                    valueRange = .05f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .semantics { stateDescription = brightnessState },
                    colors = readerSliderColors(secondary),
                )
                SettingsPill(
                    text = stringResource(R.string.settings_follow_system),
                    selected = value.brightness < 0f,
                    primary = secondary,
                    onClick = { onChange(value.copy(brightness = if (value.brightness < 0f) .5f else -1f)) },
                )
            }
            DividerLine(secondary)

            RowSetting(stringResource(R.string.settings_font_size)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsPill("A-", false, secondary, modifier = Modifier.weight(1f)) {
                        onChange(value.customLayout().copy(fontSizeSp = (layout.fontSizeSp - 1f).coerceAtLeast(14f)))
                    }
                    Box(
                        modifier = Modifier.weight(.72f).height(34.dp),
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
                    SettingsPill("A+", false, secondary, modifier = Modifier.weight(1f)) {
                        onChange(value.customLayout().copy(fontSizeSp = (layout.fontSizeSp + 1f).coerceAtMost(36f)))
                    }
                }
                SettingsPill(stringResource(R.string.settings_font), false, secondary, modifier = Modifier.width(58.dp)) {
                    val entries = FontFamilyOption.entries
                    val next = entries[(entries.indexOf(value.fontFamily) + 1) % entries.size]
                    onChange(value.copy(fontFamily = next))
                }
                SettingsPill(stringResource(R.string.settings_spacing), false, secondary, modifier = Modifier.width(58.dp)) {
                    val options = listOf(1.45f, 1.7f, 1.9f)
                    val current = options.indexOfFirst { kotlin.math.abs(layout.lineSpacingMultiplier - it) < .12f }.coerceAtLeast(0)
                    onChange(value.customLayout().copy(lineSpacingMultiplier = options[(current + 1) % options.size]))
                }
            }
            DividerLine(secondary)

            RowSetting(stringResource(R.string.settings_background)) {
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
            }
            DividerLine(secondary)

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsPill(stringResource(R.string.settings_more), false, secondary, modifier = Modifier.weight(1f), onClick = onOpenMoreSettings)
            }
        }
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
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                DetailSectionTitle(stringResource(R.string.settings_layout))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                ) {
                    listOf(
                        ReaderLayoutPreset.COMFORT,
                        ReaderLayoutPreset.COMPACT,
                        ReaderLayoutPreset.IMMERSIVE,
                        ReaderLayoutPreset.CUSTOM,
                    ).forEach { preset ->
                        FilterChip(
                            selected = preferences.layoutPreset == preset,
                            onClick = { onChange(if (preset == ReaderLayoutPreset.CUSTOM) preferences.customLayout() else preferences.withPreset(preset)) },
                            label = { Text(preset.label()) },
                            shape = RoundedCornerShape(8.dp),
                            colors = chipColors,
                        )
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
                SettingSlider(stringResource(R.string.settings_top_padding), "${layout.verticalPaddingTopDp.roundToInt()} dp", layout.verticalPaddingTopDp, 24f..92f, secondary) {
                    onChange(preferences.customLayout().copy(verticalPaddingTopDp = it))
                }
                SettingSlider(stringResource(R.string.settings_bottom_padding), "${layout.verticalPaddingBottomDp.roundToInt()} dp", layout.verticalPaddingBottomDp, 24f..84f, secondary) {
                    onChange(preferences.customLayout().copy(verticalPaddingBottomDp = it))
                }

                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FontFamilyOption.entries.forEach { family ->
                        FilterChip(
                            selected = preferences.fontFamily == family,
                            onClick = { onChange(preferences.copy(fontFamily = family)) },
                            label = { Text(family.label()) },
                            shape = RoundedCornerShape(8.dp),
                            colors = chipColors,
                        )
                    }
                    FilterChip(
                        selected = preferences.fontWeight == 400,
                        onClick = { onChange(preferences.copy(fontWeight = 400)) },
                        label = { Text(stringResource(R.string.settings_standard_weight)) },
                        shape = RoundedCornerShape(8.dp),
                        colors = chipColors,
                    )
                    FilterChip(
                        selected = preferences.fontWeight >= 500,
                        onClick = { onChange(preferences.copy(fontWeight = 600)) },
                        label = { Text(stringResource(R.string.settings_bold)) },
                        shape = RoundedCornerShape(8.dp),
                        colors = chipColors,
                    )
                }

                DetailSectionTitle(stringResource(R.string.settings_section_display))
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
                        PageTurnMode.SLIDE,
                        PageTurnMode.NONE,
                    ).forEach { mode ->
                        FilterChip(
                            selected = preferences.pageTurnMode == mode,
                            onClick = { onChange(preferences.copy(pageTurnMode = mode)) },
                            label = { Text(mode.label()) },
                            shape = RoundedCornerShape(8.dp),
                            colors = chipColors,
                        )
                    }
                }
                ToggleSetting(stringResource(R.string.settings_first_line_indent), preferences.firstLineIndent, secondary) { onChange(preferences.copy(firstLineIndent = it)) }
                if (preferences.firstLineIndent) {
                    SettingSlider(stringResource(R.string.settings_indent_width), "%.1f em".format(preferences.firstLineIndentEm), preferences.firstLineIndentEm, 1f..3f, secondary) {
                        onChange(preferences.copy(firstLineIndentEm = it))
                    }
                }
                ToggleSetting(stringResource(R.string.settings_justified), preferences.justified, secondary) { onChange(preferences.copy(justified = it)) }
                ToggleSetting(stringResource(R.string.settings_keep_screen_on), preferences.keepScreenOn, secondary) { onChange(preferences.copy(keepScreenOn = it)) }
                ToggleSetting(stringResource(R.string.settings_lock_portrait), preferences.lockPortrait, secondary) { onChange(preferences.copy(lockPortrait = it)) }
                ToggleSetting(stringResource(R.string.settings_show_progress), preferences.showStatus, secondary) { onChange(preferences.copy(showStatus = it)) }
                ToggleSetting(stringResource(R.string.settings_show_chapter_title), preferences.showHeader, secondary) { onChange(preferences.copy(showHeader = it)) }
                ToggleSetting(stringResource(R.string.settings_show_right_progress), preferences.showRightProgressBar, secondary) { onChange(preferences.copy(showRightProgressBar = it)) }
                ToggleSetting(stringResource(R.string.settings_fullscreen_tap_next), preferences.fullScreenTapNext, secondary) { onChange(preferences.copy(fullScreenTapNext = it)) }
                ToggleSetting(stringResource(R.string.settings_volume_keys), preferences.volumeKeys, secondary) { onChange(preferences.copy(volumeKeys = it)) }
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
                        FilterChip(
                            selected = preferences.theme == theme,
                            onClick = { onChange(preferences.copy(theme = theme)) },
                            label = { Text(theme.label()) },
                            shape = RoundedCornerShape(8.dp),
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
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) primary.copy(alpha = .78f) else primary.copy(alpha = .12f),
                RoundedCornerShape(8.dp),
            )
            .background(if (selected) primary.copy(alpha = .28f) else primary.copy(alpha = .10f))
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = stateText
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
        modifier.height(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .semantics { this.contentDescription = contentDescription }
                .clip(CircleShape)
                .background(Color(swatch.background))
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) Color(swatch.secondary) else Color(swatch.secondary).copy(alpha = .25f),
                    CircleShape,
                )
                .clickable { onChange(value.copy(theme = theme)) },
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
private fun PageTurnMode.label(): String = when (this) {
    PageTurnMode.NONE -> stringResource(R.string.page_turn_none)
    PageTurnMode.HORIZONTAL -> stringResource(R.string.page_turn_horizontal)
    PageTurnMode.SLIDE -> stringResource(R.string.page_turn_slide)
    PageTurnMode.VERTICAL -> stringResource(R.string.page_turn_vertical)
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
