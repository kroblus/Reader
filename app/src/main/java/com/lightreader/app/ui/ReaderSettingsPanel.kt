package com.lightreader.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.dp
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.PageTurnMode
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import com.lightreader.app.core.reader.palette
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsPanel(value: ReaderPreferences, onChange: (ReaderPreferences) -> Unit, onClose: (() -> Unit)? = null) {
    val palette = value.palette()
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color(palette.background).copy(alpha = .42f),
        labelColor = Color(palette.foreground),
        selectedContainerColor = Color(palette.secondary).copy(alpha = .32f),
        selectedLabelColor = Color(palette.foreground),
        disabledContainerColor = Color(palette.background).copy(alpha = .2f),
        disabledLabelColor = Color(palette.secondary).copy(alpha = .6f),
    )
    CompositionLocalProvider(LocalContentColor provides Color(palette.foreground)) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("阅读设置", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            onClose?.let { TextButton(onClick = it) { Text("关闭") } }
        }
        SettingSlider("字号", "${value.fontSizeSp.roundToInt()} sp", value.fontSizeSp, 14f..36f) { onChange(value.copy(fontSizeSp = it)) }
        SettingSlider("行距", "%.2f".format(value.lineSpacingMultiplier), value.lineSpacingMultiplier, 1.1f..2f) { onChange(value.copy(lineSpacingMultiplier = it)) }
        SettingSlider("段距", "${value.paragraphSpacingDp.roundToInt()} dp", value.paragraphSpacingDp, 0f..20f) { onChange(value.copy(paragraphSpacingDp = it)) }
        SettingSlider("左右边距", "${value.horizontalPaddingDp.roundToInt()} dp", value.horizontalPaddingDp, 12f..40f) { onChange(value.copy(horizontalPaddingDp = it)) }
        SettingSlider("顶部留白", "${value.verticalPaddingTopDp.roundToInt()} dp", value.verticalPaddingTopDp, 44f..92f) { onChange(value.copy(verticalPaddingTopDp = it)) }
        SettingSlider("底部留白", "${value.verticalPaddingBottomDp.roundToInt()} dp", value.verticalPaddingBottomDp, 40f..84f) { onChange(value.copy(verticalPaddingBottomDp = it)) }

        Text("字体")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontFamilyOption.entries.forEach { option ->
                FilterChip(
                    selected = value.fontFamily == option,
                    onClick = { onChange(value.copy(fontFamily = option)) },
                    label = { Text(when (option) { FontFamilyOption.SYSTEM -> "系统"; FontFamilyOption.SANS -> "黑体"; FontFamilyOption.SERIF -> "宋体"; FontFamilyOption.MONOSPACE -> "等宽" }) },
                    shape = RoundedCornerShape(50),
                    colors = chipColors,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = value.fontWeight == 400, onClick = { onChange(value.copy(fontWeight = 400)) }, label = { Text("标准") }, shape = RoundedCornerShape(50), colors = chipColors)
            FilterChip(selected = value.fontWeight >= 500, onClick = { onChange(value.copy(fontWeight = 600)) }, label = { Text("加粗") }, shape = RoundedCornerShape(50), colors = chipColors)
        }

        Text("主题")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderTheme.entries.forEach { theme ->
                FilterChip(
                    selected = value.theme == theme,
                    onClick = { onChange(value.copy(theme = theme)) },
                    label = { Text(theme.label()) },
                    shape = RoundedCornerShape(50),
                    colors = chipColors,
                )
            }
        }
        if (value.theme == ReaderTheme.CUSTOM) {
            Text("自定义配色")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = value.customBackground == 0xFFF8F5EE,
                    onClick = { onChange(value.copy(customBackground = 0xFFF8F5EE, customForeground = 0xFF24211D, customSecondary = 0xFF8A7A5C)) },
                    label = { Text("米白") },
                    shape = RoundedCornerShape(50), colors = chipColors,
                )
                FilterChip(
                    selected = value.customBackground == 0xFFE8F0E8,
                    onClick = { onChange(value.copy(customBackground = 0xFFE8F0E8, customForeground = 0xFF223027, customSecondary = 0xFF607466)) },
                    label = { Text("青灰") },
                    shape = RoundedCornerShape(50), colors = chipColors,
                )
                FilterChip(
                    selected = value.customBackground == 0xFF20242A,
                    onClick = { onChange(value.copy(customBackground = 0xFF20242A, customForeground = 0xFFE7E9EC, customSecondary = 0xFF8E99A8)) },
                    label = { Text("深灰") },
                    shape = RoundedCornerShape(50), colors = chipColors,
                )
            }
        }

        Text("翻页效果")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PageTurnMode.entries.forEach { mode ->
                FilterChip(
                    selected = value.pageTurnMode == mode,
                    onClick = { onChange(value.copy(pageTurnMode = mode)) },
                    label = { Text(mode.label()) },
                    shape = RoundedCornerShape(50),
                    colors = chipColors,
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ToggleSetting("首行缩进", value.firstLineIndent) { onChange(value.copy(firstLineIndent = it)) }
        if (value.firstLineIndent) {
            SettingSlider("缩进宽度", "%.1f em".format(value.firstLineIndentEm), value.firstLineIndentEm, 1f..3f) { onChange(value.copy(firstLineIndentEm = it)) }
        }
        ToggleSetting("两端对齐", value.justified) { onChange(value.copy(justified = it)) }
        ToggleSetting("保持屏幕常亮", value.keepScreenOn) { onChange(value.copy(keepScreenOn = it)) }
        ToggleSetting("锁定竖屏", value.lockPortrait) { onChange(value.copy(lockPortrait = it)) }
        ToggleSetting("显示阅读进度", value.showStatus) { onChange(value.copy(showStatus = it)) }
        ToggleSetting("显示章节标题", value.showHeader) { onChange(value.copy(showHeader = it)) }
        ToggleSetting("显示右侧进度条", value.showRightProgressBar) { onChange(value.copy(showRightProgressBar = it)) }
        ToggleSetting("极简模式", value.minimalMode) { onChange(value.copy(minimalMode = it)) }
        SettingSlider(
            "自动阅读速度",
            "${value.autoReadIntervalSeconds} 秒/页",
            value.autoReadIntervalSeconds.toFloat(),
            3f..30f,
        ) { onChange(value.copy(autoReadIntervalSeconds = it.roundToInt())) }
        ToggleSetting("音量键翻页", value.volumeKeys) { onChange(value.copy(volumeKeys = it)) }
        ToggleSetting("跟随系统亮度", value.brightness < 0f) {
            onChange(value.copy(brightness = if (it) -1f else .5f))
        }
        if (value.brightness >= 0f) {
            SettingSlider("阅读亮度", "${(value.brightness * 100).roundToInt()}%", value.brightness, .05f..1f) { onChange(value.copy(brightness = it)) }
        }
    }
    }
}

private fun ReaderTheme.label(): String = when (this) {
    ReaderTheme.EYE_CARE -> "护眼绿"
    ReaderTheme.SEPIA -> "米黄色"
    ReaderTheme.LIGHT_GRAY -> "浅灰白"
    ReaderTheme.WARM_BROWN -> "暖棕色"
    ReaderTheme.NIGHT -> "夜间"
    ReaderTheme.CUSTOM -> "自定义"
}

private fun PageTurnMode.label(): String = when (this) {
    PageTurnMode.NONE -> "无动画"
    PageTurnMode.HORIZONTAL -> "左右"
    PageTurnMode.SLIDE -> "平移"
    PageTurnMode.VERTICAL -> "上下"
    PageTurnMode.SIMULATION -> "仿真"
}

@Composable
private fun SettingSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(valueText, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = primary,
                activeTrackColor = primary,
                inactiveTrackColor = primary.copy(alpha = .18f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = primary,
                uncheckedThumbColor = primary.copy(alpha = .62f),
                uncheckedTrackColor = primary.copy(alpha = .14f),
                uncheckedBorderColor = primary.copy(alpha = .5f),
            ),
        )
    }
}
