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

默认值位于 `ReaderPreferences`：17sp 字号、1.75 倍行高、10dp 段距、2em 首行缩进、28dp 左右边距，以及 64dp/56dp 顶底预留。内置护眼绿、米黄色、浅灰白、暖棕色、夜间和自定义主题。

设置面板中的字体、字号、行距、段距、缩进和边距会触发重新分页；背景与文字配色、页眉页脚开关、亮度、菜单和翻页动画不会触发重新分页。

## 其他功能

- 书架、目录、阅读进度、全文搜索、书签和删除管理
- 点击、左右滑动及音量键翻页
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

## 当前边界

纵向连续滚动、仿真卷页、多字体包和自动阅读仍是安全禁用的后续入口。本版本优先保证中文排版、稳定分页、位置恢复、沉浸式系统栏和无跳动覆盖菜单。

API Key、数据库和小说正文不参与系统备份。应用不绕过登录、付费墙、验证码、DRM、robots.txt 或站点限制。
