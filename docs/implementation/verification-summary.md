# 完整改进验证汇总

本文件封存上传前的实施验收证据。用户随后授权上传 GitHub，版本递增为0.3.5/19；发布说明及原有性能限制见[0.3.5版本说明](../releases/v0.3.5.md)。下面的0.3.4/18与“未发布”均指该实施验收阶段。

日期：2026-11-13。实际起点 `700916f41a46af9a00399749b06f0e59769dad33`，工作分支 `codex/reader-complete-improvements`。版本仍为0.3.4/18；未发布。

本文件仅记录本轮实际证据。12个工作包的实现均已落地，最终整套QA为113/113通过，115项JVM测试及2项宏基准通过。**UI性能15%比较门槛仍未通过，不能宣称全部验收通过**；工作包11的性能放行需要受控参考环境继续定位。详细失败与修复过程保留在[实施检查点](reader-improvement-progress.md)。

## 环境与隔离

- Windows PowerShell，Adoptium JDK21，Android SDK36。
- `emulator-5554` / `ShengliFlowApi36`，Android16 API36，x86_64，1080×2400，420dpi，60Hz，正常测试fontScale1.0；专用用例临时切到2.0及横屏并恢复。
- QA为`com.lightreader.app.qa`，测试为`.qa.test`；独立性能包为`.benchmark`，宏基准driver为`.macrobenchmark`。
- 未修改安装授权、安全设置；动画/字号/旋转测试保留原值并恢复。正式包未安装，也未执行其清数据、卸载或force-stop。
- 没有读取真实Key或签名私钥，没有调用真实付费AI，没有push/tag/Release/分发。

## 功能与对应证据

| 包 | 关键验收 | 实际证据 |
|---|---|---|
| 01 | 请求代次、正文缓存缺失、重排/重开/新进程 | ReaderSessionControllerTest；ReaderLifecycle 6项；新进程两阶段2/2 |
| 02 | A慢B快、输入失效、重复确认、创建幂等 | DownloadViewModelTest、DownloadPersistence仪器测试 |
| 03 | URL身份、同名不同源、任务暂存到正式追加、原锚点保留 | WebChapterIdentityTest、DownloadPersistence8项、加强后的ReaderCore追更用例 |
| 04 | 执行代次/owner、通知暂停、取消删除、正式跨批 | Worker6项，含200章25个正式8章批次，94.513s（保留生产限速） |
| 05 | robots/HTTPS/重定向/HTTP错误/取消/AI配置 | HtmlFetcherTest、RobotsPolicyTest、受控QaHttps4项及WebUserJourney |
| 06 | 自定义快照、字段合并、草稿提交、兼容旧偏好 | ReaderPreferenceEditsTest、ReaderSettingsDraft2项、ReaderCore |
| 07 | 平台非线性字体换算与页面快照、短横屏大字号 | ReaderFontMetrics12组；实际font2工具/自动暂停/48dp点击 |
| 08 | 真实颜色合成、夜间表面、书签撤销、操作不串层 | ReaderUi、gallery、[可读性验证](readability-validation.md)及目视025/026/027 |
| 09 | 99/100/101/237命中、跨块中英、游标与两个返回动作 | SearchBackup、SearchPagingViewModel、SearchLifecycle2项 |
| 10 | 归档校验/事务/重复/取消、系统选择器、全新安装 | SearchBackup7项、BackupUi、最终QA内真实全新安装两阶段2/2 |
| 11 | schema8完整打开到9、旧引用、完整导入、性能 | 迁移/语料/113项整套QA通过；引擎4/4比较通过；UI配对门槛失败，性能放行未完成 |
| 12 | 报告隔离、失败计数、安全预检、精确SHA门禁 | comparator16正/负例通过；workflow YAML/契约/shell检查通过；远端未触发 |

## 实际命令与报告

基础检查：

```powershell
.\gradlew.bat :core-reader:testDebugUnitTest :core-formats:testDebugUnitTest :feature-reader:testDebugUnitTest :feature-download:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleQa :app:assembleQaAndroidTest :app:assembleBenchmark :macrobenchmark:assemble --console=plain '-Pkotlin.compiler.execution.strategy=in-process'
.\scripts\test-performance-comparison.ps1
git diff --check
```

最终static5：`build/reader-final-static5.log`，构建成功3m13s；115单元通过、0失败/0跳过（core-reader18、core-formats8、feature-reader12、feature-download5、app72）；app lint0错误/63警告/1提示；最新QA、QA AndroidTest、benchmark app和macrobenchmark全部构建成功。

