# 轻阅 LightReader

轻阅是一款使用 Kotlin 与 Jetpack Compose 构建的本地优先 Android 小说阅读器。当前阅读引擎重点优化中文 TXT 网文，同时保留无 DRM EPUB 和网页导入能力。

## 中文阅读引擎

阅读数据流：

`TXT/EPUB 文件 → BookRepository → BookTextNormalizer → BookParagraph → ReaderLayoutEngine → ReaderPage 缓存 → Compose Canvas → Room 阅读进度`

- TXT 导入会识别 UTF-8、UTF-16、GB18030/GBK、Big5 等常见编码，并以流式方式清洗、识别章节和切分超长章节。
- `BookTextNormalizer` 清除 BOM、Tab、全角/半角段首空格和多余空行；首行缩进由布局引擎以 2em 坐标偏移完成。
- `PaintReaderLayoutEngine` 使用 Android `TextPaint` 实测字符宽度，按段落、行高、段距和固定正文区域分页，并做基础中文标点避头尾。
- 阅读页使用 Compose Canvas 绘制正文、章节标题、总进度、章节页码、时间和右侧进度条；菜单和设置面板仅覆盖正文，不改变分页区域。
- 分页在后台线程执行，并缓存当前、上一和下一章节。主题、亮度和菜单变化只重绘；字体、字号、字重、行距、段距、缩进、边距或屏幕尺寸变化才重新分页。
- Room 使用章节 ID 和源字符偏移恢复阅读位置，因此修改样式后仍可回到接近原位置。

## 默认阅读样式

默认值位于 `ReaderPreferences`：17sp 字号、1.75 倍行高、10dp 段距、2em 首行缩进、20dp 左右边距，以及 52dp/42dp 顶底预留。分页引擎还会为页眉页脚保留最小安全区，避免正文被系统栏或阅读状态遮挡；每章第一页会将章名作为正文标题块参与分页。内置护眼绿、米黄色、浅灰白、暖棕色、夜间和自定义主题。

设置面板中的字体、字号、行距、段距、缩进和边距会触发重新分页；背景与文字配色、页眉页脚开关、亮度、菜单和翻页动画不会触发重新分页。

## 其他功能

- 书架、目录、阅读进度、全文搜索、书签和删除管理
- 点击、左右滑动及音量键翻页
- 横向、纵向、平移、无动画和带透视阴影的仿真翻页
- 可调速度的自动阅读、极简模式，以及系统/黑体/宋体/等宽字体
- HTTPS 小说目录解析、下载暂停/恢复和失败重试
- Android Keystore + AES-GCM 加密保存用户自己的 DeepSeek API Key

## 构建与测试

环境要求：JDK 17、Android SDK 36。

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest
```

最后一项需要在线模拟器或 Android 设备。可安装的开发签名 Release 位于 `app/build/outputs/apk/release/app-release.apk`；正式分发前应替换为个人持有的发布密钥。

发布前还需使用 `aapt dump badging` 校验 Debug/Release APK 的 `versionCode` 和 `versionName` 与本次版本一致。

## 阅读页手动回归清单

1. 冷启动应用，进入测试书，等待工具栏自动隐藏。
2. 章内点击右侧 1 次应前进 1 页，快速连点右侧 2 次应最终前进 2 页。
3. 章内快速连点左侧 2 次应最终后退 2 页，左右滑动翻页应保持自然平移。
4. 在第 1 章最后一页点击右侧进入第 2 章，不得闪到第 3 章；滑动跨章结果应一致。
5. 在第 2 章第一页点击或滑动返回上一章末页，不得白屏或乱页。
6. 音量键、自动阅读跨章只进入相邻章节；自动阅读在设置、目录、书签、更多设置打开时暂停，关闭后继续，书末自动停止。
7. 设置面板应贴在底部控制条上方，面板内部最后一行后无额外空洞；Android 16 真机需要重点复测。
8. 返回书架再进入同一本书，应恢复到最后阅读位置。

## 当前边界

当前仿真模式采用稳定的原生透视翻页效果，不模拟纸张卷曲物理。自定义外部字体文件、语音朗读和跨设备进度同步仍不在本地核心阅读器范围内。

API Key、数据库和小说正文不参与系统备份。应用不绕过登录、付费墙、验证码、DRM、robots.txt 或站点限制。
