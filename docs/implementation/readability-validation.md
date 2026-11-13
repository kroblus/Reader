# 阅读与书架可读性验证

日期：2026-11-13。颜色表来自源码颜色与 alpha 合成审计；文末单独记录实际设备布局与截图证据，二者不互相替代。

## 方法与范围

采用 WCAG sRGB 相对亮度：每通道 `c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055)^2.4`，亮度权重 `0.2126/0.7152/0.0722`，对比度 `(L高+0.05)/(L低+0.05)`。先以 `alpha * 前景 + (1-alpha) * 背景` 合成实际颜色，再计算亮度。普通与辅助文字统一使用 **4.5:1**，不依赖大字豁免。禁用态、分隔线、封面装饰、进度轨道不作为文字计入。

书架覆盖背景、装饰最浓区域（soft 0.90 与 accent 0.11 叠加）、surface、surfaceVariant、primary 0.18 选中面、书架卡片 surface 0.58、最近阅读卡片 surface 0.72，以及小封面四种渐变端点。封面底层使用 Material3 Card 的 surfaceContainerHighest；secondary/tertiary 半透明容器在该层上合成。表内文字最低值取所有实际候选面的最差值，强调色数值只在其实际使用的书架/最近阅读卡片上计算。

阅读工具面为不透明 overlay；外部黑色 scrim 0.20 不叠加在工具面文字下。预设、chips、pill 的 selected/unselected 面分别计算 secondary 的 0.06、0.07、0.10、0.20、0.22、0.24、0.28 合成。

## 4 套应用皮肤

| 皮肤 | 正文最低 | 辅助/三级文字最低 | 卡片强调数值最低 | 按钮白字 | 旧最近日期 alpha 0.68 |
|---|---:|---:|---:|---:|---:|
| MINT | 9.92:1 | 4.64:1 | 5.36:1 | 5.83:1 | 2.95:1 |
| OCEAN | 9.69:1 | 4.52:1 | 5.23:1 | 5.73:1 | 2.91:1 |
| APRICOT | 9.51:1 | 5.03:1 | 5.35:1 | 5.89:1 | 3.10:1 |
| SAKURA | 9.50:1 | 4.89:1 | 5.41:1 | 5.97:1 | 3.10:1 |

三级文字改为不透明 mutedInk；书架章节、日期、封面作者与章节统计去除额外文字 alpha。强调色略微加深以使 Ocean 卡片进度从不足 4.5 达标，其他皮肤保留同色系。背景、装饰和禁用态的透明度保留。

## 阅读主题

| 主题 | 正文/纸面 | 辅助文字/纸面和工具面最低 | 所有选中面标签最低 | 旧目录 placeholder alpha 0.78 | 旧说明 alpha 0.72 的最差情况 |
|---|---:|---:|---:|---:|---:|
| EYE_CARE | 11.95:1 | 5.80:1 | 8.01:1 | 4.02:1 | 4.24:1 |
| SEPIA | 12.05:1 | 5.43:1 | 7.42:1 | 3.46:1 | 4.00:1 |
| LIGHT_GRAY | 12.02:1 | 5.72:1 | 7.42:1 | 3.59:1 | 4.00:1 |
| WARM_BROWN | 8.36:1 | 4.75:1 | 4.79:1 | 3.22:1 | 3.06:1 |
| FROST_BLUE | 12.40:1 | 5.55:1 | 7.57:1 | 3.51:1 | 4.04:1 |
| SAKURA_PINK | 12.15:1 | 5.46:1 | 7.24:1 | 3.49:1 | 3.96:1 |
| NIGHT | 9.83:1 | 5.00:1 | 5.48:1 | 3.61:1 | 3.67:1 |

目录 placeholder、书签正文、阅读进度、工具标签与设置说明不再额外减淡文字。预设卡片、pill 与 chips 按合成后的真实表面计算标签颜色。对于 CUSTOM 用户颜色，正文/辅助颜色先由 core-reader 调整到纸面 4.5:1；若某选中面合成后再次不足，`readableTextColor` 保留合格原色，否则在黑/白中选对比度较高者。对任意不透明 sRGB 表面，黑/白至少一者达到 4.5:1。因此不把默认自定义颜色的单次通过推广成任意输入都无需校正。

上述最低值均达到4.5。读屏动作中 A+/A- 已具有中文/英文明确名称，目录选中态保留状态描述，暂停提供文本、图标描述及点击语义。

## 实际布局验收及边界

新增 `ReaderLargeFontLandscapeInstrumentedTest.twoTimesFontLandscapeToolsAndPauseRemainReachable`：隔离 QA 包，实际系统字号 2.0 与短横屏，进入夜间阅读，滚动并点击更多设置，返回后检查原字符锚点，启动自动阅读并验证暂停可见、具名、可点击。用例保存并在 finally 恢复 font_scale、accelerometer_rotation、user_rotation；不修改安装授权或安全设置。

截图位于 `/sdcard/Download/LightReader-QA-Evidence/<runId>/screenshots/025_reader_font2_landscape_tools.png` 与 `026_reader_font2_landscape_auto_pause.png`。语义证据在同次运行的 `accessibility/reader-font2-landscape.txt`。QA4实际发现暂停按钮只有46.476dp高，已改为最少48dp；QA5、完整QA1和QA6均实际通过宽/高至少48dp及点击断言。

主代理已目视检查完整QA1 `20261113T065411Z-25a0bb63-700916f41a46-8d13fcd762d1/oem-evidence-fallback/screenshots/025_reader_font2_landscape_tools.png`：Contents修复固定76dp裁切后完整可读，短横屏工具为不透明夜间面且可以滚动；026暂停按钮可见。备份UI同run的`backup-ui/duplicate-preview.png`也已目视检查：重复数、跳过说明、设置选择和禁用恢复按钮均清楚，没有覆盖原进度的暗示。

夜间搜索补全Material3容器表面及阅读系统栏颜色，新增027截图。QA8失败语义树确认返回搜索浮层漏算状态栏遮住搜索按钮，已把浮层位置改成状态栏insets加48dp顶栏和8dp间距；QA9 `20261113T072616Z-2706f567-700916f41a46-7f8f46acba03` 的gallery及两项搜索导航3/3通过。主代理已目视检查该run的`027_reader_night_search.png`：应用背景、输入框、结果卡片及状态栏为一致夜间配色，未检索完与继续检索清楚可见；系统输入法使用设备自己的外观设置。

已有 `ReaderFontMetricsInstrumentedTest` 在当前 AVD 通过 1.0/1.3/1.5/2.0 × 竖/横/宽的12组字体换算和正文完整性校验；它不是实际 UI 布局截图验证。TalkBack 的点击语义可自动检查，但真人焦点巡览及语音输出尚未执行，不声称已通过 TalkBack 实机体验。
