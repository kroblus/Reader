# 轻阅 LightReader

轻阅是一款本地优先的 Android 小说阅读器，使用 Kotlin 与 Jetpack Compose 构建。

## 已实现功能

- 自动识别 UTF-8、UTF-16、GB18030/GBK、Big5、日文及常见西文编码的 TXT，并支持无 DRM EPUB 2/3
- 流式 TXT 分章与超长章节切片，不将整本小说载入内存
- 书架、目录、阅读进度、全文搜索、书签和删除管理
- 分页阅读、点击/滑动/音量键翻页以及排版、主题、亮度、方向和常亮设置
- HTTPS 小说目录解析、预览、限速下载、暂停、恢复与失败重试
- 本地规则优先，DeepSeek 仅在网页结构识别失败时生成结构化 CSS 解析规则
- Android Keystore + AES-GCM 加密保存用户自己的 DeepSeek API Key

## 构建

环境要求：JDK 17、Android SDK 36。

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleRelease
```

可安装的开发签名 Release 位于 `app/build/outputs/apk/release/app-release.apk`。正式分发前应换成个人持有的发布密钥。

## 架构

- `core/model`：稳定领域模型和扩展契约
- `core/formats`：`BookFormatPlugin`、TXT 与 EPUB 导入器
- `core/data`：Room、FTS、书库和持久下载任务
- `core/reader`：基于 `StaticLayout` 的分页引擎
- `core/web`：Jsoup 解析、DeepSeek 适配器和 WorkManager 下载器
- `core/security`、`core/settings`：密钥加密和本地设置
- `ui`：Compose 书架、阅读器、搜索、网页导入和设置

## 安全与边界

- API Key、数据库和小说正文不参与系统备份。
- 不记录 API Key 或正文，不绕过登录、付费墙、验证码、DRM、robots.txt 或站点限制。
- 首版仅支持 HTTPS 静态网页；依赖 JavaScript 渲染的站点不会绕过其限制。
- 网页保存功能仅用于用户有权访问和保存的内容。
