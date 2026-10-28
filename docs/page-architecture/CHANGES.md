# 页面与架构优化

日期：2026-10-28。版本：0.3.4（versionCode 18）。基于已有工作区 UI 改动继续完善。

## 页面行为

- 书架支持书名、作者与多词查询；可按最近活动或书名排序。筛选无结果时可一键清空。
- “继续阅读”从真实阅读记录选择，不会被更新导入的未读书替代。
- 返回书架保留筛选、排序与滚动状态；网格列宽随字体缩放。
- 书内搜索编辑关键词时立即清理旧结果；失败与无匹配分开显示，支持重新搜索。
- 全局耗时操作使用模态进度层，取消按钮只对应当前展示的导入任务。

## 职责边界

| 入口 | 责任 |
|---|---|
| `LibraryShelfProjection` | 纯筛选、排序、最近阅读选择，可单独单测 |
| `BookSearchViewModel` | 仅保留当前书籍的搜索会话、取消、请求版本校验与失败状态；Activity 重建保留请求 |
| `BookSearchGateway` | 搜索依赖契约，由 `AppContainer` 接入 Repository 和性能记录 |
| `SearchRoute` / `SearchScreen` | 状态订阅 / 纯状态和回调驱动的 UI；由 app 导航决定搜索会话进入与退出 |
| `WebImportScreen` / `ApiSettingsScreen` | 独立文件维护各自流程，替代聚合的 `AuxiliaryScreens.kt` |
| `ReaderApp` | 页面切换、状态保存、生命周期感知事件与全局提示 |
| `ReaderSessionController` | 分页/预取任务与进度写入分别管理；保存串行，恢复等待最终保存 |

页面的 StateFlow 订阅随生命周期暂停；书架、下载任务与阅读详情状态只在对应页面订阅。自动翻页和工具栏计时只在 `RESUMED` 时运行，时钟只在 `STARTED` 时运行。停止阅读界面时立即提交可见位置。

退出阅读取消分页和预取，但允许最终进度保存完成。重新打开书籍先等待保存，再读取恢复位置，避免“刚翻页就退出”丢失进度。新的打开请求取消旧的加载任务。

Room schema、书籍格式和持久化偏好键保持现有约定。

## 验证

测试使用隔离包 `com.lightreader.app.qa`，设备为 `ShengliFlowApi36 / emulator-5554 / API 36`。

以下为 0.3.3 主页面优化的验收记录；0.3.4 的追加修复和验证见下方。

| 检查 | 结果 |
|---|---|
| `:app:testQaUnitTest` | 56 项通过 |
| `:feature-reader:testDebugUnitTest` | 10 项通过 |
| `:app:lintQa` | 0 errors，49 warnings，1 hint；主要为依赖版本与既有资源建议 |
| QA APK 与仪器测试 APK | 构建通过，版本 0.3.3-qa / 17 |
| 默认尺寸设备回归 | 29 个不同用例通过，含定向复测 |
| 360dp / 1.3× 字体 | 书架完整查找与导航流程通过，截图确认进度与继续阅读自动换行 |
| QA 安装与冷启动 | 通过；进程存活、无崩溃；AVD TotalTime 4795 ms（非性能基准） |
| 英文 / 简体中文资源 | 各 367 个键，缺失与重复均为 0 |

设备回归包括书架筛选/排序/返回保留、后台暂停自动阅读、立即退出并重新打开、书内搜索、Activity 重建、设置、书签、目录、阅读旅程、受控 HTTPS 网页导入，以及完整页面截图流程。首轮搜索用例暴露了条件分支跳过 Compose State 读取的问题；修复为无条件订阅状态后，新旧搜索与截图流程 4/4 复测通过，其余 25 个用例已通过。

本机首次并行验证触发 Windows 提交内存不足，最终采用单 worker、1 GB Gradle heap、256 MB 单测 heap 和 Kotlin in-process 完成验证。该限制仅通过本次命令与 `build/` 下的临时 init script 设置。

## 0.3.4 追加修复

0.3.3 的 GitHub 完整回归完成 71 个用例：67 通过、3 失败、1 跳过。失败调查发现：

- 标点避头规则在安全字符边界之后按 UTF-16 单位回退，可能再次拆开 Emoji。现在对正文和标题的最终换行位置重新校验字符簇边界。新增 96–720 px 宽度扫描，在旧实现上稳定失败于标题 96 px / UTF-16 offset 9，可覆盖字体度量差异。
- 两项系统边界测试受到 Quickstep 无响应对话框和 Android 首次全屏提示遮挡。截图与日志确认音量键进入系统音量控制。测试现在只关闭明确识别的这两类系统窗口，等待应用恢复前台并获得焦点，再断言返回和逐次翻页；不会忽略 Reader 自身的无响应窗口。

系统选择器导入用例仍保留既有环境条件：DocumentsUI 未显示准备好的文件时跳过；没有删除断言或跳过失败用例来通过检查。

本地追加验证：应用、阅读会话和阅读核心共 80 项单测通过；20 项核心仪器测试通过，3 项系统边界测试在修正启动 Intent 后全部复测通过，合计 23 个不同用例、无跳过。Home 恢复测试使用 launcher Intent 复用已有 Activity，并检查原书籍和页码仍在；避免测试创建第二个 Activity 却等待旧实例恢复。

应用 Lint 为 0 errors / 49 warnings，阅读核心 Lint 为 0 errors / 0 warnings。0.3.4-qa（versionCode 18）安装、冷启动和进程崩溃日志检查通过；AVD TotalTime 3832 ms，仅为启动烟测记录。

## 运行截图

以下截图来自本次模拟器测试，使用固定测试书籍。

| 页面 | 截图 |
|---|---|
| 书架与真实最近阅读 | [bookshelf.png](screenshots/bookshelf.png) |
| 按作者筛选 | [bookshelf-filtered.png](screenshots/bookshelf-filtered.png) |
| 小屏大字体 | [bookshelf-large-font.png](screenshots/bookshelf-large-font.png) |
| 阅读控制 | [reader.png](screenshots/reader.png) |
| 快速排版设置 | [reader-settings.png](screenshots/reader-settings.png) |
