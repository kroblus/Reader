# 字体、设置与过渡改进

记录日期：2026-11-14。下文是基于 `207a07f6da1986856905d3e67cdf309964adb58a` 的字体与界面改进验证快照。当时先交付版本号仍为 0.3.5 的手机 QA 体验包；后续输入法修复与 0.3.6 发布记录见 [应用设置输入法修复](settings-ime-fix.md)。本文件中的源码及 APK 散列对应该历史 QA 快照。

## 用户可见变化

- 正文新增霞鹜文楷轻便版和朱雀仿宋，共六个字体选项。预览、标题、正文与分页使用一致字体；后台预载并复用字体对象。来源、许可、体积及覆盖范围见 [内置正文字体](bundled-fonts.md)。
- 界面文字统一使用系统字体。新安装的默认正文字体也跟随系统，已有正文选择保持不变。
- 仍先展示舒适、紧凑、沉浸三个排版预设。“行距与留白”改为简短的圆角选项：紧凑/适中/宽松、首行空格、标准/加粗、自然参差/两边整齐；字号等滑杆改用细圆轨道。已有自定义值在用户选择新档位前不被量化改写。
- 设置选择有 180ms 色彩过渡，展开、工具栏和页面切换采用约 200ms 的淡入淡出及小幅位移。遵循系统动画倍速，包括关闭动画。

## 收起排版时的跳动原因及处理

原设置面板与底部工具分别以不同时间、不同距离退出；工具栏先卸载导致剩余面板重新定位、连接处圆角中途变化。同时系统栏立刻隐藏，改变退出中的可见 inset。页面切换时，旧阅读页与新页面也会竞争系统栏恢复逻辑。

现在设置面板和底部工具共用外层退出动画，整组退出前保留面板状态；内部单独开合才使用展开/收缩。保留状态直接读取同一份阅读状态，避免父子流收集时序导致提前卸载。工具位置使用稳定 inset，系统栏由应用根节点统一管理，并在退出完成后隐藏。亮度更新与屏幕方向、常亮设置分别处理，避免拖动时反复恢复窗口属性。

## 验证

| 范围 | 结果与证据 |
| --- | --- |
| JVM 单元测试 | core-reader 18 + app 72，共 90 项通过；`build/reader-font-motion-build.log` |
| 构建与 Lint | QA 应用、QA 测试 APK 构建成功；Lint 0 errors，保留 63 warnings、1 hint；最终日志 `build/reader-font-motion-final-build.log` |
| 真机完整相关回归 | 21 项中 20 项首次通过，覆盖字体、设置草稿、布局度量、生命周期、阅读交互、图库及收起动画。唯一失败为系统栏测试使用联合 mask 的可见性断言；报告 `build/reports/reader-user-tests/20261113T184135Z-2ded3abc-207a07f6da19-7b4f8992e69b` |
| 系统栏断言修正后的模拟器 | 大字体 2.0 横屏、横屏截图、两项过渡测试，共 4 项通过；`build/reports/reader-user-tests/20261113T184826Z-16926315-207a07f6da19-4e7e1ad88410` |
| 最终真机正常动画 | MEIZU 20 Pro / Android 16，系统动画 1.0；两项过渡测试和截图图库共 3 项通过；`build/reports/reader-font-motion/font-motion-20261113T185600Z` |
| 最终模拟器关闭动画 | Android 16，两项过渡测试通过；`build/reports/reader-user-tests/20261113T185618Z-715f92f7-207a07f6da19-76743d7c1427` |

过渡测试使用真实 Choreographer / Window.FrameMetrics。连续十轮点击正文收起排版，核对阅读字符锚点、布局版本和根窗口高度不变；搜索、设置和书架往返按各系统栏的真实基线分别核对。测试期间不使用 Compose 人工时钟。直接 instrumentation 的默认输出服务可能静默丢弃证据，已补充 QA 专用、SHA-256 校验的公共目录副本；最终两份 JSON 已实际拉取并检查。

## 动画实测范围与限制

最终真机正常动画运行采集 486 帧，callback dropped 为 0。全采样窗口中位数 7.218ms、P95 21.153ms、最大 38.821ms；32 帧超过 16.67ms。以测试观察到逻辑收起的时刻为零，中间 40–160ms 的 149 帧中位数 7.569ms、P95 8.094ms，未出现超过 16.67ms 的帧。

收尾 160–240ms 仍有 20 帧超过 16.67ms，峰值与动画完成、控件卸载及隐藏系统栏相邻；现有数据不能精确区分其贡献。240ms 后的 190 帧全部低于 8.33ms。可确认中段连续稳定和已知布局跳动机制被修正，不能声称全程 120fps 或零慢帧。本次为单设备 debug QA 包，未做同条件旧版对照，也不把关闭动画与正常动画两批不同分母的 P95 当作性能提升比例。

原始证据：`build/reports/reader-font-motion/font-motion-20261113T185600Z/performance/reader-chrome-close-frames.json`、`reader-chrome-system-bars.json`。

## 交付与设备状态

- 最终应用源码标识：`207a07f6da19-76743d7c1427`，完整源码树 SHA-256：`76743d7c1427014f70f96366b5ec65ea170ef2ad9d169925884353ef9f74a61d`。清单位于 `build/reports/reader-font-motion/final-source-files.sha256`。
- APK：`app/build/outputs/apk/qa/app-qa.apk`，SHA-256：`45507801e4e781d3d9cc1945d7cce045780e818955c636fd737a32dba4afe7a9`。
- 手机上安装独立包 `com.lightreader.app.qa`，保留示例书《雾桥长路》，选择霞鹜文楷和护眼绿供直接体验。正式包 `com.lightreader.app` 仍为 versionCode 19 / 0.3.5，未清理其数据。
- 手机三项系统动画倍速均恢复为 1.0，模拟器系统字号恢复为 1.0。
- 人工查看的最终截图：`build/reports/reader-font-motion/phone-fonts.png`、`phone-spacing.png`、`phone-wenkai-reading.png`。