完整设备命令：

```powershell
.\scripts\run-reader-user-tests.ps1 -Serial emulator-5554 -IncludeLargeCorpus
```

- 完整QA1：`build/reports/reader-user-tests/20261113T065411Z-25a0bb63-700916f41a46-8d13fcd762d1`，108项中107通过，1项旧暂存顺序断言失败；已改为验证暂存0/1及正式提交后2/3、原正文/进度、重复提交，QA6通过。
- QA7：生命周期6/搜索2/UI测量1通过，gallery失败。QA8的失败状态与语义树证明返回搜索浮层遮挡搜索按钮，修复真实位置后QA9三项通过。
- QA9：`build/reports/reader-user-tests/20261113T072616Z-2706f567-700916f41a46-7f8f46acba03`；包含已目视的027夜间搜索图。
- QA11：`build/reports/reader-user-tests/20261113T073649Z-763d7af2-700916f41a46-7cfcc6793c20`；指定语言后返回书架、打开真实DocumentsUI并取消回书架1/1通过。
- **最终完整QA2**：`build/reports/reader-user-tests/20261113T081344Z-9719544c-700916f41a46-5fdd0121246e`；109常规用例+2新进程阶段+2全新安装阶段，**113项、0失败、0错误、0跳过**。常规connected构建17m2s，宿主阶段完成时间2026-11-13T08:31:33Z。QA安装/冷启动成功，主代理已目视`qa-home.png`，没有崩溃或遮挡启动页。
- 本次全新安装子报告为`backup-fresh-install/20261113T083108Z_96a2f5f77d`：确实卸载QA/QA.test并检查为空，同APK重装，三种格式全部打开、重复恢复0新增、AI未配置。归档SHA256为`24a88b8ed4091fad540e9ed76a45c4c9a158ef27c8c3168a0153436eb49f77ec`；正式包前后均不存在且未触碰。
- 最终测试身份为基线HEAD `700916f41a46af9a00399749b06f0e59769dad33` 加当时未提交源码，`sourceTreeSha256=5fdd0121246e6919f53142bae029ad47536d67d38b7a2eddb1a1df25579b30b2`；不能把基线HEAD说成包含修改。178项源码清单已逐文件与最终工作区核验一致，其中175项属于最终提交；另3项为用户原有未跟踪的文档生成脚本（docs/ui-optimization下2项、tmp/pdfs下1项），保留但不纳入提交。最终提交后的对应核验见本run的`source-verification.json`。
- static5之后只改测试采集与诊断harness，两份测试APK已重建并真实执行；本轮生产代码与static5验证版本相同。文档更新不改变最终测试源码。

真实新进程恢复：

```powershell
& 'C:\Program Files\Git\bin\bash.exe' scripts/run-reader-process-recovery.sh emulator-5554 20261113T073000Z-process 700916f41a46-7f8f46acba03
python scripts/run-backup-fresh-install.py --serial emulator-5554
```

- 进程两阶段2/2，证据：`app/build/reports/process-recovery/20261113T073000Z-process-700916f41a46-7f8f46acba03/`。
- 全新安装2/2，证据：`build/reports/backup-fresh-install/20261113T073732Z_aa688d78c3/`。真正卸载QA/QA.test并确认不存在，同APK重装，导出归档由宿主持有后回传校验。三本TXT/EPUB/WEB正文、UTF-16进度、书签、设置全部恢复并重新打开，重复恢复0新增，AI未配置。
- fresh此前的三个失败均保留：Windows adb二进制stdin在0x1A截断（改Base64传输且保留往返SHA）；新测试错误默认17sp（原仓库默认18sp）；指定语言后Activity上下文丢失导致文件选择器注册崩溃（真实产品修复）。

## 性能解释

引擎指标只测已准备文本的分页与缓存容器查询；UI指标是操作到renderer frame commit的仪器同步上界。导入测量包含BookRepository.import的指纹、文件和Room提交；50ms采样PSS不是精确分配峰值。所有当前数字为模拟器观察，不是真机产品目标。

旧10样本协议的历史基线位于`build/reports/reader-performance/baseline-700916f-perf-20261113/`。完整QA1引擎4/4通过15%比较，UI排版p95+20.49%失败；QA7同章重复preview修复后仍有跨章/排版/工具p95越线，不能声称已恢复性能。保留全部原始失败。

