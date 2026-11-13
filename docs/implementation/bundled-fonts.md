# 内置正文字体

核对日期：2026-11-14。字体只影响阅读正文、章节标题和正文预览；界面文字使用系统字体。`SYSTEM` 正文对应 Android `Typeface.DEFAULT`。

## 官方资源与完整性

| 选项 | 字体与版本 | 官方发布 | 包内文件 | 字节数 |
| --- | --- | --- | --- | ---: |
| `LXGW_WENKAI` | 霞鹜文楷轻便版 Regular 1.530 | [官方 v1.530](https://github.com/lxgw/LxgwWenKai-Lite/releases/tag/v1.530) | `fonts/lxgw_wenkai_lite_regular.ttf` | 13,949,472 |
| `ZHUQUE_FANGSONG` | 朱雀仿宋 Regular 0.212 | [官方 v0.212](https://github.com/TrionesType/zhuque/releases/tag/v0.212) | `fonts/zhuque_fangsong_regular.ttf` | 8,824,084 |

字体位于 `core-reader/src/main/assets`。二进制保持官方原样，仅包内文件名采用应用资源命名；没有裁剪字库或修改字形。朱雀上游将本版本标记为 technical preview，本应用没有把它标注成正式字体版本。

SHA-256：

- `lxgw_wenkai_lite_regular.ttf`：`f799a538a3355877e597a0ae04050e897932c9ae7689eb9227c2953c5dd549ea`。与 GitHub 官方 release asset 的 `digest` 一致。
- 上游 `ZhuqueFangsong-v0.212.zip`：`bb8b661a7643d2296a72d9d10530a00949419c4e527fb61783f73c2ba1a8c062`。与官方 release asset 的 `digest` 一致。
- 从该 ZIP 取出的 `zhuque_fangsong_regular.ttf`：`558c62730844fe54ba220146ed62f859d4e2880188d92d985f8921c6e3743bc4`。

两种 Regular 文件合计 22,773,556 字节（21.72 MiB）；普通 ZIP deflate 静态估算为 14,236,167 字节（13.58 MiB），最终 APK 增量以正式构建为准。只包含一个真实字重；保留现有阅读器规则：数值 500 及以上由 Android 从该 Regular 字体产生粗体，分页与绘制使用同一粗体对象。

## 许可证与归属

两款字体均采用 SIL Open Font License 1.1。完整原版版权声明和许可证放在以下 assets 中，随 APK 一同提供、无需网络：

- `fonts/licenses/LXGW-WenKai-Lite-OFL.txt`：[v1.530 官方全文](https://raw.githubusercontent.com/lxgw/LxgwWenKai-Lite/v1.530/OFL.txt)。版权为 LXGW 2021–2026、Klee Project Authors 2020；保留官方 Reserved Font Names 和附加许可原文。
- `fonts/licenses/Zhuque-Fangsong-OFL.txt`：[v0.212 官方全文](https://raw.githubusercontent.com/TrionesType/zhuque/v0.212/LICENSE.txt)。保留上游 Zhejiang JadeFoci Technology 的版权声明及完整 OFL。

[霞鹜文楷轻便版官方说明](https://github.com/lxgw/LxgwWenKai-Lite)将该版本定位为适合嵌入应用的字库；[朱雀官方说明](https://github.com/TrionesType/zhuque)说明其西文与符号采用 Juan Pablo del Peral / Huerta Tipográfica 的 Alegreya。字库未覆盖的罕用字、emoji 等由 Android 的正常字体回退处理，不宣称覆盖全部 Unicode。

## 加载、度量与兼容

- `ReaderApplication.onCreate` 调用 `ReaderTypefaces.preload`。它只安排工作；字体 worker 在执行任务前调用 `Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)`，两个 TTF 由该后台低优先级单线程读取；同一家族只创建一次加载任务，Regular/Bold 对象长期复用。
- `ReaderTypefaces.awaitTypeface` 为异步预览 API。取消一个预览不会取消其他调用共享的字体加载。
- `ReaderTypefaces.cachedTypeface` 不做 I/O、不等待。正文 Canvas 只读已加载缓存；分页在原计算线程等待字体完成后才发布页，避免先用系统字体分页、随后用内置字体绘制。
- `rememberReaderFontFamily` 为 Compose 正文预览 API；未加载完成时预览暂用系统字体，完成后更新。界面标签不使用这些内置字体。
- 新安装没有保存选择时默认 `SYSTEM`；既有 `SYSTEM/SANS/SERIF/MONOSPACE` 持久化名称不变，也不迁移已有选择。新增两个 enum 值可保存进偏好、备份及自定义排版快照。现有 `ReaderLayoutSnapshot.fontFamily` 明确保存字体选择，`layoutSnapshot()` 和 `applyLayout()` 双向复制该字段；选择字体不应改变快照中其余字号、行距、缩进和边距值。
- 已有 px 字体度量快照与字符锚点保持不变。排版和 Canvas 都从 `ReaderTypefaces` 获取相同 family/weight 的对象。

## 验证证据

静态字体解析使用 fontTools 读取官方 TTF，而非依赖文件名：

| 字体 | 内部 PostScript 名称 | Unicode 映射数 | U+4E00–U+9FFF 映射数 |
| --- | --- | ---: | ---: |
| 文楷轻便版 | `LXGWWenKaiLite-Regular` | 25,694 | 18,430 |
| 朱雀仿宋 | `ZhuqueFangsong-Regular` | 12,882 | 11,009 |

两者均覆盖“轻阅书香山川文字阅读春风仿宋楷体繁體中文”。官方 SHA-256、资源字节数和内部版本均已静态核对。

新增 `ReaderBundledFontsInstrumentedTest` 三项：

1. 对系统、文楷、朱雀的真实中文 native raster 像素作差异断言，并确认缓存对象复用；防止只有选项名称不同、中文实际仍回退同一字体。
2. 两字体 × 普通/粗体 × 系统字号 1.0/2.0 × 竖屏/横屏共 16 种组合，核对标题/正文实际 Paint 测量宽度、页内字形边界、全文无漏字及 UTF-16 偏移。
3. 六个新旧字体选项依次保存并从新 Repository 实例读回，最后恢复测试前设置。

上述三项测试已在 MEIZU 20 Pro / Android 16 上全部通过，报告位于 `build/reports/reader-user-tests/20261113T184135Z-2ded3abc-207a07f6da19-7b4f8992e69b`。该批次共 21 项，其中另一个系统栏测试的联合可见性断言曾失败；修正为分别核对状态栏、导航栏和窗口基线后，后续真机与模拟器复验通过，详情见 [字体、设置与过渡改进](reader-font-motion.md)。

同时验证了六个字体选项与自定义排版快照、字号 2.0 横屏中的选择项可见性和触控尺寸。实际 APK 内的两个 TTF 及两份许可证与源码 assets 逐字节一致。最终手机截图见 `build/reports/reader-font-motion/phone-fonts.png` 和 `phone-wenkai-reading.png`。
