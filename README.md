# 轻阅 LightReader

轻阅是一款本地优先的 Android 小说阅读器，使用 Kotlin 与 Jetpack Compose 构建。当前版本为 `0.1.6`，重点覆盖中文 TXT 阅读体验，同时支持无 DRM EPUB、公开 HTTPS 网页小说导入、书内搜索、书签、网页追更和本机加密保存 DeepSeek API Key。

最新 APK 可在 [GitHub Releases](https://github.com/kroblus/Reader/releases) 下载。

## 功能概览

- 本地书架：导入 TXT/EPUB，保存阅读进度、书签、全文搜索索引和阅读设置。
- 中文 TXT 优化：自动识别 UTF-8、UTF-16、GB18030/GBK、Big5 等常见编码，并清洗 BOM、Tab、段首空格和多余空行。
- 阅读排版：基于 Android `TextPaint` 实测字宽分页，支持章节标题页、段距、行距、首行缩进、边距、字重、两端对齐和多套排版预设。
- 阅读操作：点击、滑动、音量键翻页；支持横向、纵向、平移、无动画和仿真翻页；支持自动阅读、极简模式、全屏点击下一页。
- 阅读辅助：目录、倒序/正序切换、书签、书内搜索、右侧进度条、页眉页脚、亮度调节、夜间模式。
- 网页小说：校验公开 HTTPS 目录页，规则解析章节列表，预览正文，创建整本下载任务，支持暂停、恢复、失败重试和网页书籍追更。
- DOM/WebView 辅助验证：提供 DOM 桥接页面，用于检查 WebView 中实际加载后的 HTML 与正文预览。
- DeepSeek 兜底解析：本地规则无法识别网页结构时，可使用用户自己的 DeepSeek API Key 解析裁剪后的目录页 HTML 样本。
- 本地化与皮肤：内置英文和简体中文字符串；书架和功能页支持薄荷、海盐、奶杏、樱花等皮肤。

## 阅读与导入流程

### 本地 TXT/EPUB

```text
TXT/EPUB 文件
  → BookRepository
  → BookTextNormalizer / 格式解析器
  → BookParagraph
  → ReaderLayoutEngine
  → ReaderPage 缓存
  → Compose Canvas 绘制
  → Room 保存阅读进度
```

阅读位置使用章节 ID、章节索引和源字符偏移恢复。字体、字号、行距、段距、缩进、边距或屏幕尺寸变化会触发重新分页；主题、亮度、菜单、页眉页脚、全屏点击和翻页动画只影响显示或交互，不改变正文分页区域。

### 网页小说

网页导入只面向公开 HTTPS 目录页：

1. 粘贴目录页 URL。
2. 本地校验 URL、robots.txt 和访问限制。
3. 使用规则解析目录、标题、作者、简介和章节链接。
4. 抓取前几章正文并生成预览。
5. 用户确认后创建整本下载任务。
6. 下载完成后以本地书籍加入书架，后续可检查新增章节并追更。

如果本地规则失败且已配置 DeepSeek，应用只发送裁剪后的目录页 HTML 样本用于结构识别；不会发送已下载的整本正文，也不会绕过登录、付费墙、验证码、DRM、robots.txt 或站点访问限制。

## 数据与隐私

- 小说正文、数据库、阅读进度和 API Key 都保存在本机。
- DeepSeek API Key 使用 Android Keystore + AES-GCM 加密保存，不写入数据库、日志或备份。
- 应用不提供账号、云同步、跨设备进度同步或付费内容绕过能力。
- `deepseekapi.txt`、`local.properties`、签名文件、APK/AAB、崩溃日志和本地测试文本均被 `.gitignore` 排除。

## 技术栈

- Kotlin
- Jetpack Compose
- Room
- WorkManager
- OkHttp
- Jsoup
- Android Keystore
- JUnit / AndroidX Test / Compose UI Test

当前数据库版本为 Room schema `4`，包含下载任务元数据、章节来源 URL、阅读进度诊断字段和迁移测试。

## 构建与测试

环境要求：

- JDK 17
- Android SDK 36
- Windows PowerShell 或等价 shell

常用命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest
```

`connectedDebugAndroidTest` 需要在线 Android 模拟器或真机。

开发签名 Release APK 输出位置：

```text
app/build/outputs/apk/release/app-release.apk
```

正式分发前应替换为个人持有的发布密钥。发布前建议使用 `apksigner verify --verbose` 校验签名，并使用 `aapt dump badging` 确认 `versionCode` 和 `versionName` 与计划版本一致。

## 手动回归清单

1. 冷启动应用，确认书架空状态、导入入口、网页导入、DeepSeek 设置和 DOM 桥接入口可达。
2. 导入 UTF-8 和 GB18030/GBK 中文 TXT，确认章节切分、正文清洗、搜索索引和阅读进度正常。
3. 导入无 DRM EPUB，确认目录和正文可打开。
4. 进入阅读页，等待工具栏自动隐藏；默认点击模式下左/中/右区域分别执行上一页、菜单、下一页。
5. 开启“全屏点击下一页”，确认左右两侧点击均进入下一页，中间窄区仍唤起菜单，滑动翻页方向不变。
6. 快速连续翻页和跨章翻页时，不应跳章、白屏或乱页；音量键和自动阅读跨章只进入相邻章节。
7. 打开设置面板，调整字体、字号、行距、段距、边距、背景、亮度和翻页动画，确认需要重排的设置才触发重新分页。
8. 添加、跳转、删除书签；执行书内搜索并从搜索结果跳回阅读页。
9. 使用公开 HTTPS 小说目录页解析网页小说，确认前 10 章、正文预览、下载任务、暂停/恢复/失败展示和追更流程。
10. 测试 DeepSeek API Key 保存、连接测试、删除确认和高级设置。
11. 使用 DOM 桥接验证页面加载 HTTPS 页面，确认可读取 DOM HTML 和 body 文本预览。
12. 返回书架再进入同一本书，应恢复到最后阅读位置。

## 当前边界

- 仿真翻页采用稳定的原生透视效果，不模拟真实纸张卷曲物理。
- 自定义外部字体文件、语音朗读、云同步、账户系统和跨设备阅读进度同步暂不在当前范围内。
- 网页导入不支持需要登录、付费、验证码、浏览器安全验证或站点明确禁止抓取的内容。
