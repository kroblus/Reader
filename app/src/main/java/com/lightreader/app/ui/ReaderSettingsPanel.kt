package com.lightreader.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.PageAnimation
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import kotlin.math.roundToInt

@Composable
fun ReaderSettingsPanel(value: ReaderPreferences, onChange: (ReaderPreferences) -> Unit, onClose: (() -> Unit)? = null) {
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
        SettingSlider("段距", "${value.paragraphSpacingSp.roundToInt()} sp", value.paragraphSpacingSp, 0f..20f) { onChange(value.copy(paragraphSpacingSp = it)) }
        SettingSlider("左右边距", "${value.horizontalPaddingDp.roundToInt()} dp", value.horizontalPaddingDp, 12f..40f) { onChange(value.copy(horizontalPaddingDp = it)) }
        SettingSlider("上下边距", "${value.verticalPaddingDp.roundToInt()} dp", value.verticalPaddingDp, 8f..36f) { onChange(value.copy(verticalPaddingDp = it)) }

        Text("字体")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontFamilyOption.entries.forEach { option ->
                FilterChip(
                    selected = value.fontFamily == option,
                    onClick = { onChange(value.copy(fontFamily = option)) },
                    label = { Text(when (option) { FontFamilyOption.SANS -> "黑体"; FontFamilyOption.SERIF -> "宋体"; FontFamilyOption.MONOSPACE -> "等宽" }) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = value.fontWeight == 400, onClick = { onChange(value.copy(fontWeight = 400)) }, label = { Text("标准") })
            FilterChip(selected = value.fontWeight >= 500, onClick = { onChange(value.copy(fontWeight = 600)) }, label = { Text("加粗") })
        }

        Text("主题")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderTheme.entries.forEach { theme ->
                FilterChip(
                    selected = value.theme == theme,
                    onClick = { onChange(value.copy(theme = theme)) },
                    label = { Text(when (theme) { ReaderTheme.DAY -> "日间"; ReaderTheme.SEPIA -> "护眼"; ReaderTheme.NIGHT -> "夜间"; ReaderTheme.CUSTOM -> "自定义" }) },
                )
            }
        }
        if (value.theme == ReaderTheme.CUSTOM) {
            Text("自定义配色")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = value.customBackground == 0xFFF8F5EE,
                    onClick = { onChange(value.copy(customBackground = 0xFFF8F5EE, customForeground = 0xFF24211D)) },
                    label = { Text("米白") },
                )
                FilterChip(
                    selected = value.customBackground == 0xFFE8F0E8,
                    onClick = { onChange(value.copy(customBackground = 0xFFE8F0E8, customForeground = 0xFF223027)) },
                    label = { Text("青灰") },
                )
                FilterChip(
                    selected = value.customBackground == 0xFF20242A,
                    onClick = { onChange(value.copy(customBackground = 0xFF20242A, customForeground = 0xFFE7E9EC)) },
                    label = { Text("深灰") },
                )
            }
        }

        Text("翻页效果")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PageAnimation.entries.forEach { animation ->
                FilterChip(
                    selected = value.pageAnimation == animation,
                    onClick = { onChange(value.copy(pageAnimation = animation)) },
                    label = { Text(when (animation) { PageAnimation.NONE -> "无"; PageAnimation.SLIDE -> "平移"; PageAnimation.COVER -> "覆盖" }) },
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ToggleSetting("首行缩进", value.firstLineIndent) { onChange(value.copy(firstLineIndent = it)) }
        ToggleSetting("两端对齐", value.justified) { onChange(value.copy(justified = it)) }
        ToggleSetting("保持屏幕常亮", value.keepScreenOn) { onChange(value.copy(keepScreenOn = it)) }
        ToggleSetting("锁定竖屏", value.lockPortrait) { onChange(value.copy(lockPortrait = it)) }
        ToggleSetting("显示阅读进度", value.showStatus) { onChange(value.copy(showStatus = it)) }
        ToggleSetting("音量键翻页", value.volumeKeys) { onChange(value.copy(volumeKeys = it)) }
        ToggleSetting("跟随系统亮度", value.brightness < 0f) {
            onChange(value.copy(brightness = if (it) -1f else .5f))
        }
        if (value.brightness >= 0f) {
            SettingSlider("阅读亮度", "${(value.brightness * 100).roundToInt()}%", value.brightness, .05f..1f) { onChange(value.copy(brightness = it)) }
        }
    }
}

@Composable
private fun SettingSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(valueText, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
    }
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
