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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.PageTurnMode
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
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

    CompositionLocalProvider(LocalContentColor provides foreground) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = bottomPadding),
        ) {
            RowSetting("亮度") {
                Slider(
                    value = value.brightness.takeIf { it >= 0f } ?: .5f,
                    onValueChange = { onChange(value.copy(brightness = it)) },
                    enabled = value.brightness >= 0f,
                    valueRange = .05f..1f,
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = readerSliderColors(secondary),
                )
                SettingsPill(
                    text = "跟随系统",
                    selected = value.brightness < 0f,
                    primary = secondary,
                    onClick = { onChange(value.copy(brightness = if (value.brightness < 0f) .5f else -1f)) },
                )
            }
            DividerLine(secondary)

            RowSetting("字号") {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsPill("A-", false, secondary, modifier = Modifier.weight(1f)) {
                        onChange(value.copy(fontSizeSp = (value.fontSizeSp - 1f).coerceAtLeast(14f)))
                    }
                    Box(
                        modifier = Modifier.weight(.72f).height(34.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${value.fontSizeSp.roundToInt()}",
                            color = foreground,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    SettingsPill("A+", false, secondary, modifier = Modifier.weight(1f)) {
                        onChange(value.copy(fontSizeSp = (value.fontSizeSp + 1f).coerceAtMost(36f)))
                    }
                }
                SettingsPill("字体", false, secondary, modifier = Modifier.width(58.dp)) {
                    val entries = FontFamilyOption.entries
                    val next = entries[(entries.indexOf(value.fontFamily) + 1) % entries.size]
                    onChange(value.copy(fontFamily = next))
                }
                SettingsPill("间距", false, secondary, modifier = Modifier.width(58.dp)) {
                    val options = listOf(1.45f, 1.7f, 1.9f)
                    val current = options.indexOfFirst { kotlin.math.abs(value.lineSpacingMultiplier - it) < .12f }.coerceAtLeast(0)
                    onChange(value.copy(lineSpacingMultiplier = options[(current + 1) % options.size]))
                }
            }
            DividerLine(secondary)

            RowSetting("背景") {
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

            RowSetting("翻页") {
                listOf(
                    PageTurnMode.SIMULATION,
                    PageTurnMode.HORIZONTAL,
                    PageTurnMode.VERTICAL,
                    PageTurnMode.SLIDE,
                    PageTurnMode.NONE,
                ).forEach { mode ->
                    SettingsPill(
                        text = mode.label(),
                        selected = value.pageTurnMode == mode,
                        primary = secondary,
                        modifier = Modifier.weight(1f),
                        onClick = { onChange(value.copy(pageTurnMode = mode)) },
                    )
                }
            }
            DividerLine(secondary)

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsPill("护眼", value.theme == ReaderTheme.EYE_CARE, secondary, modifier = Modifier.weight(.82f)) {
                    onChange(value.copy(theme = ReaderTheme.EYE_CARE))
                }
                SettingsPill(if (autoReading) "停止自动" else "自动阅读", autoReading, secondary, modifier = Modifier.weight(1.15f)) {
                    onToggleAutoReading?.invoke()
                }
                SettingsPill("全屏点击", value.fullScreenTapNext, secondary, modifier = Modifier.weight(1.25f)) {
                    onChange(value.copy(fullScreenTapNext = !value.fullScreenTapNext))
                }
                SettingsPill("更多设置", false, secondary, modifier = Modifier.weight(1.25f), onClick = onOpenMoreSettings)
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
                        "更多设置",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
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
                DetailSectionTitle("排版")
                SettingSlider("字号", "${preferences.fontSizeSp.roundToInt()} sp", preferences.fontSizeSp, 14f..36f, secondary) {
                    onChange(preferences.copy(fontSizeSp = it))
                }
                SettingSlider("行距", "%.2f".format(preferences.lineSpacingMultiplier), preferences.lineSpacingMultiplier, 1.1f..2f, secondary) {
                    onChange(preferences.copy(lineSpacingMultiplier = it))
                }
                SettingSlider("段距", "${preferences.paragraphSpacingDp.roundToInt()} dp", preferences.paragraphSpacingDp, 0f..20f, secondary) {
                    onChange(preferences.copy(paragraphSpacingDp = it))
                }
                SettingSlider("左右边距", "${preferences.horizontalPaddingDp.roundToInt()} dp", preferences.horizontalPaddingDp, 12f..40f, secondary) {
                    onChange(preferences.copy(horizontalPaddingDp = it))
                }
                SettingSlider("顶部留白", "${preferences.verticalPaddingTopDp.roundToInt()} dp", preferences.verticalPaddingTopDp, 44f..92f, secondary) {
                    onChange(preferences.copy(verticalPaddingTopDp = it))
                }
                SettingSlider("底部留白", "${preferences.verticalPaddingBottomDp.roundToInt()} dp", preferences.verticalPaddingBottomDp, 40f..84f, secondary) {
                    onChange(preferences.copy(verticalPaddingBottomDp = it))
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
                        label = { Text("标准字重") },
                        shape = RoundedCornerShape(8.dp),
                        colors = chipColors,
                    )
                    FilterChip(
                        selected = preferences.fontWeight >= 500,
                        onClick = { onChange(preferences.copy(fontWeight = 600)) },
                        label = { Text("加粗") },
                        shape = RoundedCornerShape(8.dp),
                        colors = chipColors,
                    )
                }

                DetailSectionTitle("显示与操作")
                ToggleSetting("首行缩进", preferences.firstLineIndent, secondary) { onChange(preferences.copy(firstLineIndent = it)) }
                if (preferences.firstLineIndent) {
                    SettingSlider("缩进宽度", "%.1f em".format(preferences.firstLineIndentEm), preferences.firstLineIndentEm, 1f..3f, secondary) {
                        onChange(preferences.copy(firstLineIndentEm = it))
                    }
                }
                ToggleSetting("两端对齐", preferences.justified, secondary) { onChange(preferences.copy(justified = it)) }
                ToggleSetting("保持屏幕常亮", preferences.keepScreenOn, secondary) { onChange(preferences.copy(keepScreenOn = it)) }
                ToggleSetting("锁定竖屏", preferences.lockPortrait, secondary) { onChange(preferences.copy(lockPortrait = it)) }
                ToggleSetting("显示阅读进度", preferences.showStatus, secondary) { onChange(preferences.copy(showStatus = it)) }
                ToggleSetting("显示章节标题", preferences.showHeader, secondary) { onChange(preferences.copy(showHeader = it)) }
                ToggleSetting("显示右侧进度条", preferences.showRightProgressBar, secondary) { onChange(preferences.copy(showRightProgressBar = it)) }
                ToggleSetting("音量键翻页", preferences.volumeKeys, secondary) { onChange(preferences.copy(volumeKeys = it)) }
                SettingSlider(
                    "自动阅读速度",
                    "${preferences.autoReadIntervalSeconds} 秒/页",
                    preferences.autoReadIntervalSeconds.toFloat(),
                    3f..30f,
                    secondary,
                ) { onChange(preferences.copy(autoReadIntervalSeconds = it.roundToInt())) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("自动阅读", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif))
                    SettingsPill(if (autoReading) "停止自动" else "开始自动", autoReading, secondary, onClick = onToggleAutoReading)
                }

                DetailSectionTitle("全部背景")
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
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.width(60.dp),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            ),
        )
        Row(
            Modifier.weight(1f),
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
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) primary.copy(alpha = .24f) else primary.copy(alpha = .10f))
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
    Box(
        modifier.height(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .semantics { contentDescription = "阅读背景-${theme.label()}" }
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
        Slider(value = value, onValueChange = onValue, valueRange = range, colors = readerSliderColors(primary))
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

private fun FontFamilyOption.label(): String = when (this) {
    FontFamilyOption.SYSTEM -> "系统"
    FontFamilyOption.SANS -> "黑体"
    FontFamilyOption.SERIF -> "宋体"
    FontFamilyOption.MONOSPACE -> "等宽"
}

private fun ReaderTheme.label(): String = when (this) {
    ReaderTheme.EYE_CARE -> "护眼绿"
    ReaderTheme.SEPIA -> "米杏"
    ReaderTheme.LIGHT_GRAY -> "纸白"
    ReaderTheme.WARM_BROWN -> "暖棕"
    ReaderTheme.FROST_BLUE -> "蓝白"
    ReaderTheme.SAKURA_PINK -> "粉白"
    ReaderTheme.NIGHT -> "夜间"
    ReaderTheme.CUSTOM -> "自定义"
}

private fun PageTurnMode.label(): String = when (this) {
    PageTurnMode.NONE -> "无"
    PageTurnMode.HORIZONTAL -> "左右"
    PageTurnMode.SLIDE -> "平移"
    PageTurnMode.VERTICAL -> "上下"
    PageTurnMode.SIMULATION -> "仿真"
}
