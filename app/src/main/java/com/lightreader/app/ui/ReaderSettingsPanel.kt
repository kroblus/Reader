package com.lightreader.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.lightreader.app.core.reader.mergeReaderPreferenceEdit
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import com.lightreader.app.R
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.PageTurnMode
import com.lightreader.app.core.model.ReaderLayoutPreset
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import com.lightreader.app.core.reader.selectLayoutPreset
import com.lightreader.app.core.reader.layoutSnapshot
import com.lightreader.app.core.reader.applyLayout
import com.lightreader.app.core.reader.effectiveLayout
import com.lightreader.app.core.reader.palette
import kotlin.math.roundToInt

private data class SettingsDragActions(val start: () -> Unit = {}, val finish: () -> Unit = {})
private val LocalReaderToolBackground = staticCompositionLocalOf { Color.White }
private val LocalSettingsDrag = staticCompositionLocalOf { SettingsDragActions() }

/** Controls use one draft; drag previews are coalesced, and persistence happens on release
 * or disposal (including Back/navigation while a pointer is still down). */
@Composable
private fun SettingsDraftHost(
    value: ReaderPreferences,
    onChange: (ReaderPreferences) -> Unit,
    onPreview: (ReaderPreferences) -> Unit = {},
    content: @Composable (ReaderPreferences, (ReaderPreferences) -> Unit) -> Unit,
) {
    var draft by remember { mutableStateOf(value) }
    var baseline by remember { mutableStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    val save by rememberUpdatedState(onChange)
    val preview by rememberUpdatedState(onPreview)
    LaunchedEffect(value) {
        draft = if (dirty) mergeReaderPreferenceEdit(baseline, draft, value) else value
        baseline = value
    }
    LaunchedEffect(draft, dragging) {
        if (dragging && dirty) {
            delay(70)
            preview(draft)
        }
    }
    fun finish() {
        dragging = false
        if (dirty) { save(draft); dirty = false }
    }
    DisposableEffect(Unit) { onDispose { if (dirty) save(draft) } }
    val rendered = draft
    CompositionLocalProvider(LocalSettingsDrag provides SettingsDragActions({ dragging = true }, ::finish)) {
        content(rendered) { edit ->
            draft = mergeReaderPreferenceEdit(rendered, edit, draft)
            dirty = true
            if (!dragging) finish()
        }
    }
}

@Composable
fun ReaderSettingsPanel(
    value: ReaderPreferences,
    onChange: (ReaderPreferences) -> Unit,
    onOpenMoreSettings: () -> Unit,
    bottomPadding: Dp = 24.dp,
    onPreview: (ReaderPreferences) -> Unit = {},
) {
    SettingsDraftHost(value, onChange, onPreview) { draft, edit ->
        ReaderSettingsPanelContent(draft, edit, onOpenMoreSettings, bottomPadding)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderSettingsPanelContent(
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

    CompositionLocalProvider(LocalContentColor provides foreground, LocalReaderToolBackground provides Color(palette.overlay)) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag(ReaderTestTags.READER_SETTINGS_PANEL)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomPadding + 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsPanelSection(stringResource(R.string.settings_layout)) {
                PresetChoices(value, secondary, onChange)
            }

            SettingsPanelSection(stringResource(R.string.settings_quick_text)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SettingsPill("A-", false, secondary, modifier = Modifier.width(48.dp), contentDescription = stringResource(R.string.reader_font_decrease)) {
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
                                fontFamily = FontFamily.Default,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    SettingsPill("A+", false, secondary, modifier = Modifier.width(48.dp), contentDescription = stringResource(R.string.reader_font_increase)) {
                        onChange(value.customLayout().copy(fontSizeSp = (layout.fontSizeSp + 1f).coerceAtMost(36f)))
                    }
                    val spacingOptions = listOf(1.48f, 1.78f, 1.92f)
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
            SettingSlider(
                stringResource(R.string.settings_brightness),
                if (value.brightness < 0) stringResource(R.string.settings_follow_system) else "${(value.brightness * 100).roundToInt()}%",
                value.brightness.coerceAtLeast(.05f), .05f..1f, secondary,
            ) { onChange(value.copy(brightness = it)) }
            TextButton(onClick = { onChange(value.copy(brightness = -1f)) }) {
                Text(stringResource(R.string.settings_follow_system))
            }

        }
    }
}

@Composable
private fun PresetChoices(value: ReaderPreferences, primary: Color, onChange: (ReaderPreferences) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ReaderLayoutPreset.COMFORT, ReaderLayoutPreset.COMPACT, ReaderLayoutPreset.IMMERSIVE).forEach { preset ->
            PresetQuickCard(
                preset, preset.label(), preset.description(), value.layoutPreset == preset, primary,
                Modifier.weight(1f).testTag("reader_preset_${preset.name.lowercase()}"),
            ) { onChange(value.selectLayoutPreset(preset)) }
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
    val controlBackground by animateColorAsState(
        primary.copy(alpha = if (selected) .16f else .05f).compositeOver(LocalReaderToolBackground.current),
        tween(UiMotion.ColorDurationMillis), label = "presetSelection",
    )
    val labelColor = readableTextColor(LocalContentColor.current, controlBackground)
    val stateText = stringResource(if (selected) R.string.state_selected else R.string.state_not_selected)
    Column(
        modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (selected) primary.copy(alpha = .6f) else Color.Transparent,
                RoundedCornerShape(16.dp),
            )
            .background(controlBackground)
            .semantics {
                contentDescription = "$label. $description"
                stateDescription = stateText
            }
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = labelColor,
            maxLines = 2,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Default,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp,
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
            color = LocalContentColor.current,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
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
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
        )
        content()
    }
}

@Composable
fun ReaderSettingsDetailScreen(
    preferences: ReaderPreferences,
    autoReading: Boolean,
    onChange: (ReaderPreferences) -> Unit,
    onToggleAutoReading: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsDraftHost(preferences, onChange) { draft, edit ->
        ReaderSettingsDetailContent(draft, autoReading, edit, onToggleAutoReading, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderSettingsDetailContent(
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
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.SemiBold,
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
        CompositionLocalProvider(LocalContentColor provides foreground, LocalReaderToolBackground provides Color(palette.overlay)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val wide = maxWidth >= 720.dp
                val contentModifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 1040.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                if (wide) {
                    Row(contentModifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ReaderLayoutDetail(
                            preferences, layout, secondary, chipColors, onChange,
                            Modifier.weight(1f),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ReaderPageTurnDetail(preferences, secondary, chipColors, onChange)
                            ReaderBehaviorDetail(
                                preferences, autoReading, secondary, chipColors, onChange,
                                onToggleAutoReading, advancedExpanded,
                                onAdvancedExpandedChange = { advancedExpanded = it },
                            )
                            RestoreReadingDefaults(preferences, secondary, onChange)
                        }
                    }
                } else {
                    Column(contentModifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ReaderLayoutDetail(preferences, layout, secondary, chipColors, onChange)
                        ReaderPageTurnDetail(preferences, secondary, chipColors, onChange)
                        ReaderBehaviorDetail(
                            preferences, autoReading, secondary, chipColors, onChange, onToggleAutoReading,
                            advancedExpanded,
                            onAdvancedExpandedChange = { advancedExpanded = it },
                        )
                        RestoreReadingDefaults(preferences, secondary, onChange)
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
    var fineTuning by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(modifier) {
        DetailSectionTitle(stringResource(R.string.settings_layout))
        PresetChoices(preferences, secondary, onChange)
        TextButton(
            onClick = { onChange(preferences.selectLayoutPreset(ReaderLayoutPreset.CUSTOM)) },
            modifier = Modifier.testTag("reader_saved_layout"),
        ) {
            Text(stringResource(if (preferences.layoutPreset == ReaderLayoutPreset.CUSTOM)
                R.string.settings_using_custom else R.string.settings_restore_custom), color = secondary)
        }
        ReadingStylePreview(preferences)
        SettingSlider(stringResource(R.string.settings_font_size), "${layout.fontSizeSp.roundToInt()}", layout.fontSizeSp, 14f..36f, secondary) {
            onChange(preferences.customLayout().copy(fontSizeSp = it))
        }
        DetailSectionTitle(stringResource(R.string.settings_font))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FontFamilyOption.entries.forEach { family ->
                ReaderChoiceChip(family.label(), preferences.fontFamily == family, secondary, chipColors,
                    onClick = { onChange(preferences.copy(fontFamily = family)) },
                    modifier = Modifier.testTag("reader_font_${family.name.lowercase()}"))
            }
        }
        TextButton(
            onClick = { fineTuning = !fineTuning },
            modifier = Modifier.fillMaxWidth().testTag("reader_layout_fine_tune"),
        ) {
            Text(stringResource(R.string.settings_fine_tune), Modifier.weight(1f), color = secondary)
            Icon(if (fineTuning) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null, tint = secondary)
        }
        AnimatedVisibility(fineTuning,
            enter = expandVertically(tween(UiMotion.DurationMillis)) + fadeIn(tween(UiMotion.DurationMillis)),
            exit = shrinkVertically(tween(UiMotion.DurationMillis)) + fadeOut(tween(UiMotion.ColorDurationMillis))) {
            Column(Modifier.fillMaxWidth().testTag("reader_layout_fine_tune_content"),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SpacingChoices(stringResource(R.string.settings_line_spacing), layout.lineSpacingMultiplier,
                    listOf(1.48f, 1.78f, 1.92f), secondary, "line_spacing") {
                    onChange(preferences.customLayout().copy(lineSpacingMultiplier = it))
                }
                SpacingChoices(stringResource(R.string.settings_paragraph_spacing), layout.paragraphSpacingDp,
                    listOf(0f, 10f, 20f), secondary, "paragraph_spacing") {
                    onChange(preferences.customLayout().copy(paragraphSpacingDp = it))
                }
                SpacingChoices(stringResource(R.string.settings_horizontal_padding), layout.horizontalPaddingDp,
                    listOf(16f, 30f, 42f), secondary, "side_space") {
                    onChange(preferences.customLayout().copy(horizontalPaddingDp = it))
                }
                SpacingChoices(stringResource(R.string.settings_top_padding), layout.verticalPaddingTopDp,
                    listOf(34f, 58f, 76f), secondary, "top_space") {
                    onChange(preferences.customLayout().copy(verticalPaddingTopDp = it))
                }
                SpacingChoices(stringResource(R.string.settings_bottom_padding), layout.verticalPaddingBottomDp,
                    listOf(30f, 52f, 68f), secondary, "bottom_space") {
                    onChange(preferences.customLayout().copy(verticalPaddingBottomDp = it))
                }
                ReadingChoices(stringResource(R.string.settings_first_line_indent),
                    listOf(stringResource(R.string.settings_indent_none), stringResource(R.string.settings_indent_one),
                        stringResource(R.string.settings_indent_two), stringResource(R.string.settings_indent_three)),
                    if (preferences.firstLineIndent) preferences.firstLineIndentEm.roundToInt() else 0,
                    secondary, "indent") { index ->
                    onChange(preferences.copy(firstLineIndent = index > 0,
                        firstLineIndentEm = if (index > 0) index.toFloat() else preferences.firstLineIndentEm))
                }
                ReadingChoices(stringResource(R.string.settings_text_weight),
                    listOf(stringResource(R.string.settings_weight_regular), stringResource(R.string.settings_weight_heavier)),
                    if (preferences.fontWeight >= 500) 1 else 0, secondary, "weight") {
                    onChange(preferences.copy(fontWeight = if (it == 1) 600 else 400))
                }
                ReadingChoices(stringResource(R.string.settings_alignment),
                    listOf(stringResource(R.string.settings_alignment_natural), stringResource(R.string.settings_alignment_even)),
                    if (preferences.justified) 1 else 0, secondary, "alignment") {
                    onChange(preferences.copy(justified = it == 1))
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(LocalReaderToolBackground.current).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun ReadingStylePreview(preferences: ReaderPreferences) {
    val layout = preferences.effectiveLayout()
    val palette = preferences.palette()
    Column(
        Modifier.fillMaxWidth().testTag("reader_layout_preview").animateContentSize(tween(UiMotion.DurationMillis))
            .clip(RoundedCornerShape(16.dp)).background(Color(palette.background))
            .padding(horizontal = layout.horizontalPaddingDp.coerceIn(12f, 28f).dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_preview), color = Color(palette.secondary), style = MaterialTheme.typography.labelMedium)
        Text(
            text = (if (preferences.firstLineIndent) "　".repeat(preferences.firstLineIndentEm.roundToInt()) else "") + stringResource(R.string.settings_preview_text),
            color = Color(palette.foreground),
            fontSize = layout.fontSizeSp.sp,
            lineHeight = (layout.fontSizeSp * layout.lineSpacingMultiplier).sp,
            fontWeight = if (preferences.fontWeight >= 500) FontWeight.Bold else FontWeight.Normal,
            fontFamily = rememberReaderFontFamily(preferences.fontFamily, preferences.fontWeight),
            textAlign = if (preferences.justified) TextAlign.Justify else TextAlign.Start,
            maxLines = 3,
        )
    }
}

@Composable
private fun RestoreReadingDefaults(preferences: ReaderPreferences, primary: Color, onChange: (ReaderPreferences) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
        TextButton(onClick = { onChange(preferences.resetReadingDefaults()) },
            modifier = Modifier.testTag(ReaderTestTags.READER_RESTORE_DEFAULTS)) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.settings_restore_defaults), color = primary, modifier = Modifier.padding(start = 8.dp))
        }
        Text(stringResource(R.string.reader_reset_scope), style = MaterialTheme.typography.bodySmall, color = primary)
    }
}

@Composable
private fun SpacingChoices(label: String, value: Float, values: List<Float>, primary: Color, tag: String, onValue: (Float) -> Unit) {
    val nearest = values.indices.minBy { kotlin.math.abs(values[it] - value) }
    ReadingChoices(label, listOf(0.spacingLabel(), 1.spacingLabel(), 2.spacingLabel()), nearest, primary, tag) { onValue(values[it]) }
}

@Composable
private fun ReadingChoices(label: String, options: List<String>, selectedIndex: Int, primary: Color, tag: String, onChoice: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                SettingsPill(option, index == selectedIndex, primary,
                    Modifier.weight(1f).testTag("reader_${tag}_$index"),
                    contentDescription = "$label · $option") { onChoice(index) }
            }
        }
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
    SettingsGroup(modifier) {
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
    SettingsGroup(modifier) {
        TextButton(
            onClick = { onAdvancedExpandedChange(!advancedExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ReaderTestTags.READER_ADVANCED_SETTINGS),
        ) {
            Text(
                stringResource(R.string.settings_advanced),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Default,
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

        AnimatedVisibility(visible = advancedExpanded,
            enter = expandVertically(tween(UiMotion.DurationMillis)) + fadeIn(tween(UiMotion.DurationMillis)),
            exit = shrinkVertically(tween(UiMotion.DurationMillis)) + fadeOut(tween(UiMotion.ColorDurationMillis))) {
            Column(Modifier.fillMaxWidth().testTag(ReaderTestTags.READER_ADVANCED_CONTENT)) {
                RowSetting(stringResource(R.string.settings_brightness)) {
                    val brightnessState = if (preferences.brightness < 0f) {
                        stringResource(R.string.settings_follow_system)
                    } else {
                        stringResource(R.string.settings_brightness_percent, (preferences.brightness * 100).roundToInt())
                    }
                    val drag = LocalSettingsDrag.current
                    SoftSlider(
                        value = preferences.brightness.takeIf { it >= 0f } ?: .5f,
                        onValueChange = { drag.start(); onChange(preferences.copy(brightness = it)) },
                        onValueChangeFinished = drag.finish,
                        enabled = preferences.brightness >= 0f,
                        valueRange = .05f..1f,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { stateDescription = brightnessState },
                        primary = secondary,
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
                    Text(stringResource(R.string.settings_auto_read), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Default))
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
                fontFamily = FontFamily.Default,
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
    val controlBackground by animateColorAsState(
        primary.copy(alpha = if (selected) .20f else .07f).compositeOver(LocalReaderToolBackground.current),
        tween(UiMotion.ColorDurationMillis), label = "settingSelection",
    )
    val labelColor = readableTextColor(LocalContentColor.current, controlBackground)
    val stateText = stringResource(if (selected) R.string.state_selected else R.string.state_not_selected)
    Box(
        modifier
            .heightIn(min = ReaderUiTokens.iconTouchTarget)
            .clip(RoundedCornerShape(LightReaderShapeTokens.small))
            .border(
                1.dp,
                if (selected) primary.copy(alpha = .6f) else Color.Transparent,
                RoundedCornerShape(LightReaderShapeTokens.small),
            )
            .background(controlBackground)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = stateText
                this.selected = selected
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = labelColor,
            maxLines = 2,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.Default,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
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
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
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
private fun settingsChipColors(background: Color, foreground: Color, primary: Color): androidx.compose.material3.SelectableChipColors {
    val selectedBackground = primary.copy(alpha = .22f).compositeOver(background)
    return FilterChipDefaults.filterChipColors(
        containerColor = background,
        labelColor = readableTextColor(foreground, background),
        selectedContainerColor = selectedBackground,
        selectedLabelColor = readableTextColor(foreground, selectedBackground),
    )
}

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
        modifier = modifier.heightIn(min = 48.dp).border(
            width = 1.dp,
            color = if (selected) primary.copy(alpha = .55f) else Color.Transparent,
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
    val drag = LocalSettingsDrag.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Default))
            Text(valueText, color = primary, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Default))
        }
        SoftSlider(
            value = value,
            onValueChange = { drag.start(); onValue(it) },
            onValueChangeFinished = drag.finish,
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = label; stateDescription = "$label $valueText" },
            primary = primary,
        )
    }
}

/** Keep Slider's 48dp gesture/semantics area, with a quiet track and round thumb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoftSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    primary: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val color = if (enabled) primary else primary.copy(alpha = .35f)
    Slider(
        value = value, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        colors = readerSliderColors(primary),
        thumb = { Box(Modifier.size(20.dp).background(color, CircleShape)) },
        track = {
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                val start = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width
                val end = if (layoutDirection == LayoutDirection.Ltr) size.width else 0f
                drawLine(color.copy(alpha = .16f), Offset(0f, center.y), Offset(size.width, center.y), size.height, StrokeCap.Round)
                drawLine(color, Offset(start, center.y), Offset(start + (end - start) * fraction, center.y), size.height, StrokeCap.Round)
            }
        },
    )
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, primary: Color, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Default))
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
    FontFamilyOption.LXGW_WENKAI -> stringResource(R.string.font_lxgw_wenkai)
    FontFamilyOption.ZHUQUE_FANGSONG -> stringResource(R.string.font_zhuque_fangsong)
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

@Composable
private fun PageTurnMode.label(): String = when (this) {
    PageTurnMode.NONE -> stringResource(R.string.page_turn_none)
    PageTurnMode.HORIZONTAL -> stringResource(R.string.page_turn_horizontal)
    PageTurnMode.SLIDE -> stringResource(R.string.page_turn_slide)
    PageTurnMode.VERTICAL -> stringResource(R.string.page_turn_vertical)
    PageTurnMode.SCROLL -> stringResource(R.string.page_turn_scroll)
    PageTurnMode.SIMULATION -> stringResource(R.string.page_turn_simulation)
}

private fun ReaderPreferences.customLayout(): ReaderPreferences = applyLayout(layoutSnapshot())

private fun ReaderPreferences.resetReadingDefaults(): ReaderPreferences {
    val defaults = ReaderPreferences()
    return copy(
        layoutPreset = defaults.layoutPreset,
        lastCustomLayout = null,
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