进一步配对固定为同32章语料、同harness每项30样本、A-B-B-A四轮（A为历史产品、B为当前），全部APK先构建、每轮相同空闲、独占设备、记录环境；保留全部样本及同版本差异，不挑最好轮。门槛仍15%，比较要求至少30样本。

有效ABBA报告：`build/reports/reader-performance/abba-20261113T075248Z-225614a5`。四轮共600动作原始样本全部完整，运行/源码/哈希/p95及文件一致性都通过校验；**两组性能门槛均失败**，重排p95分别+18.47%和+20.11%，缓存/工具也有部分越线，详细全表见[性能说明](../../design/performance-testing.md)。主机空闲后的负载与可用内存仍有变化（CPU16.47–50.39%，可用0.63–9.37GiB），不可冒充稳定真机条件，也不据此删掉失败。旧采集失败run另行完整保留，没有回造数组。

分段诊断已完成：`build/reports/reader-performance/phase-diagnostic-20261113T081008Z-e3d55fa3`，两轮300动作和每版454条同步引擎日志完整保留。四段逐项和与总时延一致；本轮layout p95仍退化28.06%，其他失败也保留。中位数与最终帧等待没有同步退化，尾部横跨action/ready/Compose idle；action不是纯磁盘时间，idle不是纯Compose CPU时间。两版已计时前台分页调用一致，预取没有单独指标，不能证明全部后台计算量相同。没有已证明、可安全删除的新增多余工作；进一步归因需在受控参考设备上追踪线程调度、DataStore、GC/JIT、重组和帧时间线。没有降低门槛或改动正确性规则来使结果通过。

宏基准已实际执行：`build/reports/reader-performance/macro-20261113T075929Z-a5ba6f48`。2/2通过、冷/热各10个样本，20条Perfetto trace；书架Activity TTID中位948.1458/305.0984ms，p951116.9103/390.2930ms。保留默认Partial/profile检查，仅将模拟器分类明确为EMULATOR提示豁免；这不是正文首屏测量。两个独立APK的哈希与公开证书核验、动画恢复均有receipt。

宏基准实际命令（指定`ANDROID_SERIAL=emulator-5554`）：

```powershell
.\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest --console=plain --no-configuration-cache '-Pkotlin.compiler.execution.strategy=in-process' '-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR'
```

最终QA2引擎与历史同环境基线比较4/4通过（`build/reader-final-engine-comparison.log`）：preview p95 108.5599ms（-1.92%）、256KiB分页4301.7408ms（+6.25%）、缓存容器查询0.0029981ms（-0.02%）、双章分页7769.2734ms（+7.71%）。样本数分别10/10/20/10。比较命令为`compare-reader-performance.ps1 -Baseline <历史reader-engine-benchmark.json> -Current <QA2的reader-engine-benchmark.json>`，默认门槛15%。

最终QA2独立UI观察为每项30样本：打开515.3279ms、缓存翻页216.1903ms、未缓存跨章214.9763ms、重排183.3033ms、工具列表318.8087ms（均p95）。主代理核验全部样本、p95及四段和；这不是新的受控配对，**不替代或推翻ABBA两组失败**。

最终完整入库单样本：100MiB 47.878s/峰值PSS213186KiB/410章；约10MiB 2.969s/209847KiB/857章；可复现嵌套EPUB 0.165s/170449KiB/1章。源字节数分别104857600/10483681/1191，50ms PSS采样，包含文件/指纹/Room提交，不能作为p95验收数据。原QA6观察仍保留在检查点。

## 兼容与未执行环境

- Room8→9为非破坏迁移，只新增下载执行代次、owner、等待时间、自动重试计数；真实schema8打开后原ID/进度/书签/任务/路径校验通过。
- DataStore新增自定义快照键，旧CUSTOM沿用其有效值，旧预设保留显示，不虚构被历史版本覆盖的自定义值。
- archiveVersion1为首个定义格式，独立于Room9；拒绝未知版本。正文逐字节保存，恢复重建本机路径，不直接覆盖数据库。设置写入与Room独立，若设置写入失败会明确报告书籍已恢复但设置未恢复。
- 无实体参考设备，未执行真机性能目标、真人TalkBack焦点/语音巡览。语义名称和48dp可点击范围已有仪器验证。
- 未触发远端CI或发布签名步骤；工作流针对同一精确SHA的本次质量检查，不能把历史CI记录当作本轮结果。
